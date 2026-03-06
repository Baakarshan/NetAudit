package com.netaudit.parser.dns

import com.netaudit.model.PacketMetadata
import com.netaudit.model.StreamContext
import com.netaudit.model.TransportProtocol
import kotlinx.datetime.Clock
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DnsParserTest {
    private val parser = DnsParser()

    @Test
    fun `test A query`() {
        val payload = buildDnsQuery("example.com", 1)
        val context = buildContext(payload, srcIp = "192.168.1.100", dstIp = "8.8.8.8")
        val event = parser.parse(context) as com.netaudit.model.AuditEvent.DnsEvent
        assertEquals("example.com", event.queryDomain)
        assertEquals("A", event.queryType)
        assertEquals(false, event.isResponse)
    }

    @Test
    fun `test A response`() {
        val payload = buildDnsResponse("example.com", listOf("93.184.216.34"))
        val context = buildContext(payload, srcIp = "8.8.8.8", dstIp = "192.168.1.100")
        val event = parser.parse(context) as com.netaudit.model.AuditEvent.DnsEvent
        assertEquals(true, event.isResponse)
        assertEquals(listOf("93.184.216.34"), event.resolvedIps)
    }

    @Test
    fun `test multiple A records`() {
        val payload = buildDnsResponse("example.com", listOf("93.184.216.34", "93.184.216.35"))
        val context = buildContext(payload, srcIp = "8.8.8.8", dstIp = "192.168.1.100")
        val event = parser.parse(context) as com.netaudit.model.AuditEvent.DnsEvent
        assertEquals(2, event.resolvedIps.size)
    }

    @Test
    fun `test AAAA query`() {
        val payload = buildDnsQuery("example.com", 28)
        val context = buildContext(payload, srcIp = "192.168.1.100", dstIp = "8.8.8.8")
        val event = parser.parse(context) as com.netaudit.model.AuditEvent.DnsEvent
        assertEquals("AAAA", event.queryType)
    }

    @Test
    fun `test pointer compression response`() {
        val payload = buildDnsResponse("example.com", listOf("93.184.216.34"))
        val context = buildContext(payload, srcIp = "8.8.8.8", dstIp = "192.168.1.100")
        val event = parser.parse(context)
        assertNotNull(event)
        assertEquals("example.com", event.queryDomain)
    }

    @Test
    fun `test short payload`() {
        val context = buildContext(byteArrayOf(1, 2, 3))
        assertNull(parser.parse(context))
    }

    @Test
    fun `test dns server address mapping`() {
        val queryPayload = buildDnsQuery("example.com", 1)
        val queryContext = buildContext(queryPayload, srcIp = "192.168.1.100", dstIp = "8.8.8.8")
        val queryEvent = parser.parse(queryContext) as com.netaudit.model.AuditEvent.DnsEvent
        assertEquals("192.168.1.100", queryEvent.srcIp)
        assertEquals("8.8.8.8", queryEvent.dstIp)

        val respPayload = buildDnsResponse("example.com", listOf("93.184.216.34"))
        val respContext = buildContext(respPayload, srcIp = "8.8.8.8", dstIp = "192.168.1.100")
        val respEvent = parser.parse(respContext) as com.netaudit.model.AuditEvent.DnsEvent
        assertEquals("192.168.1.100", respEvent.srcIp)
        assertEquals("8.8.8.8", respEvent.dstIp)
    }

    private fun buildContext(
        payload: ByteArray,
        srcIp: String = "192.168.1.100",
        dstIp: String = "8.8.8.8",
        srcPort: Int = 5353,
        dstPort: Int = 53
    ): StreamContext {
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:00:00:00:00:01",
            dstMac = "00:00:00:00:00:02",
            srcIp = srcIp,
            dstIp = dstIp,
            ipProtocol = TransportProtocol.UDP,
            srcPort = srcPort,
            dstPort = dstPort,
            tcpFlags = null,
            seqNumber = null,
            ackNumber = null,
            payload = payload
        )

        return StreamContext(
            key = com.netaudit.model.StreamKey(srcIp, srcPort, dstIp, dstPort),
            metadata = metadata,
            payload = payload,
            direction = com.netaudit.model.Direction.CLIENT_TO_SERVER,
            sessionState = mutableMapOf()
        )
    }

    private fun buildDnsQuery(domain: String, qtype: Int = 1, transactionId: Int = 0x1234): ByteArray {
        val header = ByteBuffer.allocate(12)
            .putShort(transactionId.toShort())
            .putShort(0x0100.toShort())
            .putShort(1)
            .putShort(0)
            .putShort(0)
            .putShort(0)
            .array()

        val qname = encodeDomain(domain)
        val question = ByteBuffer.allocate(4)
            .putShort(qtype.toShort())
            .putShort(1)
            .array()

        return header + qname + question
    }

    private fun buildDnsResponse(
        domain: String,
        ips: List<String>,
        transactionId: Int = 0x1234,
        ttl: Int = 300
    ): ByteArray {
        val header = ByteBuffer.allocate(12)
            .putShort(transactionId.toShort())
            .putShort(0x8180.toShort())
            .putShort(1)
            .putShort(ips.size.toShort())
            .putShort(0)
            .putShort(0)
            .array()

        val qname = encodeDomain(domain)
        val question = ByteBuffer.allocate(4)
            .putShort(1)
            .putShort(1)
            .array()

        val answerBytes = java.io.ByteArrayOutputStream()
        for (ip in ips) {
            val ipBytes = if (ip.contains(":")) {
                java.net.InetAddress.getByName(ip).address
            } else {
                ip.split(".").map { it.toInt().toByte() }.toByteArray()
            }
            val type = if (ipBytes.size == 16) 28 else 1
            val answer = ByteBuffer.allocate(12 + ipBytes.size)
                .putShort(0xC00C.toShort())
                .putShort(type.toShort())
                .putShort(1)
                .putInt(ttl)
                .putShort(ipBytes.size.toShort())
                .put(ipBytes)
                .array()
            answerBytes.write(answer)
        }

        return header + qname + question + answerBytes.toByteArray()
    }

    private fun encodeDomain(domain: String): ByteArray {
        val labels = domain.split(".")
        val bytes = mutableListOf<Byte>()
        for (label in labels) {
            val labelBytes = label.toByteArray(Charsets.US_ASCII)
            bytes.add(labelBytes.size.toByte())
            bytes.addAll(labelBytes.toList())
        }
        bytes.add(0)
        return bytes.toByteArray()
    }
}
