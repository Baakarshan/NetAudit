package com.netaudit.event

import com.netaudit.model.AuditEvent
import com.netaudit.model.AlertRecord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局事件总线，基于 Kotlin SharedFlow。
 * - auditEvents: 审计事件流（Parser → DbWriter + SSE + AlertEngine）
 * - alertEvents: 告警事件流（AlertEngine → SSE + DbWriter）
 */
class AuditEventBus {
    private val _auditEvents = MutableSharedFlow<AuditEvent>(
        replay = 0,
        extraBufferCapacity = 1024
    )
    val auditEvents: SharedFlow<AuditEvent> = _auditEvents.asSharedFlow()

    private val _alertEvents = MutableSharedFlow<AlertRecord>(
        replay = 0,
        extraBufferCapacity = 256
    )
    val alertEvents: SharedFlow<AlertRecord> = _alertEvents.asSharedFlow()

    suspend fun emitAudit(event: AuditEvent) {
        _auditEvents.emit(event)
    }

    suspend fun emitAlert(alert: AlertRecord) {
        _alertEvents.emit(alert)
    }
}
