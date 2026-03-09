package com.netaudit.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PacketMetadataTest {
    @Test
    fun `packet metadata equality uses key fields`() {
        val ts = Instant.parse("2024-01-01T00:00:00Z")
        val base = PacketMetadata(
            timestamp = ts,
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "192.168.1.1",
            dstIp = "192.168.1.2",
            ipProtocol = TransportProtocol.TCP,
            srcPort = 1234,
            dstPort = 80,
            tcpFlags = TcpFlags(syn = true, ack = false, fin = false, rst = false, psh = false),
            seqNumber = 10L,
            ackNumber = 0L,
            payload = byteArrayOf(1, 2, 3)
        )

        assertEquals(base, base)

        val sameKeyDifferentPayload = base.copy(payload = byteArrayOf(9, 9, 9))
        assertEquals(base, sameKeyDifferentPayload)
        assertEquals(base.hashCode(), sameKeyDifferentPayload.hashCode())

        val differentSeq = base.copy(seqNumber = 11L)
        assertNotEquals(base, differentSeq)

        val differentPort = base.copy(srcPort = 1235)
        assertNotEquals(base, differentPort)

        val differentTimestamp = base.copy(timestamp = Instant.parse("2024-01-01T00:00:01Z"))
        assertNotEquals(base, differentTimestamp)

        val differentSrcIp = base.copy(srcIp = "192.168.1.99")
        assertNotEquals(base, differentSrcIp)

        assertTrue(!base.equals("not-metadata"))
    }

    @Test
    fun `packet metadata equality handles null seq number`() {
        val ts = Instant.parse("2024-01-01T00:00:00Z")
        val udpPacket = PacketMetadata(
            timestamp = ts,
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "10.0.0.1",
            dstIp = "10.0.0.2",
            ipProtocol = TransportProtocol.UDP,
            srcPort = 1234,
            dstPort = 53,
            tcpFlags = null,
            seqNumber = null,
            ackNumber = null,
            payload = byteArrayOf(7, 8)
        )
        val udpPacket2 = udpPacket.copy(payload = byteArrayOf(9))
        assertEquals(udpPacket, udpPacket2)
    }

    @Test
    fun `transport protocol values accessible`() {
        assertTrue(TransportProtocol.entries.contains(TransportProtocol.TCP))
        assertEquals(TransportProtocol.UDP, TransportProtocol.valueOf("UDP"))
    }

    @Test
    fun `tcp flags data class behaves`() {
        val flags = TcpFlags(syn = true, ack = false, fin = false, rst = false, psh = false)
        val same = flags.copy()
        val different = flags.copy(psh = true)
        assertEquals(flags, same)
        assertNotEquals(flags, different)
        assertTrue(flags.toString().contains("syn"))
    }
}
