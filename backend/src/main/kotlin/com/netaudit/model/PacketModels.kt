package com.netaudit.model

import kotlinx.datetime.Instant

/**
 * L2-L4 解码后的结构化包元数据。
 *
 * 该结构用于上层解析与流量统计的统一输入，避免直接依赖底层 pcap 数据结构。
 */
data class TcpFlags(
    /** SYN 标志位 */
    val syn: Boolean,
    /** ACK 标志位 */
    val ack: Boolean,
    /** FIN 标志位 */
    val fin: Boolean,
    /** RST 标志位 */
    val rst: Boolean,
    /** PSH 标志位 */
    val psh: Boolean
)

/**
 * 单个网络包的元数据与载荷信息。
 *
 * @param timestamp 捕获时间（UTC）
 * @param srcMac 源 MAC
 * @param dstMac 目标 MAC
 * @param srcIp 源 IP
 * @param dstIp 目标 IP
 * @param ipProtocol 传输层协议
 * @param srcPort 源端口
 * @param dstPort 目标端口
 * @param tcpFlags TCP 标志位，UDP 时为 null
 * @param seqNumber TCP 序列号，UDP 时为 null
 * @param ackNumber TCP 确认号，UDP 时为 null
 * @param payload L7 应用层载荷
 */
data class PacketMetadata(
    val timestamp: Instant,
    val srcMac: String,
    val dstMac: String,
    val srcIp: String,
    val dstIp: String,
    val ipProtocol: TransportProtocol,
    val srcPort: Int,
    val dstPort: Int,
    val tcpFlags: TcpFlags?,       // null if UDP
    val seqNumber: Long?,          // null if UDP
    val ackNumber: Long?,          // null if UDP
    val payload: ByteArray         // L7 应用层 payload
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PacketMetadata) return false

        // 基于 timestamp + srcIp + srcPort + seqNumber 判等
        if (timestamp != other.timestamp) return false
        if (srcIp != other.srcIp) return false
        if (srcPort != other.srcPort) return false
        if (seqNumber != other.seqNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + srcIp.hashCode()
        result = 31 * result + srcPort
        result = 31 * result + (seqNumber?.hashCode() ?: 0)
        return result
    }
}
