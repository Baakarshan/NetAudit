package com.netaudit.model

import kotlinx.datetime.Instant

/** L2-L4 解码后的结构化包元数据 */
data class TcpFlags(
    val syn: Boolean,
    val ack: Boolean,
    val fin: Boolean,
    val rst: Boolean,
    val psh: Boolean
)

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
