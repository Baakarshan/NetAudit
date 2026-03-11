package com.netaudit.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * 告警规则定义。
 *
 * 规则仅在运行时使用，条件函数不参与序列化。
 */
data class AlertRule(
    val id: String,
    val name: String,
    val description: String,
    val level: AlertLevel,
    val condition: (AuditEvent) -> Boolean
)

/**
 * 触发后的告警记录。
 */
@Serializable
data class AlertRecord(
    val id: String,
    val timestamp: Instant,
    val level: AlertLevel,
    val ruleName: String,
    val message: String,
    val auditEventId: String,
    val protocol: ProtocolType
)
