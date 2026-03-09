package com.netaudit.decode

import com.netaudit.model.TransportProtocol
import org.pcap4j.packet.*
import org.pcap4j.packet.namednumber.*
import org.pcap4j.util.MacAddress
import java.net.InetAddress
import java.sql.Timestamp
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

    @Test
    fun `decode uses sql timestamp when available`() {
        val ethPacket = buildTcpEthernetPacket("ping".toByteArray())
        val timestamp = Timestamp(1700000000000)
        val wrapped = TimestampedPacket(ethPacket, timestamp)

        val metadata = decoder.decode(wrapped)

        assertNotNull(metadata)
        assertEquals(
            kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp.time),
            metadata.timestamp
        )
    }

    @Test
    fun `decode uses java time instant when available`() {
        val ethPacket = buildTcpEthernetPacket("ping".toByteArray())
        val timestamp = java.time.Instant.parse("2024-01-01T00:00:00Z")
        val wrapped = TimestampedPacket(ethPacket, timestamp)

        val metadata = decoder.decode(wrapped)

        assertNotNull(metadata)
        assertEquals(
            kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp.toEpochMilli()),
            metadata.timestamp
        )
    }

    @Test
    fun `decode falls back to current time when timestamp unsupported`() {
        val ethPacket = buildTcpEthernetPacket("ping".toByteArray())
        val wrapped = NoTimestampPacket(ethPacket)

        val metadata = decoder.decode(wrapped)

        assertNotNull(metadata)
    }

    @Test
    fun `resolveTimestamp handles missing method`() {
        val method = PacketDecoder::class.java.getDeclaredMethod("resolveTimestamp", Packet::class.java)
        method.isAccessible = true
        val packet = NoTimestampPacket(buildTcpEthernetPacket("ping".toByteArray()))

        val result = method.invoke(decoder, packet) as kotlinx.datetime.Instant

        assertNotNull(result)
    }

    @Test
    fun `resolveTimestamp handles invocation failure`() {
        val method = PacketDecoder::class.java.getDeclaredMethod("resolveTimestamp", Packet::class.java)
        method.isAccessible = true
        val packet = ThrowingTimestampPacket(buildTcpEthernetPacket("ping".toByteArray()))

        val result = method.invoke(decoder, packet) as kotlinx.datetime.Instant

        assertNotNull(result)
    }

    @Test
    fun `parseIpFromPayload parses ipv4 from payload raw data`() {
        val ipPacket = buildUdpIpPacket(byteArrayOf(1, 2, 3, 4))
        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(UnknownPacket.Builder().rawData(ipPacket.rawData))
            .paddingAtBuild(true)
            .build()

        val method = PacketDecoder::class.java.getDeclaredMethod(
            "parseIpFromPayload",
            EthernetPacket::class.java
        )
        method.isAccessible = true
        val result = method.invoke(decoder, ethPacket) as IpV4Packet?

        assertNotNull(result)
    }

    @Test
    fun `parseIpFromPayload returns null on invalid ipv4 raw`() {
        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(UnknownPacket.Builder().rawData(byteArrayOf(1)))
            .paddingAtBuild(true)
            .build()

        val method = PacketDecoder::class.java.getDeclaredMethod(
            "parseIpFromPayload",
            EthernetPacket::class.java
        )
        method.isAccessible = true
        val result = method.invoke(decoder, ethPacket) as IpV4Packet?

        assertNull(result)
    }

    @Test
    fun `decode uses parseIpFromPayload when packet lacks ipv4 chain`() {
        val ipPacket = buildUdpIpPacket(byteArrayOf(1, 2, 3, 4))
        val udpPacket = ipPacket.payload as UdpPacket
        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(UnknownPacket.Builder().rawData(ipPacket.rawData))
            .paddingAtBuild(true)
            .build()

        val wrapped = PayloadIpWrapperPacket(ethPacket, udpPacket)
        val metadata = decoder.decode(wrapped)

        assertNotNull(metadata)
        assertEquals(TransportProtocol.UDP, metadata.ipProtocol)
    }

    @Test
    fun `decode returns null for non ethernet packet`() {
        val packet = UnknownPacket.Builder().rawData(byteArrayOf(1, 2, 3)).build()
        val metadata = decoder.decode(packet)
        assertNull(metadata)
    }

    @Test
    fun `decode returns null for udp without payload`() {
        val ipPacket = buildUdpIpPacket(null)
        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(ipPacket.builder)
            .paddingAtBuild(true)
            .build()

        val metadata = decoder.decode(ethPacket)

        assertNull(metadata)
    }

    @Test
    fun `decode returns null for ipv4 non tcp udp`() {
        val srcAddr = InetAddress.getByName("192.168.1.100") as java.net.Inet4Address
        val dstAddr = InetAddress.getByName("192.168.1.1") as java.net.Inet4Address

        val ipBuilder = IpV4Packet.Builder()
            .version(IpVersion.IPV4)
            .tos(IpV4Rfc791Tos.newInstance(0))
            .ttl(64.toByte())
            .srcAddr(srcAddr)
            .dstAddr(dstAddr)
            .protocol(IpNumber.ICMPV4)
            .payloadBuilder(UnknownPacket.Builder().rawData(byteArrayOf(1, 2, 3)))
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

        assertNull(metadata)
    }

    @Test
    fun `parseIpFromRaw handles short frame`() {
        val raw = ByteArray(14)
        raw[12] = 0x08
        raw[13] = 0x00
        val ethPacket = EthernetPacket.newPacket(raw, 0, raw.size)

        val method = PacketDecoder::class.java.getDeclaredMethod(
            "parseIpFromRaw",
            EthernetPacket::class.java
        )
        method.isAccessible = true
        val result = method.invoke(decoder, ethPacket) as IpV4Packet?

        assertNull(result)
    }

    @Test
    fun `parseIpFromRaw returns null on invalid ipv4 raw`() {
        val raw = ByteArray(15)
        raw[12] = 0x08
        raw[13] = 0x00
        val ethPacket = EthernetPacket.newPacket(raw, 0, raw.size)

        val method = PacketDecoder::class.java.getDeclaredMethod(
            "parseIpFromRaw",
            EthernetPacket::class.java
        )
        method.isAccessible = true
        val result = method.invoke(decoder, ethPacket) as IpV4Packet?

        assertNull(result)
    }

    @Test
    fun `parseIpFromRaw returns null for non ipv4 type`() {
        val raw = ByteArray(60)
        raw[12] = 0x08
        raw[13] = 0x06
        val ethPacket = EthernetPacket.newPacket(raw, 0, raw.size)

        val method = PacketDecoder::class.java.getDeclaredMethod(
            "parseIpFromRaw",
            EthernetPacket::class.java
        )
        method.isAccessible = true
        val result = method.invoke(decoder, ethPacket) as IpV4Packet?

        assertNull(result)
    }

    @Test
    fun `parseIpFromRaw parses ipv4 from ethernet raw`() {
        val ipPacket = buildUdpIpPacket(byteArrayOf(1, 2, 3, 4))
        val ethPacket = EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(UnknownPacket.Builder().rawData(ipPacket.rawData))
            .paddingAtBuild(true)
            .build()

        val method = PacketDecoder::class.java.getDeclaredMethod(
            "parseIpFromRaw",
            EthernetPacket::class.java
        )
        method.isAccessible = true
        val result = method.invoke(decoder, ethPacket) as IpV4Packet?

        assertNotNull(result)
    }

    private fun buildTcpEthernetPacket(payload: ByteArray): EthernetPacket {
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

        return EthernetPacket.Builder()
            .srcAddr(MacAddress.getByName("00:11:22:33:44:55"))
            .dstAddr(MacAddress.getByName("AA:BB:CC:DD:EE:FF"))
            .type(EtherType.IPV4)
            .payloadBuilder(ipBuilder)
            .paddingAtBuild(true)
            .build()
    }

    private fun buildUdpIpPacket(payload: ByteArray?): IpV4Packet {
        val srcAddr = InetAddress.getByName("192.168.1.100") as java.net.Inet4Address
        val dstAddr = InetAddress.getByName("8.8.8.8") as java.net.Inet4Address

        val udpBuilder = UdpPacket.Builder()
            .srcPort(UdpPort(54321.toShort(), ""))
            .dstPort(UdpPort.DOMAIN)
            .srcAddr(srcAddr)
            .dstAddr(dstAddr)
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)

        if (payload != null) {
            udpBuilder.payloadBuilder(UnknownPacket.Builder().rawData(payload))
        }

        return IpV4Packet.Builder()
            .version(IpVersion.IPV4)
            .tos(IpV4Rfc791Tos.newInstance(0))
            .ttl(64.toByte())
            .srcAddr(srcAddr)
            .dstAddr(dstAddr)
            .protocol(IpNumber.UDP)
            .payloadBuilder(udpBuilder)
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)
            .build()
    }

    private class TimestampedPacket(
        private val inner: Packet,
        private val timestamp: Any
    ) : Packet {
        @Suppress("unused")
        fun getTimestamp(): Any = timestamp

        override fun getHeader(): Packet.Header = inner.header

        override fun getPayload(): Packet? = inner.payload

        override fun length(): Int = inner.length()

        override fun getRawData(): ByteArray = inner.rawData

        override fun getBuilder(): Packet.Builder = inner.builder

        override fun <T : Packet> get(clazz: Class<T>): T? {
            if (clazz.isInstance(inner)) {
                return clazz.cast(inner)
            }
            return inner.get(clazz)
        }
    }

    private class ThrowingTimestampPacket(
        private val inner: Packet
    ) : Packet {
        @Suppress("unused")
        fun getTimestamp(): Any {
            throw IllegalStateException("boom")
        }

        override fun getHeader(): Packet.Header = inner.header

        override fun getPayload(): Packet? = inner.payload

        override fun length(): Int = inner.length()

        override fun getRawData(): ByteArray = inner.rawData

        override fun getBuilder(): Packet.Builder = inner.builder

        override fun <T : Packet> get(clazz: Class<T>): T? {
            if (clazz.isInstance(inner)) {
                return clazz.cast(inner)
            }
            return inner.get(clazz)
        }
    }

    private class NoTimestampPacket(
        private val inner: Packet
    ) : Packet {
        override fun getHeader(): Packet.Header = inner.header

        override fun getPayload(): Packet? = inner.payload

        override fun length(): Int = inner.length()

        override fun getRawData(): ByteArray = inner.rawData

        override fun getBuilder(): Packet.Builder = inner.builder

        override fun <T : Packet> get(clazz: Class<T>): T? {
            if (clazz.isInstance(inner)) {
                return clazz.cast(inner)
            }
            return inner.get(clazz)
        }
    }

    private class PayloadIpWrapperPacket(
        private val ethernetPacket: EthernetPacket,
        private val udpPacket: UdpPacket
    ) : Packet {
        override fun getHeader(): Packet.Header = ethernetPacket.header

        override fun getPayload(): Packet? = ethernetPacket.payload

        override fun length(): Int = ethernetPacket.length()

        override fun getRawData(): ByteArray = ethernetPacket.rawData

        override fun getBuilder(): Packet.Builder = ethernetPacket.builder

        override fun <T : Packet> get(clazz: Class<T>): T? {
            return when {
                clazz == EthernetPacket::class.java -> clazz.cast(ethernetPacket)
                clazz == IpV4Packet::class.java -> null
                clazz == UdpPacket::class.java -> clazz.cast(udpPacket)
                else -> ethernetPacket.get(clazz)
            }
        }
    }
}
