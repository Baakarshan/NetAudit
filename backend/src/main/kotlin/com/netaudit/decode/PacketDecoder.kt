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
import org.pcap4j.packet.namednumber.EtherType
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 将 Pcap4J 的 `Packet` 解码为统一 `PacketMetadata`。
 *
 * 设计目标：
 * - 尽量复用 Pcap4J 的解析能力，避免手动拆字节带来的易错性。
 * - 对异常报文做容错处理，保证主流程不被破坏。
 *
 * 返回 null 表示该包不参与上层解析（如非 IPv4 或 UDP 无 payload）。
 */
class PacketDecoder : PacketDecoderLike {
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
    override fun decode(packet: Packet): PacketMetadata? {
        // L2 - Ethernet
        val ethPacket = (packet as? EthernetPacket)
            ?: packet.get(EthernetPacket::class.java)
            ?: return null
        val srcMac = ethPacket.header.srcAddr.toString()
        val dstMac = ethPacket.header.dstAddr.toString()

        // L3 - IPv4
        // 优先走 Pcap4J 已解析的 IPv4 包；失败时再从 payload/raw 做兜底解析
        val ipPacket = (ethPacket.payload as? IpV4Packet)
            ?: packet.get(IpV4Packet::class.java)
            ?: parseIpFromPayload(ethPacket)
            ?: parseIpFromRaw(ethPacket)
            ?: return null
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

    /**
     * 当 Pcap4J 未能直接给出 IPv4 视图时，尝试从以太负载解析 IPv4。
     *
     * 该分支用于处理部分封装异常或非标准报文，属于容错路径。
     */
    private fun parseIpFromPayload(ethPacket: EthernetPacket): IpV4Packet? {
        if (ethPacket.header.type != EtherType.IPV4) {
            return null
        }
        // 有些报文会把 IPv4 放在 payload 的 rawData 中
        val payload = ethPacket.payload ?: return null
        val raw = payload.rawData ?: return null
        return try {
            IpV4Packet.newPacket(raw, 0, raw.size)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从以太帧原始字节中解析 IPv4（跳过 14 字节以太头）。
     *
     * 仅在确认为 IPv4 类型时尝试，避免误解析。
     */
    private fun parseIpFromRaw(ethPacket: EthernetPacket): IpV4Packet? {
        if (ethPacket.header.type != EtherType.IPV4) {
            return null
        }
        // 从以太帧原始字节中跳过 14 字节头部进行解析
        val raw = ethPacket.rawData ?: return null
        val headerSize = 14 // Ethernet header length (no VLAN)
        if (raw.size <= headerSize) {
            return null
        }
        return try {
            IpV4Packet.newPacket(raw, headerSize, raw.size - headerSize)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 解析时间戳。
     *
     * 优先读取包自带时间戳（不同 Packet 实现可能提供不同类型），
     * 若不存在则回退到当前时间，保证时间字段稳定可用。
     */
    private fun resolveTimestamp(packet: Packet): Instant {
        // 通过反射读取可能存在的 getTimestamp()，避免强依赖具体 Packet 实现
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
