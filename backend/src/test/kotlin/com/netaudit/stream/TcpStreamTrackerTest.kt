package com.netaudit.stream

import com.netaudit.event.AuditEventBus
import com.netaudit.model.*
import com.netaudit.parser.ParserRegistry
import com.netaudit.parser.ProtocolParser
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class TcpStreamTrackerTest {
    private val mockRegistry = mockk<ParserRegistry>()
    private val mockEventBus = mockk<AuditEventBus>(relaxed = true)
    private val testScope = CoroutineScope(SupervisorJob())

    @Test
    fun `test basic TCP stream reassembly`() = runTest {
        val mockParser = mockk<ProtocolParser>(relaxed = true)
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        coEvery { mockParser.parse(any()) } returns null

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)

        val streamKey = StreamKey("192.168.1.100", 54321, "192.168.1.1", 80)

        // 发送 3 个分段的 TCP 包
        repeat(3) { i ->
            val metadata = PacketMetadata(
                timestamp = Clock.System.now(),
                srcMac = "00:11:22:33:44:55",
                dstMac = "AA:BB:CC:DD:EE:FF",
                srcIp = streamKey.srcIp,
                dstIp = streamKey.dstIp,
                srcPort = streamKey.srcPort,
                dstPort = streamKey.dstPort,
                ipProtocol = TransportProtocol.TCP,
                tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
                seqNumber = 1000L + i,
                ackNumber = 2000L,
                payload = "segment$i".toByteArray()
            )
            tracker.handleTcpPacket(metadata)
        }

        // 验证 parser.parse() 被调用 3 次
        coVerify(exactly = 3) { mockParser.parse(any()) }
    }

    @Test
    fun `test TCP FIN cleanup`() = runTest {
        val mockParser = mockk<ProtocolParser>(relaxed = true)
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        coEvery { mockParser.parse(any()) } returns null

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)

        val streamKey = StreamKey("192.168.1.100", 54321, "192.168.1.1", 80)

        // 发送数据包
        val dataMetadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = streamKey.srcIp,
            dstIp = streamKey.dstIp,
            srcPort = streamKey.srcPort,
            dstPort = streamKey.dstPort,
            ipProtocol = TransportProtocol.TCP,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
            seqNumber = 1000L,
            ackNumber = 2000L,
            payload = "data".toByteArray()
        )
        tracker.handleTcpPacket(dataMetadata)

        assertEquals(1, tracker.activeStreamCount())

        // 发送 FIN 包
        val finMetadata = dataMetadata.copy(
            tcpFlags = TcpFlags(syn = false, ack = true, fin = true, rst = false, psh = false),
            payload = ByteArray(0)
        )
        tracker.handleTcpPacket(finMetadata)

        assertEquals(0, tracker.activeStreamCount())
    }

    @Test
    fun `test UDP direct passthrough`() = runTest {
        val mockParser = mockk<ProtocolParser>(relaxed = true)
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        every { mockRegistry.findByPort(53) } returns mockParser
        coEvery { mockParser.parse(any()) } returns null

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)

        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "192.168.1.100",
            dstIp = "8.8.8.8",
            srcPort = 54321,
            dstPort = 53,
            ipProtocol = TransportProtocol.UDP,
            tcpFlags = null,
            seqNumber = null,
            ackNumber = null,
            payload = "dns query".toByteArray()
        )
        tracker.handleUdpPacket(metadata)

        // 验证 parser.parse() 被调用一次
        coVerify(exactly = 1) { mockParser.parse(any()) }

        // UDP 不应创建流状态
        assertEquals(0, tracker.activeStreamCount())
    }

    @Test
    fun `test unregistered port ignored`() = runTest {
        every { mockRegistry.findByEitherPort(any(), any()) } returns null

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)

        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 9999, // 未注册端口
            ipProtocol = TransportProtocol.TCP,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
            seqNumber = 1000L,
            ackNumber = 2000L,
            payload = "data".toByteArray()
        )
        tracker.handleTcpPacket(metadata)

        // 不应创建流状态
        assertEquals(0, tracker.activeStreamCount())
    }
}
