package com.netaudit.parser.dns

import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.parser.ProtocolParser
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.ByteBuffer
import java.util.UUID

private val logger = KotlinLogging.logger {}

class DnsParser : ProtocolParser {
    override val protocolType = ProtocolType.DNS
    override val ports = setOf(53)

    override fun parse(context: StreamContext): AuditEvent? {
        val data = context.payload
        if (data.size < 12) return null

        return try {
            parseDnsPacket(context, data)
        } catch (e: Exception) {
            logger.warn { "DNS parse error: ${e.message}" }
            null
        }
    }

    private fun parseDnsPacket(context: StreamContext, data: ByteArray): AuditEvent.DnsEvent? {
        val buf = ByteBuffer.wrap(data)

        val transactionId = buf.getShort().toInt() and 0xFFFF
        val flags = buf.getShort().toInt() and 0xFFFF
        val qdCount = buf.getShort().toInt() and 0xFFFF
        val anCount = buf.getShort().toInt() and 0xFFFF
        buf.getShort()
        buf.getShort()

        val isResponse = (flags and 0x8000) != 0
        var queryDomain = ""
        val queryType: String
        var ttl: Int? = null
        val resolvedIps = mutableListOf<String>()

        if (qdCount > 0) {
            queryDomain = readDomainName(data, buf.position())
            skipDomainName(buf)
            val qtype = buf.getShort().toInt() and 0xFFFF
            buf.getShort()
            queryType = qtypeToString(qtype)
        } else {
            queryType = "UNKNOWN"
        }

        if (isResponse && anCount > 0) {
            repeat(anCount) {
                if (buf.remaining() < 12) return@repeat

                skipDomainName(buf)
                val type = buf.getShort().toInt() and 0xFFFF
                buf.getShort()
                ttl = buf.getInt()
                val rdLength = buf.getShort().toInt() and 0xFFFF

                if (type == 1 && rdLength == 4 && buf.remaining() >= 4) {
                    val ip = "${buf.get().toInt() and 0xFF}." +
                        "${buf.get().toInt() and 0xFF}." +
                        "${buf.get().toInt() and 0xFF}." +
                        "${buf.get().toInt() and 0xFF}"
                    resolvedIps.add(ip)
                } else if (type == 28 && rdLength == 16 && buf.remaining() >= 16) {
                    val ipv6Bytes = ByteArray(16)
                    buf.get(ipv6Bytes)
                    val ipv6 = java.net.InetAddress.getByAddress(ipv6Bytes).hostAddress
                    resolvedIps.add(ipv6)
                } else {
                    if (buf.remaining() >= rdLength) {
                        buf.position(buf.position() + rdLength)
                    }
                }
            }
        }

        if (queryDomain.isBlank()) return null

        val dnsServerIp = if (isResponse) context.srcIp else context.dstIp
        val clientIp = if (isResponse) context.dstIp else context.srcIp

        logger.debug {
            "DNS ${if (isResponse) "Response" else "Query"}: " +
                "$queryDomain ($queryType) ${if (resolvedIps.isNotEmpty()) "→ $resolvedIps" else ""}"
        }

        return AuditEvent.DnsEvent(
            id = generateId(),
            timestamp = context.timestamp,
            srcIp = clientIp,
            dstIp = dnsServerIp,
            srcPort = if (isResponse) context.dstPort else context.srcPort,
            dstPort = if (isResponse) context.srcPort else context.dstPort,
            transactionId = transactionId,
            queryDomain = queryDomain,
            queryType = queryType,
            isResponse = isResponse,
            resolvedIps = resolvedIps,
            responseTtl = ttl
        )
    }

    private fun readDomainName(data: ByteArray, startPos: Int): String {
        val labels = mutableListOf<String>()
        var pos = startPos
        var maxIterations = 64

        while (pos < data.size && maxIterations-- > 0) {
            val len = data[pos].toInt() and 0xFF

            if (len == 0) break

            if ((len and 0xC0) == 0xC0) {
                if (pos + 1 >= data.size) break
                val offset = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                pos = offset
                continue
            }

            pos++
            if (pos + len > data.size) break
            labels.add(String(data, pos, len, Charsets.US_ASCII))
            pos += len
        }

        return labels.joinToString(".")
    }

    private fun skipDomainName(buf: ByteBuffer) {
        while (buf.hasRemaining()) {
            val len = buf.get().toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) {
                buf.get()
                break
            }
            buf.position(buf.position() + len)
        }
    }

    private fun qtypeToString(qtype: Int): String = when (qtype) {
        1 -> "A"
        2 -> "NS"
        5 -> "CNAME"
        6 -> "SOA"
        12 -> "PTR"
        15 -> "MX"
        16 -> "TXT"
        28 -> "AAAA"
        33 -> "SRV"
        255 -> "ANY"
        else -> "TYPE$qtype"
    }

    private fun generateId(): String = UUID.randomUUID().toString()
}
