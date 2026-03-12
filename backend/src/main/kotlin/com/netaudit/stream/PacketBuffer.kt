package com.netaudit.stream

import org.pcap4j.packet.Packet

/**
 * 原始包缓冲区，保存最近 N 条数据。
 *
 * 线程安全：使用 `synchronized` 保护队列，适合低开销读写场景。
 *
 * @param capacity 最大缓存条数，超出后丢弃最旧数据
 */
class PacketBuffer(
    private val capacity: Int = 1000
) {
    private val buffer = ArrayDeque<Packet>(capacity)
    private val lock = Any()

    /**
     * 追加新包，超出容量则丢弃最旧数据。
     *
     * @param packet 原始网络包
     */
    suspend fun add(packet: Packet) {
        synchronized(lock) {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(packet)
        }
    }

    /**
     * 获取最近 `count` 条数据，按时间顺序返回。
     */
    fun getRecent(count: Int): List<Packet> {
        if (count <= 0) return emptyList()
        return synchronized(lock) {
            buffer.takeLast(count)
        }
    }
}
