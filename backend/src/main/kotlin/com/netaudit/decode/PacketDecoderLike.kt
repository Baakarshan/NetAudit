package com.netaudit.decode

import com.netaudit.model.PacketMetadata
import org.pcap4j.packet.Packet

/**
 * 解码器抽象。
 *
 * 约定：
 * - 返回 null 表示该包不参与上层解析（例如非 IPv4、非 TCP/UDP、或无有效载荷）。
 * - 实现应尽量容错，不抛出异常影响主链路。
 */
interface PacketDecoderLike {
    /**
     * 将原始 `Packet` 解码为统一的元数据结构。
     *
     * @param packet Pcap4J 捕获的原始包
     * @return 解析后的元数据；若不关心该包则返回 null
     */
    fun decode(packet: Packet): PacketMetadata?
}
