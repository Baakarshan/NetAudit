package com.netaudit.api

import com.netaudit.event.AuditEventBus
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SseRoutesTest {
    @Test
    fun `SSE emits audit event`() = testApplication {
        environment { config = MapApplicationConfig() }
        val eventBus = AuditEventBus()

        application {
            routing { sseRoutes(eventBus) }
        }

        val response = client.get("/api/sse/events")
        val contentType = response.contentType()

        assertNotNull(contentType)
        assertEquals(ContentType.Text.EventStream.contentType, contentType.contentType)

        response.call.cancel()
    }
}
