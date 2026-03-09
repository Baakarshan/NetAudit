package com.netaudit.capture

import com.netaudit.config.CaptureConfig
import io.mockk.mockk
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.pcap4j.core.PcapNativeException
import org.pcap4j.packet.Packet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PacketCaptureEngineTest {
    @Test
    fun `offline stops at end and closes channel`() = runTest {
        val packets = listOf(mockk<Packet>(), mockk<Packet>())
        val source = FakePacketSource(packets)
        val factory = FakeFactory(source)
        val engine = PacketCaptureEngine(testConfig(), this, sourceFactory = factory)

        engine.startOffline("dummy.pcap")

        val received = mutableListOf<Packet>()
        withTimeout(1000) {
            while (true) {
                val result = engine.rawPacketChannel.receiveCatching()
                if (result.isClosed) break
                received.add(result.getOrThrow())
            }
        }

        assertEquals(2, received.size)
        assertEquals(1, factory.offlineCalls)
        assertTrue(source.closed)
    }

    @Test
    fun `live ignores null packets and stops on error`() = runTest {
        val packets = listOf<Packet?>(null, mockk())
        val source = FakePacketSource(packets, throwAfter = true)
        val factory = FakeFactory(source)
        val engine = PacketCaptureEngine(testConfig(), this, sourceFactory = factory)

        engine.startLive()

        val first = withTimeout(1000) { engine.rawPacketChannel.receiveCatching().getOrNull() }
        assertNotNull(first)

        val closed = withTimeout(1000) { engine.rawPacketChannel.receiveCatching() }
        assertTrue(closed.isClosed)
    }

    @Test
    fun `startLive ignored when already running`() = runTest {
        val source = object : PacketSource {
            override fun nextPacket(): Packet? = null
            override fun close() {}
        }
        val factory = FakeFactory(source)
        val engine = PacketCaptureEngine(testConfig(), this, sourceFactory = factory)

        engine.startLive()
        engine.startLive()
        engine.stop()

        assertEquals(1, factory.liveCalls)
    }

    @Test
    fun `startOffline ignored when running`() = runTest {
        val source = object : PacketSource {
            override fun nextPacket(): Packet? = null
            override fun close() {}
        }
        val factory = FakeFactory(source)
        val engine = PacketCaptureEngine(testConfig(), this, sourceFactory = factory)

        engine.startLive()
        engine.startOffline("ignored.pcap")
        engine.stop()

        assertEquals(1, factory.liveCalls)
        assertEquals(0, factory.offlineCalls)
    }

    @Test
    fun `drops packets when channel is full`() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val packets = listOf(mockk<Packet>(), mockk<Packet>())
        val source = FakePacketSource(packets, throwAfter = true)
        val factory = FakeFactory(source)
        val engine = PacketCaptureEngine(
            config = testConfig(channelBufferSize = 1),
            scope = this,
            sourceFactory = factory,
            ioDispatcher = dispatcher
        )

        engine.startLive()
        advanceUntilIdle()

        val droppedField = PacketCaptureEngine::class.java.getDeclaredField("droppedCount")
        droppedField.isAccessible = true
        val dropped = droppedField.getLong(engine)
        assertTrue(dropped >= 1)
    }

    @Test
    fun `startLive propagates pcap exception`() = runTest {
        val factory = object : PacketSourceFactory {
            override fun openLive(config: CaptureConfig): PacketSource {
                throw PcapNativeException("open failed")
            }

            override fun openOffline(pcapFilePath: String): PacketSource {
                throw UnsupportedOperationException("not used")
            }
        }
        val engine = PacketCaptureEngine(testConfig(), this, sourceFactory = factory)

        assertFailsWith<PcapNativeException> {
            engine.startLive()
        }
    }

    @Test
    fun `stop is safe when not running`() = runTest {
        val engine = PacketCaptureEngine(testConfig(), this, sourceFactory = FakeFactory(FakePacketSource(emptyList())))
        engine.stop()
        assertTrue(!engine.rawPacketChannel.isClosedForReceive)
        assertTrue(!engine.rawPacketChannel.isClosedForSend)
    }

    private fun testConfig(channelBufferSize: Int = 4): CaptureConfig = CaptureConfig(
        interfaceName = "eth0",
        promiscuous = true,
        snapshotLength = 65536,
        readTimeoutMs = 100,
        channelBufferSize = channelBufferSize
    )

    private class FakePacketSource(
        private val packets: List<Packet?>,
        private val throwAfter: Boolean = false
    ) : PacketSource {
        private var index = 0
        var closed = false
            private set

        override fun nextPacket(): Packet? {
            if (index < packets.size) {
                return packets[index++]
            }
            if (throwAfter) {
                throw IllegalStateException("boom")
            }
            return null
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeFactory(private val source: PacketSource) : PacketSourceFactory {
        var liveCalls = 0
        var offlineCalls = 0

        override fun openLive(config: CaptureConfig): PacketSource {
            liveCalls += 1
            return source
        }

        override fun openOffline(pcapFilePath: String): PacketSource {
            offlineCalls += 1
            return source
        }
    }
}
