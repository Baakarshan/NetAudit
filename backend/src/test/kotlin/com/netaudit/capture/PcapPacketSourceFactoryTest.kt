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
        every { Pcaps.findAllDevs() } returns emptyList()

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
    fun `openLive resolves by description when name missing`() {
        mockkStatic(Pcaps::class)
        val handle = mockk<PcapHandle>(relaxed = true)
        val nif = mockk<PcapNetworkInterface>()
        every { Pcaps.getDevByName("wifi") } returns null
        every { Pcaps.findAllDevs() } returns listOf(nif)
        every { nif.description } returns "Intel Wi-Fi Adapter"
        every { nif.name } returns "\\Device\\NPF_{WIFI}"
        every { nif.openLive(64, PromiscuousMode.PROMISCUOUS, 10) } returns handle

        val config = CaptureConfig(
            interfaceName = "wifi",
            promiscuous = true,
            snapshotLength = 64,
            readTimeoutMs = 10,
            channelBufferSize = 1
        )

        val source = PcapPacketSourceFactory.openLive(config)
        source.close()

        verify { handle.close() }
    }

    @Test
    fun `openLive resolves by name equals when description not matched`() {
        mockkStatic(Pcaps::class)
        val handle = mockk<PcapHandle>(relaxed = true)
        val nif = mockk<PcapNetworkInterface>()
        every { Pcaps.getDevByName("eth1") } returns null
        every { Pcaps.findAllDevs() } returns listOf(nif)
        every { nif.description } returns "Other"
        every { nif.name } returns "eth1"
        every { nif.openLive(64, PromiscuousMode.NONPROMISCUOUS, 10) } returns handle

        val config = CaptureConfig(
            interfaceName = "ETH1",
            promiscuous = false,
            snapshotLength = 64,
            readTimeoutMs = 10,
            channelBufferSize = 1
        )

        val source = PcapPacketSourceFactory.openLive(config)
        source.close()

        verify { handle.close() }
    }

    @Test
    fun `openLive resolves by name contains when partial match`() {
        mockkStatic(Pcaps::class)
        val handle = mockk<PcapHandle>(relaxed = true)
        val nif = mockk<PcapNetworkInterface>()
        every { Pcaps.getDevByName("vmnet") } returns null
        every { Pcaps.findAllDevs() } returns listOf(nif)
        every { nif.description } returns "VMware"
        every { nif.name } returns "VMnet1"
        every { nif.openLive(64, PromiscuousMode.PROMISCUOUS, 10) } returns handle

        val config = CaptureConfig(
            interfaceName = "vmnet",
            promiscuous = true,
            snapshotLength = 64,
            readTimeoutMs = 10,
            channelBufferSize = 1
        )

        val source = PcapPacketSourceFactory.openLive(config)
        source.close()

        verify { handle.close() }
    }

    @Test
    fun `openLive normalizes device name`() {
        mockkStatic(Pcaps::class)
        val handle = mockk<PcapHandle>(relaxed = true)
        val nif = mockk<PcapNetworkInterface>()
        every { Pcaps.getDevByName("\\Device\\NPF_{ABC}") } returns nif
        every { nif.openLive(64, PromiscuousMode.PROMISCUOUS, 10) } returns handle

        val config = CaptureConfig(
            interfaceName = "\\\\Device\\\\NPF_{{ABC}}",
            promiscuous = true,
            snapshotLength = 64,
            readTimeoutMs = 10,
            channelBufferSize = 1
        )

        val source = PcapPacketSourceFactory.openLive(config)
        source.close()

        verify { handle.close() }
    }

    @Test
    fun `openLive throws with available list when no match`() {
        mockkStatic(Pcaps::class)
        val nif = mockk<PcapNetworkInterface>()
        every { Pcaps.getDevByName("unknown") } returns null
        every { Pcaps.findAllDevs() } returns listOf(nif)
        every { nif.description } returns "Ethernet"
        every { nif.name } returns "eth0"

        val config = CaptureConfig(
            interfaceName = "unknown",
            promiscuous = false,
            snapshotLength = 64,
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
