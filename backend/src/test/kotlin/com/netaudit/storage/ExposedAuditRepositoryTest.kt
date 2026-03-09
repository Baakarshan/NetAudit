package com.netaudit.storage

import com.netaudit.model.AuditEvent
import com.netaudit.model.AppJson
import com.netaudit.model.ProtocolType
import com.netaudit.storage.impl.ExposedAuditRepository
import com.netaudit.storage.tables.AlertsTable
import com.netaudit.storage.tables.AuditLogsTable
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ExposedAuditRepositoryTest {
    private lateinit var repository: ExposedAuditRepository

    @BeforeTest
    fun setup() {
        Database.connect(
            url = "jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        DatabaseFactory.createTables()
        repository = ExposedAuditRepository(AppJson)
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(AuditLogsTable, AlertsTable)
        }
    }

    @Test
    fun `test save and findRecent`() = runTest {
        val event = httpEvent("test-1")
        repository.save(event)

        val recent = repository.findRecent(1)
        assertEquals(1, recent.size)
        assertEquals(event.id, recent[0].id)
        assertEquals(ProtocolType.HTTP, recent[0].protocol)
    }

    @Test
    fun `test saveBatch and countByProtocol`() = runTest {
        val httpEvents = List(3) { i -> httpEvent("http-$i") }
        val dnsEvents = List(2) { i -> dnsEvent("dns-$i") }

        repository.saveBatch(httpEvents + dnsEvents)

        val counts = repository.countByProtocol()
        assertEquals(3L, counts[ProtocolType.HTTP])
        assertEquals(2L, counts[ProtocolType.DNS])
    }

    @Test
    fun `test findByProtocol`() = runTest {
        val httpEvent = httpEvent("http-1")
        val dnsEvent = dnsEvent("dns-1")

        repository.saveBatch(listOf(httpEvent, dnsEvent))

        val httpResults = repository.findByProtocol(ProtocolType.HTTP)
        assertEquals(1, httpResults.size)
        assertEquals(ProtocolType.HTTP, httpResults[0].protocol)
    }

    @Test
    fun `test findBySourceIp`() = runTest {
        val event1 = httpEvent("test-1", srcIp = "192.168.1.100")
        val event2 = httpEvent("test-2", srcIp = "192.168.1.200")

        repository.saveBatch(listOf(event1, event2))

        val results = repository.findBySourceIp("192.168.1.100")
        assertEquals(1, results.size)
        assertEquals("192.168.1.100", results[0].srcIp)
    }

    @Test
    fun `test findBetween`() = runTest {
        val now = Clock.System.now()
        val eventOld = httpEvent("old", timestamp = now - 10.seconds)
        val eventNew = httpEvent("new", timestamp = now)

        repository.saveBatch(listOf(eventOld, eventNew))

        val results = repository.findBetween(now - 5.seconds, now + 5.seconds)
        assertTrue(results.any { it.id == "new" })
        assertTrue(results.none { it.id == "old" })
    }

    @Test
    fun `test countAll and findByEventId`() = runTest {
        val event = httpEvent("event-1")
        repository.save(event)

        assertEquals(1L, repository.countAll())
        val found = repository.findByEventId(event.id)
        assertEquals(event.id, found?.id)
    }

    @Test
    fun `test default parameters`() = runTest {
        val event = httpEvent("default-1")
        repository.save(event)

        assertTrue(repository.findAll().isNotEmpty())
        assertTrue(repository.findByProtocol(ProtocolType.HTTP).isNotEmpty())
        assertTrue(repository.findBySourceIp(event.srcIp).isNotEmpty())
        assertTrue(
            repository.findBetween(event.timestamp - 1.seconds, event.timestamp + 1.seconds).isNotEmpty()
        )
        assertTrue(repository.findRecent().isNotEmpty())
    }

    @Test
    fun `test pagination`() = runTest {
        val events = List(10) { i -> httpEvent("test-$i") }
        repository.saveBatch(events)

        val page0 = repository.findAll(page = 0, size = 3)
        val page1 = repository.findAll(page = 1, size = 3)

        assertEquals(3, page0.size)
        assertEquals(3, page1.size)
        assertNotEquals(page0[0].id, page1[0].id)
    }

    private fun httpEvent(
        id: String,
        srcIp: String = "192.168.1.100",
        timestamp: Instant = Clock.System.now()
    ): AuditEvent.HttpEvent =
        AuditEvent.HttpEvent(
            id = id,
            timestamp = timestamp,
            srcIp = srcIp,
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 80,
            method = "GET",
            url = "/test",
            host = "example.com",
            userAgent = "TestAgent/1.0",
            contentType = "application/json",
            statusCode = 200
        )

    private fun dnsEvent(id: String): AuditEvent.DnsEvent =
        AuditEvent.DnsEvent(
            id = id,
            timestamp = Clock.System.now(),
            srcIp = "192.168.1.100",
            dstIp = "8.8.8.8",
            srcPort = 54321,
            dstPort = 53,
            transactionId = 1234,
            queryDomain = "example.com",
            queryType = "A",
            isResponse = true,
            resolvedIps = listOf("93.184.216.34"),
            responseTtl = 3600
        )
}
