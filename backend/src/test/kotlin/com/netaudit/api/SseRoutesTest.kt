package com.netaudit.api

import com.netaudit.event.AuditEventBus
import com.netaudit.model.AuditEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SseRoutesTest {
    @Test
    fun `SSE emits audit event`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val eventBus = AuditEventBus()
        val server = embeddedServer(Netty, port = port) {
            routing { sseRoutes(eventBus) }
        }.start()

        val client = HttpClient(CIO)
        try {
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

            client.prepareGet("http://localhost:$port/api/sse/events").execute { response ->
                val channel = response.bodyAsChannel()
                val comment = withTimeout(1000) { channel.readUTF8Line() }
                val commentSeparator = withTimeout(1000) { channel.readUTF8Line() }

                assertEquals(": connected", comment)
                assertEquals("", commentSeparator)

                eventBus.emitAudit(event)

                val line1 = withTimeout(1000) { channel.readUTF8Line() }
                val line2 = withTimeout(1000) { channel.readUTF8Line() }
                val line3 = withTimeout(1000) { channel.readUTF8Line() }

                assertEquals("event: audit", line1)
                assertNotNull(line2)
                assertTrue(line2.startsWith("data: "))
                assertEquals("", line3)

                response.call.cancel()
            }
        } finally {
            client.close()
            server.stop(1000, 2000)
        }
    }
}
