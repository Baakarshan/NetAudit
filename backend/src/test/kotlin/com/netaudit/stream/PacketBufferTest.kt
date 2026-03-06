package com.netaudit.stream

import kotlinx.coroutines.test.runTest
import org.pcap4j.packet.UnknownPacket
import kotlin.test.Test
import kotlin.test.assertEquals

class PacketBufferTest {
    @Test
    fun `test buffer keeps recent packets`() = runTest {
        val buffer = PacketBuffer(capacity = 2)

        val p1 = UnknownPacket.Builder().rawData(byteArrayOf(1)).build()
        val p2 = UnknownPacket.Builder().rawData(byteArrayOf(2)).build()
        val p3 = UnknownPacket.Builder().rawData(byteArrayOf(3)).build()

        buffer.add(p1)
        buffer.add(p2)
        buffer.add(p3)

        val recent = buffer.getRecent(2)
        assertEquals(listOf(p2, p3), recent)
    }
}
