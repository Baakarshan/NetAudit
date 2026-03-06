package com.netaudit.stream

import org.pcap4j.packet.Packet

/**
 * 原始包缓冲区，保存最近 N 条数据。
 */
class PacketBuffer(
    private val capacity: Int = 1000
) {
    private val buffer = ArrayDeque<Packet>(capacity)
    private val lock = Any()

    suspend fun add(packet: Packet) {
        synchronized(lock) {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(packet)
        }
    }

    fun getRecent(count: Int): List<Packet> {
        if (count <= 0) return emptyList()
        return synchronized(lock) {
            buffer.takeLast(count)
        }
    }
}
