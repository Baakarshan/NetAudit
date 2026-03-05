package com.netaudit.decode

import com.netaudit.model.TransportProtocol
import kotlinx.datetime.Clock
import org.pcap4j.packet.*
import org.pcap4j.packet.namednumber.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacketDecoderTest {
    private val decoder = PacketDecoder()

    @Test
    fun `test decode valid TCP HTTP packet`() {
        val payload = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray()

        val tcpPacket = TcpPacket.Builder()
            .srcPort(TcpPort(54321.toShort()))
            .dstPort(TcpPort.HTTP)
            .sequenceNumber(1000)
            .acknowledgmentNumber(0)
            .syn(false)
            .ack(true)
            .payloadBuilder(UnknownPacket.Builder().rawData(payload))
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)
            .build()

        val ipPacket = IpV4Packet.Builder()
            .srcAddr(IpV4Address.getByName("192.168.1.100"))
            .dstAddr(IpV4Address.getByName("192.168.1.1"))
            .protocol(IpNumber.TCP)
            .payloadBuilder(UnknownPacket.Builder().rawData(tcpPacket.rawData))
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)
            .build()

        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(UnknownPacket.Builder().rawData(ipPacket.rawData))
            .paddingAtBuild(true)
            .build()

        val metadata = decoder.decode(ethPacket)

        assertNotNull(metadata)
        assertEquals("192.168.1.100", metadata.srcIp)
        assertEquals("192.168.1.1", metadata.dstIp)
        assertEquals(54321, metadata.srcPort)
        assertEquals(80, metadata.dstPort)
        assertEquals(TransportProtocol.TCP, metadata.ipProtocol)
        assertNotNull(metadata.tcpFlags)
        assertEquals(false, metadata.tcpFlags?.syn)
        assertEquals(true, metadata.tcpFlags?.ack)
        assertTrue(metadata.payload.isNotEmpty())
    }

    @Test
    fun `test decode valid UDP DNS packet`() {
        val dnsQuery = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01)

        val udpPacket = UdpPacket.Builder()
            .srcPort(UdpPort(54321.toShort()))
            .dstPort(UdpPort.DOMAIN)
            .payloadBuilder(UnknownPacket.Builder().rawData(dnsQuery))
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)
            .build()

        val ipPacket = IpV4Packet.Builder()
            .srcAddr(IpV4Address.getByName("192.168.1.100"))
            .dstAddr(IpV4Address.getByName("8.8.8.8"))
            .protocol(IpNumber.UDP)
            .payloadBuilder(UnknownPacket.Builder().rawData(udpPacket.rawData))
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)
            .build()

        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(UnknownPacket.Builder().rawData(ipPacket.rawData))
            .paddingAtBuild(true)
            .build()

        val metadata = decoder.decode(ethPacket)

        assertNotNull(metadata)
        assertEquals(TransportProtocol.UDP, metadata.ipProtocol)
        assertEquals(53, metadata.dstPort)
    }

    @Test
    fun `test decode non-IPv4 packet returns null`() {
        val arpPacket = ArpPacket.Builder()
            .hardwareType(ArpHardwareType.ETHERNET)
            .protocolType(EtherType.IPV4)
            .operation(ArpOperation.REQUEST)
            .srcHardwareAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .srcProtocolAddr(IpV4Address.getByName("192.168.1.100"))
            .dstHardwareAddr(MacAddress.ETHER_BROADCAST_ADDRESS)
            .dstProtocolAddr(IpV4Address.getByName("192.168.1.1"))
            .build()

        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.ETHER_BROADCAST_ADDRESS)
            .type(EtherType.ARP)
            .payloadBuilder(UnknownPacket.Builder().rawData(arpPacket.rawData))
            .paddingAtBuild(true)
            .build()

        val metadata = decoder.decode(ethPacket)

        assertNull(metadata)
    }

    @Test
    fun `test decode TCP SYN packet with no payload`() {
        val tcpPacket = TcpPacket.Builder()
            .srcPort(TcpPort(54321.toShort()))
            .dstPort(TcpPort.HTTP)
            .sequenceNumber(1000)
            .acknowledgmentNumber(0)
            .syn(true)
            .ack(false)
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)
            .build()

        val ipPacket = IpV4Packet.Builder()
            .srcAddr(IpV4Address.getByName("192.168.1.100"))
            .dstAddr(IpV4Address.getByName("192.168.1.1"))
            .protocol(IpNumber.TCP)
            .payloadBuilder(UnknownPacket.Builder().rawData(tcpPacket.rawData))
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)
            .build()

        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(UnknownPacket.Builder().rawData(ipPacket.rawData))
            .paddingAtBuild(true)
            .build()

        val metadata = decoder.decode(ethPacket)

        assertNotNull(metadata)
        assertNotNull(metadata.tcpFlags)
        assertEquals(true, metadata.tcpFlags?.syn)
        assertTrue(metadata.payload.isEmpty())
    }
}
