package com.netaudit.stream

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.pcap4j.packet.UnknownPacket
import kotlin.test.Test
import kotlin.test.assertEquals

class PacketStreamTest {
    @Test
    fun `test emit and collect`() = runTest {
        val stream = PacketStream()
        val packet = UnknownPacket.Builder().rawData(byteArrayOf(1, 2, 3)).build()

        val received = async { stream.packets.first() }
        stream.emit(packet)

        assertEquals(packet, received.await())
    }
}
