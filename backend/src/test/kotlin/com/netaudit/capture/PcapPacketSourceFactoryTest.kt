package com.netaudit.capture

import com.netaudit.config.CaptureConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.pcap4j.core.PcapHandle
import org.pcap4j.core.PcapNativeException
import org.pcap4j.core.PcapNetworkInterface
import org.pcap4j.core.Pcaps
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PcapPacketSourceFactoryTest {
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `openLive returns packet source and delegates to handle`() {
        mockkStatic(Pcaps::class)
        val handle = mockk<PcapHandle>(relaxed = true)
        val nif = mockk<PcapNetworkInterface>()
        every { Pcaps.getDevByName("eth0") } returns nif
        every { nif.openLive(64, PromiscuousMode.PROMISCUOUS, 10) } returns handle

        val config = CaptureConfig(
            interfaceName = "eth0",
            promiscuous = true,
            snapshotLength = 64,
            readTimeoutMs = 10,
            channelBufferSize = 1
        )
        val source = PcapPacketSourceFactory.openLive(config)
        source.nextPacket()
        source.close()
        verify { handle.close() }
    }

    @Test
    fun `openLive uses non-promiscuous mode when disabled`() {
        mockkStatic(Pcaps::class)
        val handle = mockk<PcapHandle>(relaxed = true)
        val nif = mockk<PcapNetworkInterface>()
        every { Pcaps.getDevByName("eth0") } returns nif
        every { nif.openLive(64, PromiscuousMode.NONPROMISCUOUS, 10) } returns handle

        val config = CaptureConfig(
            interfaceName = "eth0",
            promiscuous = false,
            snapshotLength = 64,
            readTimeoutMs = 10,
            channelBufferSize = 1
        )
        val source = PcapPacketSourceFactory.openLive(config)
        source.close()

        verify { nif.openLive(64, PromiscuousMode.NONPROMISCUOUS, 10) }
        verify { handle.close() }
    }

    @Test
    fun `openLive throws when interface not found`() {
        mockkStatic(Pcaps::class)
        every { Pcaps.getDevByName("missing0") } returns null

        val config = CaptureConfig(
            interfaceName = "missing0",
            promiscuous = false,
            snapshotLength = 128,
            readTimeoutMs = 10,
            channelBufferSize = 1
        )

        assertFailsWith<PcapNativeException> {
            PcapPacketSourceFactory.openLive(config)
        }
    }

    @Test
    fun `openOffline returns packet source and delegates to handle`() {
        mockkStatic(Pcaps::class)
        val handle = mockk<PcapHandle>(relaxed = true)
        every { Pcaps.openOffline("test-data/sample.pcap") } returns handle

        val source = PcapPacketSourceFactory.openOffline("test-data/sample.pcap")
        assertNotNull(source)
        source.close()
        verify { handle.close() }
    }
}
