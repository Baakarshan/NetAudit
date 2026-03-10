package com.netaudit

import com.netaudit.config.AppConfig
import com.netaudit.config.CaptureConfig
import com.netaudit.config.DatabaseConfig
import com.netaudit.event.AuditEventBus
import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord
import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import com.netaudit.parser.ParserRegistry
import com.netaudit.storage.AlertRepository
import com.netaudit.storage.AuditRepository
import com.netaudit.alert.AlertEngine
import com.netaudit.pipeline.AuditPipeline
import com.netaudit.storage.BatchWriter
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.Runs
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MainTest {
    @Test
    fun `runServer respects disable flag`() {
        var called = false
        System.setProperty("netaudit.disableMain", "true")
        runServer(emptyArray()) { called = true }
        assertFalse(called)

        System.clearProperty("netaudit.disableMain")
        runServer(arrayOf("arg")) { called = true }
        assertTrue(called)
    }

    @Test
    fun `main delegates to runServer`() {
        System.setProperty("netaudit.disableMain", "true")
        main(emptyArray())
        System.clearProperty("netaudit.disableMain")
    }

    @Test
    fun `main logger is initialized`() {
        val clazz = Class.forName("com.netaudit.MainKt")
        val field = clazz.getDeclaredField("logger")
        field.isAccessible = true
        assertNotNull(field.get(null))
    }

    @Test
    fun `module registers parsers and routes without background`() = testApplication {
        environment { config = MapApplicationConfig() }
        val registry = ParserRegistry()
        val eventBus = AuditEventBus()
        var dbInitCalled = false
        var batchCalled = false
        var pipelineCalled = false
        var alertCalled = false

        val config = sampleConfig(alertEnabled = true)

        application {
            module(
                config = config,
                registry = registry,
                eventBus = eventBus,
                databaseInit = { dbInitCalled = true },
                auditRepoProvider = { FakeAuditRepository() },
                alertRepoProvider = { FakeAlertRepository() },
                batchWriterStarter = { _, _, _ -> batchCalled = true },
                pipelineStarter = { _, _, _, _ -> pipelineCalled = true },
                alertEngineStarter = { _, _, _ -> alertCalled = true },
                startBackground = false
            )
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(6, registry.allParsers().size)
        assertTrue(dbInitCalled)
        assertFalse(batchCalled)
        assertFalse(pipelineCalled)
        assertFalse(alertCalled)
    }

    @Test
    fun `module uses defaults without background`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:defaults;DB_CLOSE_DELAY=-1",
                "database.driver" to "org.h2.Driver",
                "database.user" to "sa",
                "database.password" to "",
                "database.maxPoolSize" to "2",
                "capture.interface" to "lo",
                "capture.promiscuous" to "false",
                "capture.snapshotLength" to "65536",
                "capture.readTimeout" to "100",
                "capture.channelBufferSize" to "16",
                "alert.enabled" to "false"
            )
        }

        application {
            module(startBackground = false)
        }

        startApplication()

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `module default starters invoked when enabled`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:defaults2;DB_CLOSE_DELAY=-1",
                "database.driver" to "org.h2.Driver",
                "database.user" to "sa",
                "database.password" to "",
                "database.maxPoolSize" to "2",
                "capture.interface" to "netaudit-test0",
                "capture.promiscuous" to "false",
                "capture.snapshotLength" to "65536",
                "capture.readTimeout" to "100",
                "capture.channelBufferSize" to "16",
                "alert.enabled" to "true"
            )
        }

        mockkConstructor(BatchWriter::class)
        mockkConstructor(AuditPipeline::class)
        mockkConstructor(AlertEngine::class)
        every { anyConstructed<BatchWriter>().start() } just Runs
        every { anyConstructed<AuditPipeline>().start() } just Runs
        every { anyConstructed<AlertEngine>().start() } returns mockk(relaxed = true)

        try {
            application {
                module(startBackground = true)
            }

            startApplication()

            verify { anyConstructed<BatchWriter>().start() }
            verify { anyConstructed<AuditPipeline>().start() }
            verify { anyConstructed<AlertEngine>().start() }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `module uses default startBackground value`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:defaults3;DB_CLOSE_DELAY=-1",
                "database.driver" to "org.h2.Driver",
                "database.user" to "sa",
                "database.password" to "",
                "database.maxPoolSize" to "2",
                "capture.interface" to "netaudit-test1",
                "capture.promiscuous" to "false",
                "capture.snapshotLength" to "65536",
                "capture.readTimeout" to "100",
                "capture.channelBufferSize" to "16",
                "alert.enabled" to "true"
            )
        }

        mockkConstructor(BatchWriter::class)
        mockkConstructor(AuditPipeline::class)
        mockkConstructor(AlertEngine::class)
        every { anyConstructed<BatchWriter>().start() } just Runs
        every { anyConstructed<AuditPipeline>().start() } just Runs
        every { anyConstructed<AlertEngine>().start() } returns mockk(relaxed = true)

        try {
            application {
                module()
            }

            startApplication()

            verify { anyConstructed<BatchWriter>().start() }
            verify { anyConstructed<AuditPipeline>().start() }
            verify { anyConstructed<AlertEngine>().start() }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `module starts background services when enabled`() = testApplication {
        environment { config = MapApplicationConfig() }
        val registry = ParserRegistry()
        val eventBus = AuditEventBus()
        var dbInitCalled = false
        var batchCalled = false
        var pipelineCalled = false
        var alertCalled = false

        val config = sampleConfig(alertEnabled = true)

        application {
            module(
                config = config,
                registry = registry,
                eventBus = eventBus,
                databaseInit = { dbInitCalled = true },
                auditRepoProvider = { FakeAuditRepository() },
                alertRepoProvider = { FakeAlertRepository() },
                batchWriterStarter = { _, _, _ -> batchCalled = true },
                pipelineStarter = { _, _, _, _ -> pipelineCalled = true },
                alertEngineStarter = { _, _, _ -> alertCalled = true },
                startBackground = true
            )
        }

        startApplication()

        assertTrue(dbInitCalled)
        assertTrue(batchCalled)
        assertTrue(pipelineCalled)
        assertTrue(alertCalled)
    }

    @Test
    fun `module skips alert engine when disabled`() = testApplication {
        environment { config = MapApplicationConfig() }
        val registry = ParserRegistry()
        val eventBus = AuditEventBus()
        var dbInitCalled = false
        var batchCalled = false
        var pipelineCalled = false
        var alertCalled = false

        val config = sampleConfig(alertEnabled = false)

        application {
            module(
                config = config,
                registry = registry,
                eventBus = eventBus,
                databaseInit = { dbInitCalled = true },
                auditRepoProvider = { FakeAuditRepository() },
                alertRepoProvider = { FakeAlertRepository() },
                batchWriterStarter = { _, _, _ -> batchCalled = true },
                pipelineStarter = { _, _, _, _ -> pipelineCalled = true },
                alertEngineStarter = { _, _, _ -> alertCalled = true },
                startBackground = true
            )
        }

        startApplication()

        assertTrue(dbInitCalled)
        assertTrue(batchCalled)
        assertTrue(pipelineCalled)
        assertFalse(alertCalled)
    }

    private fun sampleConfig(alertEnabled: Boolean): AppConfig = AppConfig(
        database = DatabaseConfig(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
            maxPoolSize = 2
        ),
        capture = CaptureConfig(
            interfaceName = "eth0",
            promiscuous = true,
            snapshotLength = 65536,
            readTimeoutMs = 100,
            channelBufferSize = 16
        ),
        alertEnabled = alertEnabled
    )

    private class FakeAuditRepository : AuditRepository {
        override suspend fun save(event: AuditEvent) = Unit

        override suspend fun saveBatch(events: List<AuditEvent>) = Unit

        override suspend fun findAll(page: Int, size: Int): List<AuditEvent> = emptyList()

        override suspend fun findByProtocol(protocol: ProtocolType, page: Int, size: Int): List<AuditEvent> =
            emptyList()

        override suspend fun findBySourceIp(srcIp: String, page: Int, size: Int): List<AuditEvent> = emptyList()

        override suspend fun findBetween(start: Instant, end: Instant, page: Int, size: Int): List<AuditEvent> =
            emptyList()

        override suspend fun findRecent(limit: Int): List<AuditEvent> = emptyList()

        override suspend fun findByEventId(eventId: String): AuditEvent? = null

        override suspend fun countAll(): Long = 0

        override suspend fun countByProtocol(): Map<ProtocolType, Long> = emptyMap()
    }

    private class FakeAlertRepository : AlertRepository {
        override suspend fun save(alert: AlertRecord) = Unit

        override suspend fun findRecent(limit: Int): List<AlertRecord> = emptyList()

        override suspend fun countByLevel(): Map<AlertLevel, Long> = emptyMap()
    }
}
