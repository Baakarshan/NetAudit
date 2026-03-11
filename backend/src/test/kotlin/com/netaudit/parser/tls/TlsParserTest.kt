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

    private fun createContext(
        payload: ByteArray,
        sessionState: MutableMap<String, Any> = mutableMapOf()
    ): StreamContext {
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:00:00:00:00:00",
            dstMac = "00:00:00:00:00:01",
            srcIp = "192.168.1.10",
            dstIp = "93.184.216.34",
            ipProtocol = TransportProtocol.TCP,
            srcPort = 51514,
            dstPort = 443,
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
        supportedVersions: List<Int>
    ): ByteArray {
        val extensions = ByteArrayBuilder()

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

        val extensionsBytes = extensions.toByteArray()

        val body = ByteArrayBuilder()
        body.writeU16(0x0303)
        body.write(ByteArray(32))
        body.writeU8(0)
        val cipherSuites = byteArrayOf(0x13.toByte(), 0x01.toByte())
        body.writeU16(cipherSuites.size)
        body.write(cipherSuites)
        body.writeU8(1)
        body.writeU8(0)
        body.writeU16(extensionsBytes.size)
        body.write(extensionsBytes)

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
}
