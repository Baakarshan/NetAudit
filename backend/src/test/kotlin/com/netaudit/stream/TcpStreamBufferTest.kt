package com.netaudit.stream

import com.netaudit.model.StreamKey
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TcpStreamBufferTest {
    @Test
    fun `append and consume data works`() {
        val buffer = TcpStreamBuffer(StreamKey("1.1.1.1", 1, "2.2.2.2", 2))

        buffer.appendClientData("hello".toByteArray())
        buffer.appendServerData("world".toByteArray())

        assertEquals("hello", buffer.clientData())
        assertEquals("world", buffer.serverData())

        assertEquals("hello", buffer.consumeClientData())
        assertEquals("", buffer.clientData())

        assertEquals("world", buffer.consumeServerData())
        assertEquals("", buffer.serverData())
    }

    @Test
    fun `isExpired respects timeout`() {
        val old = TcpStreamBuffer(
            StreamKey("1.1.1.1", 1, "2.2.2.2", 2),
            createdAt = Instant.fromEpochSeconds(0)
        )
        assertTrue(old.isExpired(timeoutSeconds = 1))

        val fresh = TcpStreamBuffer(StreamKey("1.1.1.1", 1, "2.2.2.2", 2))
        assertFalse(fresh.isExpired(timeoutSeconds = 3600))
    }
}
