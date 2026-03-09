package com.netaudit.capture

import kotlinx.coroutines.channels.ReceiveChannel
import org.pcap4j.packet.Packet

interface CaptureEngine {
    val rawPacketChannel: ReceiveChannel<Packet>
    fun startLive()
    fun startOffline(pcapFilePath: String)
    fun stop()
}
