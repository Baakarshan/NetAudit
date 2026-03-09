package com.netaudit.stream

import com.netaudit.event.AuditEventBus
import com.netaudit.model.*
import com.netaudit.parser.ParserRegistry
import com.netaudit.parser.ProtocolParser
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

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

        // 发送 FIN 包（空 payload）
        val finMetadata = dataMetadata.copy(
            tcpFlags = TcpFlags(syn = false, ack = true, fin = true, rst = false, psh = false),
            payload = ByteArray(0)
        )
        tracker.handleTcpPacket(finMetadata)

        assertEquals(0, tracker.activeStreamCount())
    }

    @Test
    fun `test TCP RST cleanup branch`() = runTest {
        val mockParser = mockk<ProtocolParser>(relaxed = true)
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        coEvery { mockParser.parse(any()) } returns null

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)

        val dataMetadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 12345,
            dstPort = 80,
            ipProtocol = TransportProtocol.TCP,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
            seqNumber = 1000L,
            ackNumber = 2000L,
            payload = "data".toByteArray()
        )
        tracker.handleTcpPacket(dataMetadata)
        assertEquals(1, tracker.activeStreamCount())

        val rstMetadata = dataMetadata.copy(
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = true, psh = false),
            payload = ByteArray(0)
        )
        tracker.handleTcpPacket(rstMetadata)

        assertEquals(0, tracker.activeStreamCount())
    }

    @Test
    fun `test TCP FIN with payload triggers parse and emit`() = runTest {
        val mockParser = mockk<ProtocolParser>()
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        val event = AuditEvent.HttpEvent(
            id = "event-1",
            timestamp = Clock.System.now(),
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1111,
            dstPort = 80,
            method = "GET",
            url = "/",
            host = "example.com"
        )
        coEvery { mockParser.parse(any()) } returns event

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1111,
            dstPort = 80,
            ipProtocol = TransportProtocol.TCP,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = true, rst = false, psh = false),
            seqNumber = 1,
            ackNumber = 1,
            payload = "hi".toByteArray()
        )

        tracker.handleTcpPacket(metadata)

        coVerify { mockParser.parse(any()) }
        coVerify { mockEventBus.emitAudit(event) }
    }

    @Test
    fun `test TCP ignores empty payload without flags`() = runTest {
        val mockParser = mockk<ProtocolParser>(relaxed = true)
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        coEvery { mockParser.parse(any()) } returns null

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "192.168.1.10",
            dstIp = "192.168.1.1",
            srcPort = 1111,
            dstPort = 80,
            ipProtocol = TransportProtocol.TCP,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
            seqNumber = 1,
            ackNumber = 1,
            payload = ByteArray(0)
        )

        tracker.handleTcpPacket(metadata)

        coVerify(exactly = 0) { mockParser.parse(any()) }
    }

    @Test
    fun `test TCP parser exception is handled`() = runTest {
        val mockParser = mockk<ProtocolParser>()
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        coEvery { mockParser.parse(any()) } throws IllegalStateException("boom")

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 80,
            ipProtocol = TransportProtocol.TCP,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
            seqNumber = 1000L,
            ackNumber = 2000L,
            payload = "data".toByteArray()
        )

        tracker.handleTcpPacket(metadata)

        coVerify(exactly = 0) { mockEventBus.emitAudit(any()) }
    }

    @Test
    fun `test TCP server to client direction`() = runTest {
        val mockParser = mockk<ProtocolParser>()
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        coEvery { mockParser.parse(any()) } returns null

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "9.9.9.9",
            dstIp = "1.1.1.1",
            srcPort = 80,
            dstPort = 12345,
            ipProtocol = TransportProtocol.TCP,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
            seqNumber = 1,
            ackNumber = 1,
            payload = "data".toByteArray()
        )

        tracker.handleTcpPacket(metadata)

        coVerify(exactly = 1) { mockParser.parse(withArg { context ->
            assertEquals(Direction.SERVER_TO_CLIENT, context.direction)
        }) }
    }

    @Test
    fun `test UDP server to client direction`() = runTest {
        val mockParser = mockk<ProtocolParser>(relaxed = true)
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        every { mockRegistry.findByPort(any()) } returns null
        coEvery { mockParser.parse(any()) } returns null

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)

        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "8.8.8.8",
            dstIp = "192.168.1.100",
            srcPort = 53,
            dstPort = 55555,
            ipProtocol = TransportProtocol.UDP,
            tcpFlags = null,
            seqNumber = null,
            ackNumber = null,
            payload = "dns response".toByteArray()
        )
        tracker.handleUdpPacket(metadata)

        coVerify(exactly = 1) { mockParser.parse(withArg { context ->
            assertEquals(Direction.SERVER_TO_CLIENT, context.direction)
        }) }
    }

    @Test
    fun `test UDP parser exception is handled`() = runTest {
        val mockParser = mockk<ProtocolParser>()
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        every { mockRegistry.findByPort(any()) } returns null
        coEvery { mockParser.parse(any()) } throws IllegalStateException("boom")

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)

        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "8.8.8.8",
            dstIp = "192.168.1.100",
            srcPort = 53,
            dstPort = 55555,
            ipProtocol = TransportProtocol.UDP,
            tcpFlags = null,
            seqNumber = null,
            ackNumber = null,
            payload = "dns response".toByteArray()
        )
        tracker.handleUdpPacket(metadata)

        coVerify(exactly = 0) { mockEventBus.emitAudit(any()) }
    }

    @Test
    fun `test UDP ignored when no parser`() = runTest {
        every { mockRegistry.findByEitherPort(any(), any()) } returns null

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "8.8.8.8",
            dstIp = "192.168.1.100",
            srcPort = 53,
            dstPort = 55555,
            ipProtocol = TransportProtocol.UDP,
            tcpFlags = null,
            seqNumber = null,
            ackNumber = null,
            payload = "dns response".toByteArray()
        )

        tracker.handleUdpPacket(metadata)

        coVerify(exactly = 0) { mockEventBus.emitAudit(any()) }
    }

    @Test
    fun `test UDP emits audit event`() = runTest {
        val mockParser = mockk<ProtocolParser>()
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        every { mockRegistry.findByPort(any()) } returns null

        val event = AuditEvent.DnsEvent(
            id = "dns-1",
            timestamp = Clock.System.now(),
            srcIp = "8.8.8.8",
            dstIp = "192.168.1.100",
            srcPort = 53,
            dstPort = 55555,
            transactionId = 1,
            queryDomain = "example.com",
            queryType = "A",
            isResponse = true,
            resolvedIps = listOf("1.1.1.1")
        )
        coEvery { mockParser.parse(any()) } returns event

        val tracker = TcpStreamTracker(mockRegistry, mockEventBus, testScope)
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "8.8.8.8",
            dstIp = "192.168.1.100",
            srcPort = 53,
            dstPort = 55555,
            ipProtocol = TransportProtocol.UDP,
            tcpFlags = null,
            seqNumber = null,
            ackNumber = null,
            payload = "dns response".toByteArray()
        )

        tracker.handleUdpPacket(metadata)

        coVerify(exactly = 1) { mockEventBus.emitAudit(event) }
    }

    @Test
    fun `cleanup job with no expired streams keeps state`() = runTest {
        val mockParser = mockk<ProtocolParser>(relaxed = true)
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        coEvery { mockParser.parse(any()) } returns null

        val tracker = TcpStreamTracker(
            registry = mockRegistry,
            eventBus = mockEventBus,
            scope = this,
            streamTimeoutSeconds = 60,
            cleanupIntervalMs = 1,
            nowProvider = { Clock.System.now() }
        )

        val cleanupJob = tracker.startCleanupJob()
        advanceTimeBy(2)
        runCurrent()

        assertEquals(0, tracker.activeStreamCount())
        cleanupJob.cancel()
        cleanupJob.join()
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

    @Test
    fun `cleanup job removes expired streams`() = runTest {
        val mockParser = mockk<ProtocolParser>(relaxed = true)
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        coEvery { mockParser.parse(any()) } returns null

        var now = Instant.parse("2024-01-01T00:00:00Z")
        val tracker = TcpStreamTracker(
            registry = mockRegistry,
            eventBus = mockEventBus,
            scope = this,
            streamTimeoutSeconds = 1,
            cleanupIntervalMs = 1,
            nowProvider = { now }
        )

        val metadata = PacketMetadata(
            timestamp = now,
            srcMac = "00:11:22:33:44:55",
            dstMac = "AA:BB:CC:DD:EE:FF",
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 80,
            ipProtocol = TransportProtocol.TCP,
            tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
            seqNumber = 1000L,
            ackNumber = 2000L,
            payload = "data".toByteArray()
        )
        tracker.handleTcpPacket(metadata)
        assertEquals(1, tracker.activeStreamCount())

        val cleanupJob = tracker.startCleanupJob()
        now = now + 2.seconds
        advanceTimeBy(2000)
        runCurrent()

        assertEquals(0, tracker.activeStreamCount())
        cleanupJob.cancel()
        cleanupJob.join()
    }

    @Test
    fun `cleanup job completes after cancel`() = runTest {
        val mockParser = mockk<ProtocolParser>(relaxed = true)
        every { mockRegistry.findByEitherPort(any(), any()) } returns mockParser
        coEvery { mockParser.parse(any()) } returns null

        val tracker = TcpStreamTracker(
            registry = mockRegistry,
            eventBus = mockEventBus,
            scope = this,
            streamTimeoutSeconds = 60,
            cleanupIntervalMs = 1,
            nowProvider = { Clock.System.now() }
        )

        val cleanupJob = tracker.startCleanupJob()
        advanceTimeBy(2)
        runCurrent()

        cleanupJob.cancel()
        advanceUntilIdle()
        cleanupJob.join()

        assertEquals(0, tracker.activeStreamCount())
    }
}
