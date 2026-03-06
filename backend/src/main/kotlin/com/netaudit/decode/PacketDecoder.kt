package com.netaudit.decode

import com.netaudit.model.PacketMetadata
import com.netaudit.model.TransportProtocol
import com.netaudit.model.TcpFlags
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.pcap4j.packet.Packet
import org.pcap4j.packet.EthernetPacket
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.TcpPacket
import org.pcap4j.packet.UdpPacket
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 将 Pcap4J 的 Packet 对象解码为 PacketMetadata。
 * 利用 Pcap4J 内置的协议解析能力，不手动拆字节。
 *
 * 返回 null 表示该包不是我们感兴趣的（非 IPv4、非 TCP/UDP、无 payload）。
 */
class PacketDecoder {
    private val timestampMethodCache = ConcurrentHashMap<Class<*>, Method?>()

    /**
     * 主解码方法。
     * 解码流程:
     * 1. 检查是否包含 EthernetPacket → 提取 srcMac, dstMac
     * 2. 检查是否包含 IpV4Packet → 提取 srcIp, dstIp, protocol
     * 3. 根据 protocol:
     *    - TCP → 提取 TcpPacket 的 srcPort, dstPort, flags, seq, ack, payload
     *    - UDP → 提取 UdpPacket 的 srcPort, dstPort, payload
     * 4. 如果 payload 为空 → 对于 UDP 返回 null，对于 TCP 仍然返回（需要感知连接生命周期）
     * 5. 组装 PacketMetadata 返回
     */
    fun decode(packet: Packet): PacketMetadata? {
        // L2 - Ethernet
        val ethPacket = packet.get(EthernetPacket::class.java) ?: return null
        val srcMac = ethPacket.header.srcAddr.toString()
        val dstMac = ethPacket.header.dstAddr.toString()

        // L3 - IPv4
        val ipPacket = packet.get(IpV4Packet::class.java) ?: return null
        val srcIp = ipPacket.header.srcAddr.hostAddress
        val dstIp = ipPacket.header.dstAddr.hostAddress

        // 时间戳：优先使用包自带时间戳，缺失时使用当前时间
        val timestamp = resolveTimestamp(packet)

        // L4 - TCP
        val tcpPacket = packet.get(TcpPacket::class.java)
        if (tcpPacket != null) {
            val payload = tcpPacket.payload?.rawData ?: ByteArray(0)
            // 纯控制包 (SYN/FIN/RST without payload) 仍然需要送出去
            // 因为 TCP 流重组需要感知连接生命周期

            return PacketMetadata(
                timestamp = timestamp,
                srcMac = srcMac,
                dstMac = dstMac,
                srcIp = srcIp,
                dstIp = dstIp,
                ipProtocol = TransportProtocol.TCP,
                srcPort = tcpPacket.header.srcPort.valueAsInt(),
                dstPort = tcpPacket.header.dstPort.valueAsInt(),
                tcpFlags = TcpFlags(
                    syn = tcpPacket.header.syn,
                    ack = tcpPacket.header.ack,
                    fin = tcpPacket.header.fin,
                    rst = tcpPacket.header.rst,
                    psh = tcpPacket.header.psh
                ),
                seqNumber = tcpPacket.header.sequenceNumberAsLong,
                ackNumber = tcpPacket.header.acknowledgmentNumberAsLong,
                payload = payload
            )
        }

        // L4 - UDP
        val udpPacket = packet.get(UdpPacket::class.java)
        if (udpPacket != null) {
            val payload = udpPacket.payload?.rawData ?: return null  // UDP 无 payload 则跳过
            return PacketMetadata(
                timestamp = timestamp,
                srcMac = srcMac,
                dstMac = dstMac,
                srcIp = srcIp,
                dstIp = dstIp,
                ipProtocol = TransportProtocol.UDP,
                srcPort = udpPacket.header.srcPort.valueAsInt(),
                dstPort = udpPacket.header.dstPort.valueAsInt(),
                tcpFlags = null,
                seqNumber = null,
                ackNumber = null,
                payload = payload
            )
        }

        return null  // 既不是 TCP 也不是 UDP
    }

    private fun resolveTimestamp(packet: Packet): Instant {
        val method = timestampMethodCache.computeIfAbsent(packet.javaClass) { clazz ->
            try {
                clazz.getMethod("getTimestamp")
            } catch (_: NoSuchMethodException) {
                null
            }
        }

        val timestamp = try {
            method?.invoke(packet)
        } catch (_: Exception) {
            null
        }

        return when (timestamp) {
            is java.sql.Timestamp -> Instant.fromEpochMilliseconds(timestamp.time)
            is java.time.Instant -> Instant.fromEpochMilliseconds(timestamp.toEpochMilli())
            else -> Clock.System.now()
        }
    }
}
