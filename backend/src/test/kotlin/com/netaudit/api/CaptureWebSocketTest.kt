package com.netaudit.api

import com.netaudit.event.AuditEventBus
import com.netaudit.model.AuditEvent
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureWebSocketTest {
    @Test
    fun `websocket responds to ping and emits events`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        val eventBus = AuditEventBus()

        application {
            install(ServerWebSockets)
            routing {
                captureWebSocket(eventBus)
            }
        }

        val wsClient = createClient {
            install(WebSockets)
        }

        wsClient.webSocket("/ws/capture") {
            send(Frame.Text("ping"))
            val pong = withTimeout(2000) { incoming.receive() as Frame.Text }
            assertEquals("pong", pong.readText())

            eventBus.emitAudit(sampleHttpEvent())
            val eventFrame = withTimeout(2000) { incoming.receive() as Frame.Text }
            assertTrue(eventFrame.readText().contains("\"protocol\""))
        }
    }

    private fun sampleHttpEvent(): AuditEvent.HttpEvent = AuditEvent.HttpEvent(
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
}
