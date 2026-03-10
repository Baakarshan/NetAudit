package com.netaudit.alert

import com.netaudit.event.AuditEventBus
import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRule
import com.netaudit.model.AuditEvent
import com.netaudit.storage.AlertRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue

class AlertEngineTest {
    @Test
    fun `alert engine emits and saves alerts`() = runTest {
        val eventBus = AuditEventBus()
        val repository = mockk<AlertRepository>(relaxed = true)
        val rule = AlertRule(
            id = "rule-1",
            name = "always",
            description = "always alert",
            level = AlertLevel.WARN
        ) { true }

        val engine = AlertEngine(
            eventBus = eventBus,
            alertRepository = repository,
            scope = this,
            rules = listOf(rule)
        )
        val engineJob = engine.start()
        advanceUntilIdle()

        val alertDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            eventBus.alertEvents.first()
        }

        val event = AuditEvent.HttpEvent(
            id = "event-1",
            timestamp = Clock.System.now(),
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1111,
            dstPort = 80,
            method = "GET",
            url = "http://example.com",
            host = "example.com"
        )
        eventBus.emitAudit(event)
        advanceUntilIdle()
        val alert = withTimeout(1_000) { alertDeferred.await() }

        coVerify { repository.save(any()) }
        assertTrue(alert.auditEventId == event.id)
        engineJob.cancelAndJoin()
    }

    @Test
    fun `alert engine logs save failure without crashing`() = runTest {
        val eventBus = AuditEventBus()
        val repository = mockk<AlertRepository>(relaxed = true)
        coEvery { repository.save(any()) } throws RuntimeException("save failed")

        val rule = AlertRule(
            id = "rule-2",
            name = "always",
            description = "always alert",
            level = AlertLevel.WARN
        ) { true }

        val engine = AlertEngine(
            eventBus = eventBus,
            alertRepository = repository,
            scope = this,
            rules = listOf(rule)
        )
        val engineJob = engine.start()
        advanceUntilIdle()

        val alertDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            eventBus.alertEvents.first()
        }

        val event = AuditEvent.HttpEvent(
            id = "event-2",
            timestamp = Clock.System.now(),
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1112,
            dstPort = 80,
            method = "GET",
            url = "http://example.com",
            host = "example.com"
        )
        eventBus.emitAudit(event)
        advanceUntilIdle()

        val alert = withTimeout(1_000) { alertDeferred.await() }
        coVerify { repository.save(any()) }
        assertTrue(alert.auditEventId == event.id)
        engineJob.cancelAndJoin()
    }

    @Test
    fun `alert engine swallows rule exceptions`() = runTest {
        val eventBus = AuditEventBus()
        val repository = mockk<AlertRepository>(relaxed = true)
        val rule = AlertRule(
            id = "rule-3",
            name = "boom",
            description = "throws",
            level = AlertLevel.WARN
        ) { error("rule failure") }

        val engine = AlertEngine(
            eventBus = eventBus,
            alertRepository = repository,
            scope = this,
            rules = listOf(rule)
        )
        val engineJob = engine.start()
        advanceUntilIdle()

        val event = AuditEvent.HttpEvent(
            id = "event-3",
            timestamp = Clock.System.now(),
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1113,
            dstPort = 80,
            method = "GET",
            url = "http://example.com",
            host = "example.com"
        )
        eventBus.emitAudit(event)
        advanceUntilIdle()

        val alert = withTimeoutOrNull(300) { eventBus.alertEvents.first() }
        assertTrue(alert == null)
        engineJob.cancelAndJoin()
    }
}
