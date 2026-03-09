package com.netaudit.api

import com.netaudit.event.AuditEventBus
import com.netaudit.model.AuditEvent
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SseRoutesTest {
    @Test
    fun `SSE emits audit event`() = testApplication {
        environment { config = MapApplicationConfig() }
        val eventBus = AuditEventBus()

        application {
            routing { sseRoutes(eventBus) }
        }

        val event = AuditEvent.HttpEvent(
            id = "event-1",
            timestamp = Clock.System.now(),
            srcIp = "192.168.1.100",
            dstIp = "93.184.216.34",
            srcPort = 54321,
            dstPort = 80,
            method = "GET",
            url = "http://example.com/index.html",
            host = "example.com",
            userAgent = "TestAgent/1.0",
            contentType = "text/html",
            statusCode = 200
        )

        client.prepareGet("/api/sse/events").execute { response ->
            val channel = response.bodyAsChannel()
            coroutineScope {
                launch { eventBus.emitAudit(event) }

                val line1 = withTimeout(1000) { channel.readUTF8Line() }
                val line2 = withTimeout(1000) { channel.readUTF8Line() }
                val line3 = withTimeout(1000) { channel.readUTF8Line() }

                assertEquals("event: audit", line1)
                assertNotNull(line2)
                assertTrue(line2.startsWith("data: "))
                assertEquals("", line3)
            }
            channel.cancel(CancellationException("SSE test completed"))
        }
    }
}
