package com.netaudit.alert

import com.netaudit.event.AuditEventBus
import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRule
import com.netaudit.model.AuditEvent
import com.netaudit.storage.AlertRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlertEngineTest {
    @Test
    fun `telnet event triggers alert`() = runTest {
        val eventBus = AuditEventBus()
        val alertRepo = mockk<AlertRepository>()
        coEvery { alertRepo.save(any()) } just Runs

        val engine = AlertEngine(eventBus, alertRepo, backgroundScope)
        engine.start()
        runCurrent()

        val alertDeferred = async { eventBus.alertEvents.first() }
        eventBus.emitAudit(telnetEvent(username = "admin"))
        runCurrent()

        val alert = withTimeout(1000) { alertDeferred.await() }
        assertEquals(AlertLevel.CRITICAL, alert.level)
        assertEquals("TELNET Login Detection", alert.ruleName)
    }

    @Test
    fun `normal http event does not trigger alert`() = runTest {
        val eventBus = AuditEventBus()
        val alertRepo = mockk<AlertRepository>()
        coEvery { alertRepo.save(any()) } just Runs

        val engine = AlertEngine(eventBus, alertRepo, backgroundScope)
        engine.start()
        runCurrent()

        eventBus.emitAudit(httpEvent(url = "http://example.com/index.html"))
        val alertDeferred = async { withTimeoutOrNull(200) { eventBus.alertEvents.first() } }
        advanceTimeBy(250)
        val alert = alertDeferred.await()
        assertNull(alert)
    }

    @Test
    fun `sensitive url triggers warn alert`() = runTest {
        val eventBus = AuditEventBus()
        val alertRepo = mockk<AlertRepository>()
        coEvery { alertRepo.save(any()) } just Runs

        val engine = AlertEngine(eventBus, alertRepo, backgroundScope)
        engine.start()
        runCurrent()

        val alertDeferred = async { eventBus.alertEvents.first() }
        eventBus.emitAudit(httpEvent(url = "http://example.com/admin/config"))
        runCurrent()

        val alert = withTimeout(1000) { alertDeferred.await() }
        assertEquals(AlertLevel.WARN, alert.level)
        assertEquals("Sensitive URL Access", alert.ruleName)
    }

    @Test
    fun `dns long domain triggers warn alert`() = runTest {
        val eventBus = AuditEventBus()
        val alertRepo = mockk<AlertRepository>()
        coEvery { alertRepo.save(any()) } just Runs

        val engine = AlertEngine(eventBus, alertRepo, backgroundScope)
        engine.start()
        runCurrent()

        val alertDeferred = async { eventBus.alertEvents.first() }
        val longDomain = "a".repeat(60) + ".example.com"
        eventBus.emitAudit(dnsEvent(queryDomain = longDomain))
        runCurrent()

        val alert = withTimeout(1000) { alertDeferred.await() }
        assertNotNull(alert)
        assertEquals(AlertLevel.WARN, alert.level)
        assertEquals("DNS Tunnel Suspicion", alert.ruleName)
    }

    @Test
    fun `alert save failure is handled`() = runTest {
        val eventBus = AuditEventBus()
        val alertRepo = mockk<AlertRepository>()
        coEvery { alertRepo.save(any()) } throws IllegalStateException("db fail")

        val engine = AlertEngine(eventBus, alertRepo, backgroundScope)
        engine.start()
        runCurrent()

        val alertDeferred = async { eventBus.alertEvents.first() }
        eventBus.emitAudit(telnetEvent(username = "admin"))
        runCurrent()

        val alert = withTimeout(1000) { alertDeferred.await() }
        assertNotNull(alert)
    }

    @Test
    fun `alert rule exceptions are ignored`() = runTest {
        val eventBus = AuditEventBus()
        val alertRepo = mockk<AlertRepository>()
        coEvery { alertRepo.save(any()) } just Runs

        val rules = listOf(
            AlertRule(
                id = "rule-err",
                name = "Throwing Rule",
                description = "boom",
                level = AlertLevel.WARN,
                condition = { throw IllegalStateException("boom") }
            )
        )

        val engine = AlertEngine(eventBus, alertRepo, backgroundScope, rules)
        engine.start()
        runCurrent()

        eventBus.emitAudit(httpEvent(url = "http://example.com/index.html"))
        val alertDeferred = async { withTimeoutOrNull(200) { eventBus.alertEvents.first() } }
        advanceTimeBy(250)
        val alert = alertDeferred.await()
        assertNull(alert)
    }

    private fun telnetEvent(username: String?): AuditEvent.TelnetEvent = AuditEvent.TelnetEvent(
        id = "telnet-1",
        timestamp = Clock.System.now(),
        srcIp = "192.168.1.100",
        dstIp = "10.0.0.1",
        srcPort = 54321,
        dstPort = 23,
        username = username,
        commandLine = "whoami",
        direction = com.netaudit.model.Direction.CLIENT_TO_SERVER
    )

    private fun httpEvent(url: String): AuditEvent.HttpEvent = AuditEvent.HttpEvent(
        id = "http-1",
        timestamp = Clock.System.now(),
        srcIp = "192.168.1.100",
        dstIp = "93.184.216.34",
        srcPort = 54321,
        dstPort = 80,
        method = "GET",
        url = url,
        host = "example.com",
        userAgent = "TestAgent/1.0",
        contentType = "text/html",
        statusCode = 200
    )

    private fun dnsEvent(queryDomain: String): AuditEvent.DnsEvent = AuditEvent.DnsEvent(
        id = "dns-1",
        timestamp = Clock.System.now(),
        srcIp = "192.168.1.100",
        dstIp = "8.8.8.8",
        srcPort = 5353,
        dstPort = 53,
        transactionId = 1234,
        queryDomain = queryDomain,
        queryType = "A",
        isResponse = false,
        resolvedIps = emptyList(),
        responseTtl = null
    )
}
