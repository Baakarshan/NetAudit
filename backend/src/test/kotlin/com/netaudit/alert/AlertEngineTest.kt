package com.netaudit.alert

import com.netaudit.event.AuditEventBus
import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord
import com.netaudit.model.AlertRule
import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import com.netaudit.storage.AlertRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        engine.start()

        val received = mutableListOf<AlertRecord>()
        val collectJob = launch {
            eventBus.alertEvents.collect { alert ->
                received.add(alert)
                if (received.size >= 1) cancel()
            }
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

        coVerify { repository.save(any()) }
        assertTrue(received.isNotEmpty())
        collectJob.cancelAndJoin()
    }
}
