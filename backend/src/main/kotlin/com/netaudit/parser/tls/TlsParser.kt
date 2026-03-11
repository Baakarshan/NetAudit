package com.netaudit.parser.tls

import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.parser.ProtocolParser
import java.util.UUID

/**
 * TLS 解析器。
 *
 * 仅解析 ClientHello，提取 SNI、ALPN 与版本信息。
 */
class TlsParser : ProtocolParser {
    override val protocolType: ProtocolType = ProtocolType.TLS
    override val ports: Set<Int> = setOf(443, 8443, 9443)

    override fun parse(context: StreamContext): AuditEvent? {
        if (context.dstPort !in ports) return null

        val seen = context.sessionState[SESSION_KEY_SEEN] as? Boolean ?: false
        if (seen) return null

        if (context.payload.isEmpty()) return null

        val combined = mergeBuffer(context, context.payload)
        if (combined.isEmpty()) return null

        val result = parseClientHello(combined)
        return when (result.status) {
            ParseStatus.NEED_MORE -> {
                context.sessionState[SESSION_KEY_BUFFER] = combined
                null
            }
            ParseStatus.NOT_TLS -> {
                context.sessionState.remove(SESSION_KEY_BUFFER)
                null
            }
            ParseStatus.FOUND -> {
                context.sessionState.remove(SESSION_KEY_BUFFER)
                context.sessionState[SESSION_KEY_SEEN] = true

                val info = result.info ?: return null
                AuditEvent.TlsEvent(
                    id = generateId(),
                    timestamp = context.timestamp,
                    srcIp = context.srcIp,
                    dstIp = context.dstIp,
                    srcPort = context.srcPort,
                    dstPort = context.dstPort,
                    serverName = info.serverName,
                    alpn = info.alpn,
                    clientVersion = formatTlsVersion(info.clientVersion),
                    supportedVersions = info.supportedVersions.map(::formatTlsVersion)
                )
            }
        }
    }

    /**
     * 合并跨包缓存，限制最大缓冲大小。
     */
    private fun mergeBuffer(context: StreamContext, payload: ByteArray): ByteArray {
        val existing = context.sessionState[SESSION_KEY_BUFFER] as? ByteArray
        val combined = if (existing != null && existing.isNotEmpty()) {
            existing + payload
        } else {
            payload
        }

        return if (combined.size > MAX_BUFFER_BYTES) {
            context.sessionState.remove(SESSION_KEY_BUFFER)
            ByteArray(0)
        } else {
            combined
        }
    }

    /**
     * 尝试解析 ClientHello。
     *
     * 解析失败或数据不足会返回不同状态，以便上层决定是否继续缓存。
     */
    private fun parseClientHello(data: ByteArray): ParseResult {
        if (data.size < 6) return ParseResult(ParseStatus.NEED_MORE)

        val contentType = readU8(data, 0)
        if (contentType != TLS_HANDSHAKE) return ParseResult(ParseStatus.NOT_TLS)

        val recordVersion = readU16(data, 1)
        if (!isTlsRecordVersion(recordVersion)) return ParseResult(ParseStatus.NOT_TLS)

        val recordLength = readU16(data, 3)
        val recordEnd = 5 + recordLength
        if (data.size < recordEnd) return ParseResult(ParseStatus.NEED_MORE)

        if (data.size < 9) return ParseResult(ParseStatus.NEED_MORE)
        val handshakeType = readU8(data, 5)
        if (handshakeType != HANDSHAKE_CLIENT_HELLO) return ParseResult(ParseStatus.NOT_TLS)

        val handshakeLength = readU24(data, 6)
        val handshakeStart = 9
        val handshakeEnd = handshakeStart + handshakeLength
        if (data.size < handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)

        var index = handshakeStart

        if (index + 2 > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)
        val clientVersion = readU16(data, index)
        index += 2

        if (index + RANDOM_LEN > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)
        index += RANDOM_LEN

        if (index + 1 > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)
        val sessionIdLength = readU8(data, index)
        index += 1
        if (index + sessionIdLength > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)
        index += sessionIdLength

        if (index + 2 > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)
        val cipherSuitesLength = readU16(data, index)
        index += 2
        if (index + cipherSuitesLength > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)
        index += cipherSuitesLength

        if (index + 1 > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)
        val compressionMethodsLength = readU8(data, index)
        index += 1
        if (index + compressionMethodsLength > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)
        index += compressionMethodsLength

        val alpn = mutableListOf<String>()
        val supportedVersions = mutableListOf<Int>()
        var serverName: String? = null

        if (index == handshakeEnd) {
            return ParseResult(
                ParseStatus.FOUND,
                ClientHelloInfo(clientVersion, serverName, alpn, supportedVersions)
            )
        }

        if (index + 2 > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)
        val extensionsLength = readU16(data, index)
        index += 2
        val extensionsEnd = index + extensionsLength
        if (extensionsEnd > handshakeEnd) return ParseResult(ParseStatus.NEED_MORE)

        while (index + 4 <= extensionsEnd) {
            val extType = readU16(data, index)
            index += 2
            val extSize = readU16(data, index)
            index += 2

            val extStart = index
            val extEnd = extStart + extSize
            if (extEnd > extensionsEnd) break

            when (extType) {
                EXT_SNI -> serverName = parseSni(data, extStart, extEnd) ?: serverName
                EXT_ALPN -> alpn.addAll(parseAlpn(data, extStart, extEnd))
                EXT_SUPPORTED_VERSIONS -> supportedVersions.addAll(parseSupportedVersions(data, extStart, extEnd))
            }

            index = extEnd
        }

        return ParseResult(
            ParseStatus.FOUND,
            ClientHelloInfo(clientVersion, serverName, alpn, supportedVersions)
        )
    }

