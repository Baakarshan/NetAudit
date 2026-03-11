package com.netaudit.stream

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.pcap4j.packet.Packet

/**
 * 基于 SharedFlow 的原始包数据流。
 *
 * 适用场景：
 * - 多订阅者并行消费原始包。
 * - 依赖 `extraBufferCapacity` 提供背压缓冲。
 */
class PacketStream(capacity: Int = 1000) {
    private val _packets = MutableSharedFlow<Packet>(
        replay = 0,
        extraBufferCapacity = capacity
    )
    /** 对外只读的包流。 */
    val packets: SharedFlow<Packet> = _packets.asSharedFlow()

    /**
     * 推送新包。
     *
     * 由生产者调用，若缓冲耗尽则会挂起以形成背压。
     */
    suspend fun emit(packet: Packet) {
        _packets.emit(packet)
    }
}
