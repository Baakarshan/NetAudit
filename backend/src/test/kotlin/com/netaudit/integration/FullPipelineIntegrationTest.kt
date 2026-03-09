package com.netaudit.integration

import com.netaudit.alert.AlertEngine
import com.netaudit.config.CaptureConfig
import com.netaudit.config.DatabaseConfig
import com.netaudit.event.AuditEventBus
import com.netaudit.model.AppJson
import com.netaudit.parser.ParserRegistry
import com.netaudit.parser.dns.DnsParser
import com.netaudit.parser.email.Pop3Parser
import com.netaudit.parser.email.SmtpParser
import com.netaudit.parser.ftp.FtpParser
import com.netaudit.parser.http.HttpParser
import com.netaudit.parser.telnet.TelnetParser
import com.netaudit.pipeline.AuditPipeline
import com.netaudit.storage.BatchWriter
import com.netaudit.storage.DatabaseFactory
import com.netaudit.storage.impl.ExposedAlertRepository
import com.netaudit.storage.impl.ExposedAuditRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import kotlin.test.assertTrue

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "INTEGRATION_DATABASE_URL", matches = ".+")
class FullPipelineIntegrationTest {
    @Test
    fun `full pipeline offline writes to database`() = runBlocking {
        val pcapFile = File("test-data/sample.pcap")
        assumeTrue(pcapFile.exists() && pcapFile.length() > 24, "sample.pcap missing or empty")

        val databaseUrl = requireNotNull(System.getenv("INTEGRATION_DATABASE_URL"))
        val databaseUser = System.getenv("INTEGRATION_DATABASE_USER") ?: "netaudit"
        val databasePassword = System.getenv("INTEGRATION_DATABASE_PASSWORD") ?: "netaudit"
        val databaseDriver = System.getenv("INTEGRATION_DATABASE_DRIVER") ?: "org.postgresql.Driver"

        val dbConfig = DatabaseConfig(
            url = databaseUrl,
            driver = databaseDriver,
            user = databaseUser,
            password = databasePassword,
            maxPoolSize = 5
        )

        DatabaseFactory.init(dbConfig)

        val auditRepo = ExposedAuditRepository(AppJson)
        val alertRepo = ExposedAlertRepository()
        val eventBus = AuditEventBus()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val batchWriter = BatchWriter(auditRepo, eventBus, scope, batchSize = 20, flushIntervalMs = 500)
        batchWriter.start()

        val alertEngine = AlertEngine(eventBus, alertRepo, scope)
        alertEngine.start()

        val registry = ParserRegistry().apply {
            register(HttpParser())
            register(FtpParser())
            register(TelnetParser())
            register(DnsParser())
            register(SmtpParser())
            register(Pop3Parser())
        }

        val captureConfig = CaptureConfig(
            interfaceName = "eth0",
            snapshotLength = 65536,
            promiscuous = true,
            readTimeoutMs = 100,
            channelBufferSize = 4096
        )

        val pipeline = AuditPipeline(captureConfig, registry, eventBus, scope)
        try {
            pipeline.startOffline(pcapFile.absolutePath)
            withTimeoutOrNull(8000) { delay(8000) }
            batchWriter.shutdown()

            val totalEvents = auditRepo.countAll()
            assertTrue(totalEvents > 0, "No audit events persisted")

            val protocolCounts = auditRepo.countByProtocol()
            assertTrue(protocolCounts.isNotEmpty(), "No protocol counts found")

            val alerts = alertRepo.findRecent(10)
            if (alerts.isNotEmpty()) {
                assertTrue(alerts.all { it.message.isNotBlank() })
            }
        } finally {
            pipeline.stop()
            scope.cancel()
            DatabaseFactory.close()
        }
    }
}
