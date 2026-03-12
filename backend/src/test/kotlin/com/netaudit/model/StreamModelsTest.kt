package com.netaudit.model

import kotlin.test.Test
import kotlin.test.assertEquals


class StreamModelsTest {
    @Test
    fun `test StreamKey reverse`() {
        val key = StreamKey(
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 80
        )

        val reversed = key.reverse()

        assertEquals("192.168.1.1", reversed.srcIp)
        assertEquals("192.168.1.100", reversed.dstIp)
        assertEquals(80, reversed.srcPort)
        assertEquals(54321, reversed.dstPort)
    }

    @Test
    fun `test StreamKey canonical`() {
        val key1 = StreamKey(
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 80
        )

        val key2 = key1.reverse()

        // 无论方向，canonical 结果应一致
        assertEquals(key1.canonical(), key2.canonical())
    }

    @Test
    fun `test StreamKey canonical with different IPs`() {
        val key1 = StreamKey(
            srcIp = "10.0.0.1",
            dstIp = "10.0.0.2",
            srcPort = 12345,
            dstPort = 80
        )

        val key2 = StreamKey(
            srcIp = "10.0.0.2",
            dstIp = "10.0.0.1",
            srcPort = 80,
            dstPort = 12345
        )

        // 双向流应该有相同的 canonical key
        assertEquals(key1.canonical(), key2.canonical())
    }

    @Test
    fun `test StreamKey reverse is reversible`() {
        val original = StreamKey(
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 80
        )

        val reversed = original.reverse()
        val doubleReversed = reversed.reverse()

        // 两次反转应该回到原始值
        assertEquals(original, doubleReversed)
    }

    @Test
    fun `test StreamContext accessors and payload text`() {
        val key = StreamKey("1.1.1.1", 1111, "2.2.2.2", 2222)
        val payload = "hello".toByteArray()
        val metadata = PacketMetadata(
            timestamp = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00Z"),
            srcMac = "00:00:00:00:00:01",
            dstMac = "00:00:00:00:00:02",
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            ipProtocol = TransportProtocol.TCP,
            srcPort = 1111,
            dstPort = 2222,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
            seqNumber = 1,
            ackNumber = 1,
            payload = payload
        )

        val context = StreamContext(
            key = key,
            metadata = metadata,
            payload = payload,
            direction = Direction.CLIENT_TO_SERVER
        )

        assertEquals("1.1.1.1", context.srcIp)
        assertEquals("2.2.2.2", context.dstIp)
        assertEquals(1111, context.srcPort)
        assertEquals(2222, context.dstPort)
        assertEquals(metadata.timestamp, context.timestamp)
        assertEquals("hello", context.payloadAsText())
        assertEquals(0, context.sessionState.size)
    }
}
