package com.netaudit.event

import com.netaudit.model.AuditEvent
import com.netaudit.model.AlertRecord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局事件总线，基于 Kotlin `SharedFlow`。
 *
 * 用途：
 * - `auditEvents`：审计事件流（解析器 → 存储/告警/推送）。
 * - `alertEvents`：告警事件流（告警引擎 → 推送/存储）。
 */
class AuditEventBus {
    private val _auditEvents = MutableSharedFlow<AuditEvent>(
        replay = 0,
        extraBufferCapacity = 1024
    )
    /** 审计事件只读流。 */
    val auditEvents: SharedFlow<AuditEvent> = _auditEvents.asSharedFlow()

    private val _alertEvents = MutableSharedFlow<AlertRecord>(
        replay = 0,
        extraBufferCapacity = 256
    )
    /** 告警事件只读流。 */
    val alertEvents: SharedFlow<AlertRecord> = _alertEvents.asSharedFlow()

    /** 推送审计事件。 */
    suspend fun emitAudit(event: AuditEvent) {
        _auditEvents.emit(event)
    }

    /** 推送告警事件。 */
    suspend fun emitAlert(alert: AlertRecord) {
        _alertEvents.emit(alert)
    }
}
