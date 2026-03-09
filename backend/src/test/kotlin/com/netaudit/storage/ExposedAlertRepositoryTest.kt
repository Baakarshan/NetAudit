package com.netaudit.storage

import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord
import com.netaudit.model.ProtocolType
import com.netaudit.storage.impl.ExposedAlertRepository
import com.netaudit.storage.tables.AlertsTable
import com.netaudit.storage.tables.AuditLogsTable
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ExposedAlertRepositoryTest {
    private lateinit var repository: ExposedAlertRepository

    @BeforeTest
    fun setup() {
        Database.connect(
            url = "jdbc:h2:mem:test_alerts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        DatabaseFactory.createTables()
        repository = ExposedAlertRepository()
        DatabaseFactory.forceSuspend = true
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(AuditLogsTable, AlertsTable)
        }
        DatabaseFactory.forceSuspend = false
    }

    @Test
    fun `test save and findRecent`() = runTest {
        val alert = AlertRecord(
            id = "alert-1",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            level = AlertLevel.WARN,
            ruleName = "test-rule",
            message = "test-message",
            auditEventId = "event-1",
            protocol = ProtocolType.HTTP
        )

        repository.save(alert)

        val recent = repository.findRecent(1)
        assertEquals(1, recent.size)
        assertEquals(alert.id, recent[0].id)
        assertEquals(AlertLevel.WARN, recent[0].level)
    }

    @Test
    fun `test default limit`() = runTest {
        val alert = AlertRecord(
            id = "alert-default",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            level = AlertLevel.INFO,
            ruleName = "rule-default",
            message = "message-default",
            auditEventId = "event-default",
            protocol = ProtocolType.HTTP
        )
        repository.save(alert)

        val recent = repository.findRecent()
        assertEquals(alert.id, recent.first().id)
    }

    @Test
    fun `test countByLevel`() = runTest {
        val alert1 = AlertRecord(
            id = "alert-1",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            level = AlertLevel.INFO,
            ruleName = "rule-1",
            message = "message-1",
            auditEventId = "event-1",
            protocol = ProtocolType.HTTP
        )
        val alert2 = alert1.copy(id = "alert-2", level = AlertLevel.CRITICAL)

        repository.save(alert1)
        repository.save(alert2)

        val counts = repository.countByLevel()
        assertEquals(1L, counts[AlertLevel.INFO])
        assertEquals(1L, counts[AlertLevel.CRITICAL])
    }

    @Test
    fun `empty repository returns empty results`() = runTest {
        val recent = repository.findRecent(5)
        assertEquals(0, recent.size)

        val counts = repository.countByLevel()
        assertEquals(0, counts.size)
    }
}
