package com.netaudit.event

import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord
import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditEventBusTest {
    @Test
    fun `audit event should be delivered`() = runTest {
        val bus = AuditEventBus()
        val event = AuditEvent.HttpEvent(
            id = "test-id",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 80,
            method = "GET",
            url = "http://example.com/",
            host = "example.com"
        )

        val received = async(start = CoroutineStart.UNDISPATCHED) { bus.auditEvents.first() }
        bus.emitAudit(event)

        assertEquals(event, received.await())
    }

    @Test
    fun `alert event should be delivered`() = runTest {
        val bus = AuditEventBus()
        val alert = AlertRecord(
            id = "alert-id",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            level = AlertLevel.WARN,
            ruleName = "test-rule",
            message = "test-message",
            auditEventId = "event-id",
            protocol = ProtocolType.HTTP
        )

        val received = async(start = CoroutineStart.UNDISPATCHED) { bus.alertEvents.first() }
        bus.emitAlert(alert)

        assertEquals(alert, received.await())
    }
}
