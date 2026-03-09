package com.netaudit.api

import kotlin.test.Test
import kotlin.test.assertEquals

class SseRoutesTest {
    @Test
    fun `SSE comment format`() {
        assertEquals(": connected\n\n", sseComment("connected"))
    }

    @Test
    fun `SSE event format`() {
        assertEquals("event: audit\n" + "data: {}\n\n", sseEvent("audit", "{}"))
    }
}