    /**
     * 解析 SNI 扩展中的服务器名称。
     */
    private fun parseSni(data: ByteArray, start: Int, end: Int): String? {
        if (start + 2 > end) return null
        var index = start
        val listLen = readU16(data, index)
        index += 2
        val listEnd = (index + listLen).coerceAtMost(end)

        while (index + 3 <= listEnd) {
            val nameType = readU8(data, index)
            index += 1
            val nameLen = readU16(data, index)
            index += 2
            if (index + nameLen > listEnd) break
            val name = data.copyOfRange(index, index + nameLen).toString(Charsets.US_ASCII)
            index += nameLen

            if (nameType == 0) return name
        }
        return null
    }

    /**
     * 解析 ALPN 扩展中的协议列表。
     */
    private fun parseAlpn(data: ByteArray, start: Int, end: Int): List<String> {
        if (start + 2 > end) return emptyList()
        var index = start
        val listLen = readU16(data, index)
        index += 2
        val listEnd = (index + listLen).coerceAtMost(end)

        val protocols = mutableListOf<String>()
        while (index < listEnd) {
            val nameLen = readU8(data, index)
            index += 1
            if (index + nameLen > listEnd) break
            val name = data.copyOfRange(index, index + nameLen).toString(Charsets.US_ASCII)
            index += nameLen
            protocols.add(name)
        }

        return protocols
    }

    /**
     * 解析 Supported Versions 扩展中的版本列表。
     */
    private fun parseSupportedVersions(data: ByteArray, start: Int, end: Int): List<Int> {
        if (start + 1 > end) return emptyList()
        var index = start
        val listLen = readU8(data, index)
        index += 1
        val listEnd = (index + listLen).coerceAtMost(end)

        val versions = mutableListOf<Int>()
        while (index + 1 < listEnd) {
            val version = readU16(data, index)
            index += 2
            versions.add(version)
        }

        return versions
    }

    private fun isTlsRecordVersion(value: Int): Boolean = value in TLS_VERSION_MIN..TLS_VERSION_MAX

    private fun readU8(data: ByteArray, offset: Int): Int = data[offset].toInt() and 0xFF

    private fun readU16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun readU24(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() and 0xFF)
    }

    private fun formatTlsVersion(value: Int): String = when (value) {
        0x0300 -> "SSL 3.0"
        0x0301 -> "TLS 1.0"
        0x0302 -> "TLS 1.1"
        0x0303 -> "TLS 1.2"
        0x0304 -> "TLS 1.3"
        else -> "0x" + value.toString(16).padStart(4, '0')
    }

    private fun generateId(): String = UUID.randomUUID().toString()

    private data class ClientHelloInfo(
        val clientVersion: Int,
        val serverName: String?,
        val alpn: List<String>,
        val supportedVersions: List<Int>
    )

    private data class ParseResult(
        val status: ParseStatus,
        val info: ClientHelloInfo? = null
    )

    private enum class ParseStatus {
        FOUND,
        NEED_MORE,
        NOT_TLS
    }

    private companion object {
        private const val TLS_HANDSHAKE = 0x16
        private const val HANDSHAKE_CLIENT_HELLO = 0x01
        private const val EXT_SNI = 0x0000
        private const val EXT_ALPN = 0x0010
        private const val EXT_SUPPORTED_VERSIONS = 0x002b
        private const val RANDOM_LEN = 32
        private const val MAX_BUFFER_BYTES = 16 * 1024
        private const val SESSION_KEY_SEEN = "tls.clienthello.seen"
        private const val SESSION_KEY_BUFFER = "tls.clienthello.buffer"
        private const val TLS_VERSION_MIN = 0x0300
        private const val TLS_VERSION_MAX = 0x0304
    }
}
