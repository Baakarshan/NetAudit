package com.netaudit.api

import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord
import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue

class SseRoutesTest {
    @Test
    fun `sse emits audit and alert events`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        val auditEvents = flowOf(sampleHttpEvent())
        val alertEvents = flowOf(sampleAlert())

        application {
            routing {
                sseRoutes(auditEvents, alertEvents)
            }
        }

        val response = client.get("/api/sse/events")
        val body = response.bodyAsText()

        assertTrue(body.contains(": connected"))
        assertTrue(body.contains("event: audit"))
        assertTrue(body.contains("event: alert"))
    }

    @Test
    fun `sse handles stream and encode errors`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        val auditEvents = flow {
            emit(sampleHttpEvent())
            throw IllegalStateException("audit boom")
        }
        val alertEvents = flow {
            emit(sampleAlert())
            throw IllegalStateException("alert boom")
        }

        application {
            routing {
                sseRoutes(
                    auditEvents = auditEvents,
                    alertEvents = alertEvents,
                    auditEncoder = { _ -> throw IllegalArgumentException("encode failed") },
                    alertEncoder = { it.id }
                )
            }
        }

        val response = client.get("/api/sse/events")
        val body = response.bodyAsText()

        assertTrue(body.contains(": connected"))
        assertTrue(body.contains("event: alert"))
    }

    @Test
    fun `sse handles audit stream cancellation`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        val auditEvents = flow {
            emit(sampleHttpEvent())
            throw CancellationException("audit cancel")
        }

        application {
            routing {
                sseRoutes(auditEvents = auditEvents, alertEvents = emptyFlow())
            }
        }

        val response = client.get("/api/sse/events")
        val body = response.bodyAsText()
        assertTrue(body.contains(": connected"))
    }

    @Test
    fun `sse handles alert stream cancellation`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        val alertEvents = flow {
            emit(sampleAlert())
            throw CancellationException("alert cancel")
        }

        application {
            routing {
                sseRoutes(auditEvents = emptyFlow(), alertEvents = alertEvents)
            }
        }

        val response = client.get("/api/sse/events")
        val body = response.bodyAsText()
        assertTrue(body.contains(": connected"))
    }

    @Test
    fun `handleSseSession handles write error`() = runTest {
        handleSseSession(
            auditEvents = emptyFlow(),
            alertEvents = emptyFlow(),
            auditEncoder = { "" },
            alertEncoder = { "" },
            writeLine = { throw IllegalStateException("write failed") },
            flush = { }
        )
        assertTrue(true)
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

    private fun sampleAlert(): AlertRecord = AlertRecord(
        id = "alert-1",
        timestamp = Clock.System.now(),
        level = AlertLevel.WARN,
        ruleName = "test-rule",
        message = "test alert",
        auditEventId = "event-1",
        protocol = ProtocolType.HTTP
    )
}
