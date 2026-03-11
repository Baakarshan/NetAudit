package com.netaudit.decode

import com.netaudit.model.PacketMetadata
import org.pcap4j.packet.Packet

/**
 * 解码器抽象。
 *
 * 约定：返回 null 表示该包不参与上层解析（例如非 IPv4 或无有效载荷）。
 */
interface PacketDecoderLike {
    /**
     * 将原始 `Packet` 解码为统一的元数据结构。
     */
    fun decode(packet: Packet): PacketMetadata?
}
