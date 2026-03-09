package com.netaudit.api

import com.netaudit.model.AlertLevel
import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import com.netaudit.model.AppJson
import com.netaudit.storage.AlertRepository
import com.netaudit.storage.AuditRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditRoutesTest {
    @Test
    fun `GET audit logs default pagination`() = testApplication {
        environment { config = MapApplicationConfig() }
        val auditRepo = mockk<AuditRepository>()
        val sample = sampleHttpEvent()
        coEvery { auditRepo.findAll(0, 50) } returns listOf(sample)

        application {
            install(ContentNegotiation) { json(AppJson) }
            routing { auditRoutes(auditRepo) }
        }

        val response = client.get("/api/audit/logs")
        assertEquals(HttpStatusCode.OK, response.status)
        val events = AppJson.decodeFromString(
            ListSerializer(AuditEvent.serializer()),
            response.bodyAsText()
        )
        assertEquals(1, events.size)
    }

    @Test
    fun `GET audit logs protocol filter`() = testApplication {
        environment { config = MapApplicationConfig() }
        val auditRepo = mockk<AuditRepository>()
        val sample = sampleHttpEvent()
        coEvery { auditRepo.findByProtocol(ProtocolType.HTTP, 0, 50) } returns listOf(sample)

        application {
            install(ContentNegotiation) { json(AppJson) }
            routing { auditRoutes(auditRepo) }
        }

        val response = client.get("/api/audit/logs?protocol=HTTP")
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { auditRepo.findByProtocol(ProtocolType.HTTP, 0, 50) }
    }

    @Test
    fun `GET audit recent`() = testApplication {
        environment { config = MapApplicationConfig() }
        val auditRepo = mockk<AuditRepository>()
        val sample = sampleHttpEvent()
        coEvery { auditRepo.findRecent(2) } returns listOf(sample)

        application {
            install(ContentNegotiation) { json(AppJson) }
            routing { auditRoutes(auditRepo) }
        }

        val response = client.get("/api/audit/recent?limit=2")
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { auditRepo.findRecent(2) }
    }

    @Test
    fun `GET audit by id`() = testApplication {
        environment { config = MapApplicationConfig() }
        val auditRepo = mockk<AuditRepository>()
        val sample = sampleHttpEvent()
        coEvery { auditRepo.findByEventId(sample.id) } returns sample

        application {
            install(ContentNegotiation) { json(AppJson) }
            routing { auditRoutes(auditRepo) }
        }

        val response = client.get("/api/audit/${sample.id}")
        assertEquals(HttpStatusCode.OK, response.status)
        val event = AppJson.decodeFromString(AuditEvent.serializer(), response.bodyAsText())
        assertEquals(sample.id, event.id)
    }

    @Test
    fun `GET audit by id not found`() = testApplication {
        environment { config = MapApplicationConfig() }
        val auditRepo = mockk<AuditRepository>()
        coEvery { auditRepo.findByEventId("missing") } returns null

        application {
            install(ContentNegotiation) { json(AppJson) }
            routing { auditRoutes(auditRepo) }
        }

        val response = client.get("/api/audit/missing")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET stats dashboard`() = testApplication {
        environment { config = MapApplicationConfig() }
        val auditRepo = mockk<AuditRepository>()
        val alertRepo = mockk<AlertRepository>()
        coEvery { auditRepo.countAll() } returns 100
        coEvery { auditRepo.countByProtocol() } returns mapOf(ProtocolType.HTTP to 80L)
        coEvery { alertRepo.countByLevel() } returns mapOf(AlertLevel.WARN to 3L)

        application {
            install(ContentNegotiation) { json(AppJson) }
            routing { statsRoutes(auditRepo, alertRepo) }
        }

        val response = client.get("/api/stats/dashboard")
        assertEquals(HttpStatusCode.OK, response.status)
        val stats = AppJson.decodeFromString(DashboardStats.serializer(), response.bodyAsText())
        assertEquals(100, stats.totalEvents)
        assertEquals(80, stats.protocolCounts["HTTP"])
        assertEquals(3, stats.alertCounts["WARN"])
    }

    @Test
    fun `GET stats protocols`() = testApplication {
        environment { config = MapApplicationConfig() }
        val auditRepo = mockk<AuditRepository>()
        val alertRepo = mockk<AlertRepository>()
        coEvery { auditRepo.countByProtocol() } returns mapOf(ProtocolType.HTTP to 2L, ProtocolType.DNS to 1L)
        coEvery { alertRepo.countByLevel() } returns emptyMap()

        application {
            install(ContentNegotiation) { json(AppJson) }
            routing { statsRoutes(auditRepo, alertRepo) }
        }

        val response = client.get("/api/stats/protocols")
        assertEquals(HttpStatusCode.OK, response.status)
        val counts = AppJson.decodeFromString(
            MapSerializer(String.serializer(), Long.serializer()),
            response.bodyAsText()
        )
        assertEquals(2L, counts["HTTP"])
        assertEquals(1L, counts["DNS"])
    }

    @Test
    fun `GET alerts recent`() = testApplication {
        environment { config = MapApplicationConfig() }
        val alertRepo = mockk<AlertRepository>()
        coEvery { alertRepo.findRecent(3) } returns emptyList()

        application {
            install(ContentNegotiation) { json(AppJson) }
            routing { alertRoutes(alertRepo) }
        }

        val response = client.get("/api/alerts/recent?limit=3")
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { alertRepo.findRecent(3) }
    }

    @Test
    fun `GET alerts stats`() = testApplication {
        environment { config = MapApplicationConfig() }
        val alertRepo = mockk<AlertRepository>()
        coEvery { alertRepo.countByLevel() } returns mapOf(AlertLevel.INFO to 4L)

        application {
            install(ContentNegotiation) { json(AppJson) }
            routing { alertRoutes(alertRepo) }
        }

        val response = client.get("/api/alerts/stats")
        assertEquals(HttpStatusCode.OK, response.status)
        val counts = AppJson.decodeFromString(
            MapSerializer(String.serializer(), Long.serializer()),
            response.bodyAsText()
        )
        assertEquals(4L, counts["INFO"])
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
