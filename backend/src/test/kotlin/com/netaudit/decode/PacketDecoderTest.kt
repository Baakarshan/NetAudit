package com.netaudit.decode

import com.netaudit.model.TransportProtocol
import org.pcap4j.packet.*
import org.pcap4j.packet.namednumber.*
import org.pcap4j.util.MacAddress
import java.net.InetAddress
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

        val srcAddr = InetAddress.getByName("192.168.1.100") as java.net.Inet4Address
        val dstAddr = InetAddress.getByName("192.168.1.1") as java.net.Inet4Address

        val tcpBuilder = TcpPacket.Builder()
            .srcPort(TcpPort(54321.toShort(), ""))
            .dstPort(TcpPort.HTTP)
            .sequenceNumber(1000)
            .acknowledgmentNumber(0)
            .syn(false)
            .ack(true)
            .srcAddr(srcAddr)
            .dstAddr(dstAddr)
            .payloadBuilder(UnknownPacket.Builder().rawData(payload))
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)

        val ipBuilder = IpV4Packet.Builder()
            .version(IpVersion.IPV4)
            .tos(IpV4Rfc791Tos.newInstance(0))
            .ttl(64.toByte())
            .srcAddr(srcAddr)
            .dstAddr(dstAddr)
            .protocol(IpNumber.TCP)
            .payloadBuilder(tcpBuilder)
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)

        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(ipBuilder)
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

        val srcAddr = InetAddress.getByName("192.168.1.100") as java.net.Inet4Address
        val dstAddr = InetAddress.getByName("8.8.8.8") as java.net.Inet4Address

        val udpBuilder = UdpPacket.Builder()
            .srcPort(UdpPort(54321.toShort(), ""))
            .dstPort(UdpPort.DOMAIN)
            .srcAddr(srcAddr)
            .dstAddr(dstAddr)
            .payloadBuilder(UnknownPacket.Builder().rawData(dnsQuery))
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)

        val ipBuilder = IpV4Packet.Builder()
            .version(IpVersion.IPV4)
            .tos(IpV4Rfc791Tos.newInstance(0))
            .ttl(64.toByte())
            .srcAddr(srcAddr)
            .dstAddr(dstAddr)
            .protocol(IpNumber.UDP)
            .payloadBuilder(udpBuilder)
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)

        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(ipBuilder)
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
            .srcProtocolAddr(InetAddress.getByName("192.168.1.100") as java.net.Inet4Address)
            .dstHardwareAddr(MacAddress.ETHER_BROADCAST_ADDRESS)
            .dstProtocolAddr(InetAddress.getByName("192.168.1.1") as java.net.Inet4Address)
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
        val srcAddr = InetAddress.getByName("192.168.1.100") as java.net.Inet4Address
        val dstAddr = InetAddress.getByName("192.168.1.1") as java.net.Inet4Address

        val tcpBuilder = TcpPacket.Builder()
            .srcPort(TcpPort(54321.toShort(), ""))
            .dstPort(TcpPort.HTTP)
            .sequenceNumber(1000)
            .acknowledgmentNumber(0)
            .syn(true)
            .ack(false)
            .srcAddr(srcAddr)
            .dstAddr(dstAddr)
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)

        val ipBuilder = IpV4Packet.Builder()
            .version(IpVersion.IPV4)
            .tos(IpV4Rfc791Tos.newInstance(0))
            .ttl(64.toByte())
            .srcAddr(srcAddr)
            .dstAddr(dstAddr)
            .protocol(IpNumber.TCP)
            .payloadBuilder(tcpBuilder)
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)

        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(ipBuilder)
            .paddingAtBuild(true)
            .build()

        val metadata = decoder.decode(ethPacket)

        assertNotNull(metadata)
        assertNotNull(metadata.tcpFlags)
        assertEquals(true, metadata.tcpFlags?.syn)
        assertTrue(metadata.payload.isEmpty())
    }
}
