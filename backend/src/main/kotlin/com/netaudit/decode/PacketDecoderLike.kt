package com.netaudit.decode

import com.netaudit.model.PacketMetadata
import org.pcap4j.packet.Packet

interface PacketDecoderLike {
    fun decode(packet: Packet): PacketMetadata?
}
