package com.netaudit.parser.tls

import com.netaudit.model.AuditEvent
import com.netaudit.model.Direction
import com.netaudit.model.PacketMetadata
import com.netaudit.model.StreamContext
import com.netaudit.model.StreamKey
import com.netaudit.model.TcpFlags
import com.netaudit.model.TransportProtocol
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TlsParserTest {
    private val parser = TlsParser()

    @Test
    fun `parse client hello with sni and alpn`() {
        val payload = buildClientHello(
            serverName = "leetcode.cn",
            alpn = listOf("h2", "http/1.1"),
            supportedVersions = listOf(0x0304, 0x0303)
        )
        val context = createContext(payload)

        val event = parser.parse(context) as? AuditEvent.TlsEvent

        assertNotNull(event)
        assertEquals("leetcode.cn", event.serverName)
        assertEquals(listOf("h2", "http/1.1"), event.alpn)
        assertEquals("TLS 1.2", event.clientVersion)
        assertEquals(listOf("TLS 1.3", "TLS 1.2"), event.supportedVersions)
    }

    @Test
    fun `buffered payload completes on second chunk`() {
        val payload = buildClientHello(
            serverName = "example.com",
            alpn = listOf("http/1.1"),
            supportedVersions = listOf(0x0304)
        )
        val sessionState = mutableMapOf<String, Any>()

        val first = payload.copyOfRange(0, payload.size / 2)
        val second = payload.copyOfRange(payload.size / 2, payload.size)

        val firstContext = createContext(first, sessionState)
        val secondContext = createContext(second, sessionState)

        val firstResult = parser.parse(firstContext)
        val secondResult = parser.parse(secondContext) as? AuditEvent.TlsEvent

        assertNull(firstResult)
        assertNotNull(secondResult)
        assertEquals("example.com", secondResult.serverName)
    }

    @Test
    fun `non tls payload ignored`() {
        val payload = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray()
        val context = createContext(payload)

        val result = parser.parse(context)

        assertNull(result)
    }

    @Test
    fun `parse ignores non tls port, seen session and empty payload`() {
        val payload = buildClientHello(
            serverName = "example.com",
            alpn = listOf("h2"),
            supportedVersions = listOf(0x0304)
        )
        val notTlsPort = createContext(payload, dstPort = 80)
        assertNull(parser.parse(notTlsPort))

        val seenState = mutableMapOf<String, Any>("tls.clienthello.seen" to true)
        val seenContext = createContext(payload, sessionState = seenState)
        assertNull(parser.parse(seenContext))

        val emptyPayload = createContext(byteArrayOf())
        assertNull(parser.parse(emptyPayload))
    }

    @Test
    fun `mergeBuffer clears when exceeding limit`() {
        val overflow = ByteArray(16 * 1024)
        val sessionState = mutableMapOf<String, Any>("tls.clienthello.buffer" to overflow)
        val context = createContext(byteArrayOf(0x16), sessionState = sessionState)

        assertNull(parser.parse(context))
        assertNull(sessionState["tls.clienthello.buffer"])
    }

    @Test
    fun `parseClientHello handles malformed lengths`() {
        val base = buildClientHello(
            serverName = "example.com",
            alpn = listOf("h2"),
            supportedVersions = listOf(0x0304)
        )

        assertEquals("NeedMore", parseStatus(ByteArray(5)))

        val shortRecord = base.copyOf(6)
        setU16(shortRecord, 3, 0)
        assertEquals("NeedMore", parseStatus(shortRecord))

        val badVersion = base.copyOf()
        setU16(badVersion, 1, 0x0100)
        assertEquals("NotTls", parseStatus(badVersion))

        val badHandshakeType = base.copyOf()
        badHandshakeType[5] = 0x02
        assertEquals("NotTls", parseStatus(badHandshakeType))

        val tooLongHandshake = base.copyOf()
        setU24(tooLongHandshake, 6, 0x2000)
        assertEquals("NeedMore", parseStatus(tooLongHandshake))

        val noClientVersion = base.copyOf()
        setU24(noClientVersion, 6, 1)
        assertEquals("NeedMore", parseStatus(noClientVersion))

        val noRandom = base.copyOf()
        setU24(noRandom, 6, 2)
        assertEquals("NeedMore", parseStatus(noRandom))

        val noSessionLen = base.copyOf()
        setU24(noSessionLen, 6, 34)
        assertEquals("NeedMore", parseStatus(noSessionLen))

        val sessionTooLong = base.copyOf()
        setU24(sessionTooLong, 6, 35)
        sessionTooLong[SESSION_ID_LEN_OFFSET] = 5
        assertEquals("NeedMore", parseStatus(sessionTooLong))

        val noCipherLen = base.copyOf()
        setU24(noCipherLen, 6, 36)
        assertEquals("NeedMore", parseStatus(noCipherLen))

        val cipherTooLong = base.copyOf()
        setU24(cipherTooLong, 6, 38)
        setU16(cipherTooLong, CIPHER_SUITES_LEN_OFFSET, 4)
        assertEquals("NeedMore", parseStatus(cipherTooLong))

        val noCompressionLen = base.copyOf()
        setU24(noCompressionLen, 6, 39)
        assertEquals("NeedMore", parseStatus(noCompressionLen))

        val compressionTooLong = base.copyOf()
        setU24(compressionTooLong, 6, 40)
        compressionTooLong[COMPRESSION_LEN_OFFSET] = 2
        assertEquals("NeedMore", parseStatus(compressionTooLong))

        val noExtensions = base.copyOf()
        setU24(noExtensions, 6, 41)
        assertEquals("Found", parseStatus(noExtensions))

        val shortExtensionsLen = base.copyOf()
        setU24(shortExtensionsLen, 6, 42)
        assertEquals("NeedMore", parseStatus(shortExtensionsLen))

        val extensionsTooLong = base.copyOf()
        setU24(extensionsTooLong, 6, 43)
        setU16(extensionsTooLong, EXT_LEN_OFFSET, 10)
        assertEquals("NeedMore", parseStatus(extensionsTooLong))

        val extSizeBeyond = base.copyOf()
        setU16(extSizeBeyond, EXT_LEN_OFFSET, 4)
        setU16(extSizeBeyond, EXT_LEN_OFFSET + 2, 0x0000)
        setU16(extSizeBeyond, EXT_LEN_OFFSET + 4, 10)
        assertEquals("Found", parseStatus(extSizeBeyond))
    }

    @Test
    fun `parse client hello with malformed extensions`() {
        val sniBroken = buildExtension(0x0000, byteArrayOf(0x01))
        val alpnBroken = buildExtension(0x0010, byteArrayOf(0x00, 0x01, 0x05))
        val versionsEmpty = buildExtension(0x002b, byteArrayOf())

        val payload = buildClientHello(
            serverName = "",
            alpn = emptyList(),
            supportedVersions = emptyList(),
            extensionsOverride = sniBroken + alpnBroken + versionsEmpty
        )
        val event = parser.parse(createContext(payload)) as? AuditEvent.TlsEvent
        assertNotNull(event)
        assertNull(event.serverName)
        assertTrue(event.alpn.isEmpty())
        assertTrue(event.supportedVersions.isEmpty())
    }

    @Test
    fun `parse client hello handles sni list mismatch`() {
        val sniList = ByteArrayBuilder().apply {
            writeU16(3)
            writeU8(1)
            writeU16(10)
        }.toByteArray()
        val sniData = ByteArrayBuilder().apply {
            writeU16(sniList.size)
            write(sniList)
        }.toByteArray()
        val payload = buildClientHello(
            serverName = "",
            alpn = emptyList(),
            supportedVersions = emptyList(),
            extensionsOverride = buildExtension(0x0000, sniData)
        )

        val event = parser.parse(createContext(payload)) as? AuditEvent.TlsEvent
        assertNotNull(event)
        assertNull(event.serverName)
    }

    @Test
    fun `formatTlsVersion covers legacy and unknown`() {
        val payload = buildClientHello(
            serverName = "example.com",
            alpn = emptyList(),
            supportedVersions = listOf(0x0301, 0x0302, 0x1234),
            clientVersionOverride = 0x0300
        )
        val event = parser.parse(createContext(payload)) as? AuditEvent.TlsEvent

        assertNotNull(event)
        assertEquals("SSL 3.0", event.clientVersion)
        assertEquals(listOf("TLS 1.0", "TLS 1.1", "0x1234"), event.supportedVersions)
    }

    private fun createContext(
        payload: ByteArray,
        sessionState: MutableMap<String, Any> = mutableMapOf(),
        dstPort: Int = 443,
        srcPort: Int = 51514
    ): StreamContext {
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:00:00:00:00:00",
            dstMac = "00:00:00:00:00:01",
            srcIp = "192.168.1.10",
            dstIp = "93.184.216.34",
            ipProtocol = TransportProtocol.TCP,
            srcPort = srcPort,
            dstPort = dstPort,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = true),
            seqNumber = 1,
            ackNumber = 1,
            payload = payload
        )
        return StreamContext(
            key = StreamKey(metadata.srcIp, metadata.srcPort, metadata.dstIp, metadata.dstPort),
            metadata = metadata,
            payload = payload,
            direction = Direction.CLIENT_TO_SERVER,
            sessionState = sessionState
        )
    }

    private fun buildClientHello(
        serverName: String,
        alpn: List<String>,
        supportedVersions: List<Int>,
        clientVersionOverride: Int? = null,
        extensionsOverride: ByteArray? = null
    ): ByteArray {
        val extensions = ByteArrayBuilder()

        if (extensionsOverride == null) {
            val sniHost = serverName.toByteArray(Charsets.US_ASCII)
            val sniList = ByteArrayBuilder()
            sniList.writeU8(0)
            sniList.writeU16(sniHost.size)
            sniList.write(sniHost)
            val sniListBytes = sniList.toByteArray()
            val sniData = ByteArrayBuilder()
            sniData.writeU16(sniListBytes.size)
            sniData.write(sniListBytes)
            val sniBytes = sniData.toByteArray()
            extensions.writeU16(0x0000)
            extensions.writeU16(sniBytes.size)
            extensions.write(sniBytes)

            val alpnList = ByteArrayBuilder()
            for (proto in alpn) {
                val bytes = proto.toByteArray(Charsets.US_ASCII)
                alpnList.writeU8(bytes.size)
                alpnList.write(bytes)
            }
            val alpnListBytes = alpnList.toByteArray()
            val alpnData = ByteArrayBuilder()
            alpnData.writeU16(alpnListBytes.size)
            alpnData.write(alpnListBytes)
            val alpnBytes = alpnData.toByteArray()
            extensions.writeU16(0x0010)
            extensions.writeU16(alpnBytes.size)
            extensions.write(alpnBytes)

            val supported = ByteArrayBuilder()
            for (version in supportedVersions) {
                supported.writeU16(version)
            }
            val supportedList = supported.toByteArray()
            val supportedData = ByteArrayBuilder()
            supportedData.writeU8(supportedList.size)
            supportedData.write(supportedList)
            val supportedBytes = supportedData.toByteArray()
            extensions.writeU16(0x002b)
            extensions.writeU16(supportedBytes.size)
            extensions.write(supportedBytes)
        }

        val extensionsBytes = extensionsOverride ?: extensions.toByteArray()

        val body = ByteArrayBuilder()
        body.writeU16(clientVersionOverride ?: 0x0303)
        body.write(ByteArray(32))
        body.writeU8(0)
        val cipherSuites = byteArrayOf(0x13.toByte(), 0x01.toByte())
        body.writeU16(cipherSuites.size)
        body.write(cipherSuites)
        body.writeU8(1)
        body.writeU8(0)
        if (extensionsOverride != null || extensionsBytes.isNotEmpty()) {
            body.writeU16(extensionsBytes.size)
            body.write(extensionsBytes)
        }

        val bodyBytes = body.toByteArray()
        val handshake = ByteArrayBuilder()
        handshake.writeU8(0x01)
        handshake.writeU24(bodyBytes.size)
        handshake.write(bodyBytes)
        val handshakeBytes = handshake.toByteArray()

        val record = ByteArrayBuilder()
        record.writeU8(0x16)
        record.writeU16(0x0301)
        record.writeU16(handshakeBytes.size)
        record.write(handshakeBytes)
        return record.toByteArray()
    }

    private class ByteArrayBuilder {
        private val buffer = ArrayList<Byte>()

        fun write(bytes: ByteArray) {
            for (b in bytes) buffer.add(b)
        }

        fun writeU8(value: Int) {
            buffer.add((value and 0xFF).toByte())
        }

        fun writeU16(value: Int) {
            buffer.add(((value shr 8) and 0xFF).toByte())
            buffer.add((value and 0xFF).toByte())
        }

        fun writeU24(value: Int) {
            buffer.add(((value shr 16) and 0xFF).toByte())
            buffer.add(((value shr 8) and 0xFF).toByte())
            buffer.add((value and 0xFF).toByte())
        }

        fun toByteArray(): ByteArray {
            val result = ByteArray(buffer.size)
            for (i in buffer.indices) {
                result[i] = buffer[i]
            }
            return result
        }
    }

    private fun parseStatus(data: ByteArray): String {
        val method = TlsParser::class.java.getDeclaredMethod("parseClientHello", ByteArray::class.java)
        method.isAccessible = true
        val result = method.invoke(parser, data) ?: return "null"
        return result.javaClass.simpleName
    }

    private fun buildExtension(type: Int, data: ByteArray): ByteArray {
        val builder = ByteArrayBuilder()
        builder.writeU16(type)
        builder.writeU16(data.size)
        builder.write(data)
        return builder.toByteArray()
    }

    private fun setU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }

    private fun setU24(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 16) and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = (value and 0xFF).toByte()
    }

    private companion object {
        private const val SESSION_ID_LEN_OFFSET = 43
        private const val CIPHER_SUITES_LEN_OFFSET = 44
        private const val COMPRESSION_LEN_OFFSET = 48
        private const val EXT_LEN_OFFSET = 50
    }
}
