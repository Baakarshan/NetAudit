package com.netaudit.model

import kotlinx.datetime.Instant

/**
 * TCP 连接的 4-tuple 标识。
 *
 * 用于在流追踪阶段关联双向数据包，保证同一连接能映射到唯一键。
 */
data class StreamKey(
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int
) {
    /**
     * 返回反向连接标识，用于匹配双向流。
     */
    fun reverse(): StreamKey = StreamKey(dstIp, dstPort, srcIp, srcPort)

    /**
     * 规范化当前 key。
     *
     * 确保较小的 IP:Port 在前，以便将双向流合并为同一 key。
     */
    fun canonical(): StreamKey {
        return if ("$srcIp:$srcPort" <= "$dstIp:$dstPort") this else reverse()
    }
}

/**
 * 传递给 ProtocolParser 的上下文对象。
 *
 * 包含包的元信息、应用层 payload、方向以及会话级状态容器。
 */
class StreamContext(
    val key: StreamKey,
    val metadata: PacketMetadata,
    val payload: ByteArray,
    val direction: Direction,
    val sessionState: MutableMap<String, Any> = mutableMapOf()
) {
    /** 便捷访问 */
    val srcIp: String get() = metadata.srcIp
    val dstIp: String get() = metadata.dstIp
    val srcPort: Int get() = metadata.srcPort
    val dstPort: Int get() = metadata.dstPort
    val timestamp: Instant get() = metadata.timestamp

    /**
     * 将 payload 解读为 UTF-8 文本。
     *
     * 仅适用于文本协议；二进制协议不应调用此方法。
     */
    fun payloadAsText(): String = payload.decodeToString()
}
