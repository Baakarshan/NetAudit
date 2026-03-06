package com.netaudit.stream

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.pcap4j.packet.Packet

/**
 * 基于 SharedFlow 的原始包数据流。
 */
class PacketStream(capacity: Int = 1000) {
    private val _packets = MutableSharedFlow<Packet>(
        replay = 0,
        extraBufferCapacity = capacity
    )
    val packets: SharedFlow<Packet> = _packets.asSharedFlow()

    suspend fun emit(packet: Packet) {
        _packets.emit(packet)
    }
}
