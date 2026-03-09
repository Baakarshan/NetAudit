package com.netaudit.pipeline

import com.netaudit.capture.CaptureEngine
import com.netaudit.config.CaptureConfig
import com.netaudit.decode.PacketDecoderLike
import com.netaudit.event.AuditEventBus
import com.netaudit.model.PacketMetadata
import com.netaudit.model.TcpFlags
import com.netaudit.model.TransportProtocol
import com.netaudit.parser.ParserRegistry
import com.netaudit.stream.StreamTracker
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.pcap4j.packet.Packet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditPipelineTest {
    @Test
    fun `test active stream count tracking`() = runTest {
        val config = testConfig()
        val eventBus = AuditEventBus()
        val registry = ParserRegistry()
        val scope = CoroutineScope(SupervisorJob())
        val pipeline = AuditPipeline(config, registry, eventBus, scope)

        assertTrue(pipeline.activeStreams() == 0)

        scope.cancel()
    }

    @Test
    fun `start dispatches decoded packets`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val capture = FakeCaptureEngine()
        val decoder = FakeDecoder(
            listOf(tcpMetadata(), udpMetadata(), null)
        )
        val tracker = FakeStreamTracker()
        val pipeline = AuditPipeline(
            config = testConfig(),
            registry = ParserRegistry(),
            eventBus = AuditEventBus(),
            scope = this,
            captureEngine = capture,
            decoder = decoder,
            streamTracker = tracker,
            dispatcher = dispatcher
        )

        pipeline.start()
        capture.rawPacketChannel.trySend(mockk())
        capture.rawPacketChannel.trySend(mockk())
        capture.rawPacketChannel.trySend(mockk())
        capture.rawPacketChannel.close()

        advanceUntilIdle()

        assertTrue(capture.liveStarted)
        assertTrue(tracker.cleanupStarted)
        assertEquals(1, tracker.tcpCalls)
        assertEquals(1, tracker.udpCalls)
    }

    @Test
    fun `decoder exception does not stop pipeline`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val capture = FakeCaptureEngine()
        val decoder = FakeDecoder(
            listOf(tcpMetadata(), udpMetadata()),
            throwOnIndex = 0
        )
        val tracker = FakeStreamTracker()
        val pipeline = AuditPipeline(
            config = testConfig(),
            registry = ParserRegistry(),
            eventBus = AuditEventBus(),
            scope = this,
            captureEngine = capture,
            decoder = decoder,
            streamTracker = tracker,
            dispatcher = dispatcher
        )

        pipeline.start()
        capture.rawPacketChannel.trySend(mockk())
        capture.rawPacketChannel.trySend(mockk())
        capture.rawPacketChannel.close()

        advanceUntilIdle()

        assertEquals(0, tracker.tcpCalls)
        assertEquals(1, tracker.udpCalls)
    }

    @Test
    fun `startOffline triggers offline capture`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val capture = FakeCaptureEngine()
        val decoder = FakeDecoder(emptyList())
        val tracker = FakeStreamTracker()
        val pipeline = AuditPipeline(
            config = testConfig(),
            registry = ParserRegistry(),
            eventBus = AuditEventBus(),
            scope = this,
            captureEngine = capture,
            decoder = decoder,
            streamTracker = tracker,
            dispatcher = dispatcher
        )

        pipeline.startOffline("sample.pcap")
        capture.rawPacketChannel.close()

        advanceUntilIdle()

        assertTrue(capture.offlineStarted)
        assertTrue(tracker.cleanupStarted)
    }

    @Test
    fun `stop delegates to capture engine`() = runTest {
        val capture = FakeCaptureEngine()
        val pipeline = AuditPipeline(
            config = testConfig(),
            registry = ParserRegistry(),
            eventBus = AuditEventBus(),
            scope = this,
            captureEngine = capture,
            decoder = FakeDecoder(emptyList()),
            streamTracker = FakeStreamTracker(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        pipeline.stop()
        assertTrue(capture.stopped)
    }

    private fun testConfig(): CaptureConfig = CaptureConfig(
        interfaceName = "eth0",
        snapshotLength = 65536,
        promiscuous = true,
        readTimeoutMs = 100,
        channelBufferSize = 1024
    )

    private fun tcpMetadata(): PacketMetadata = PacketMetadata(
        timestamp = kotlinx.datetime.Clock.System.now(),
        srcMac = "00:11:22:33:44:55",
        dstMac = "AA:BB:CC:DD:EE:FF",
        srcIp = "192.168.1.100",
        dstIp = "192.168.1.1",
        ipProtocol = TransportProtocol.TCP,
        srcPort = 54321,
        dstPort = 80,
        tcpFlags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false),
        seqNumber = 1L,
        ackNumber = 2L,
        payload = "data".toByteArray()
    )

    private fun udpMetadata(): PacketMetadata = PacketMetadata(
        timestamp = kotlinx.datetime.Clock.System.now(),
        srcMac = "00:11:22:33:44:55",
        dstMac = "AA:BB:CC:DD:EE:FF",
        srcIp = "192.168.1.100",
        dstIp = "8.8.8.8",
        ipProtocol = TransportProtocol.UDP,
        srcPort = 54321,
        dstPort = 53,
        tcpFlags = null,
        seqNumber = null,
        ackNumber = null,
        payload = "dns".toByteArray()
    )

    private class FakeCaptureEngine : CaptureEngine {
        override val rawPacketChannel = Channel<Packet>(Channel.UNLIMITED)
        var liveStarted = false
            private set
        var offlineStarted = false
            private set
        var stopped = false
            private set

        override fun startLive() {
            liveStarted = true
        }

        override fun startOffline(pcapFilePath: String) {
            offlineStarted = true
        }

        override fun stop() {
            stopped = true
            rawPacketChannel.close()
        }
    }

    private class FakeDecoder(
        private val results: List<PacketMetadata?>,
        private val throwOnIndex: Int? = null
    ) : PacketDecoderLike {
        private var index = 0

        override fun decode(packet: Packet): PacketMetadata? {
            if (throwOnIndex != null && index == throwOnIndex) {
                index += 1
                throw IllegalStateException("boom")
            }
            val result = results.getOrNull(index)
            index += 1
            return result
        }
    }

    private class FakeStreamTracker : StreamTracker {
        var tcpCalls = 0
            private set
        var udpCalls = 0
            private set
        var cleanupStarted = false
            private set

        override fun startCleanupJob(): Job {
            cleanupStarted = true
            return Job()
        }

        override suspend fun handleTcpPacket(metadata: PacketMetadata) {
            tcpCalls += 1
        }

        override suspend fun handleUdpPacket(metadata: PacketMetadata) {
            udpCalls += 1
        }

        override fun activeStreamCount(): Int = 0
    }
}
