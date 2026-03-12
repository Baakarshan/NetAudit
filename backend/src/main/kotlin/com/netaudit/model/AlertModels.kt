package com.netaudit.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * 告警规则定义。
 *
 * 说明：
 * - 规则仅在运行时使用；
 * - `condition` 为函数类型，不参与序列化；
 * - 规则命中后会生成 `AlertRecord` 写入存储并推送给前端。
 *
 * @param id 规则唯一标识
 * @param name 规则名称（用于日志与 UI 展示）
 * @param description 规则描述（用于告警消息拼装）
 * @param level 告警等级
 * @param condition 匹配条件（true 表示触发）
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
 *
 * @param id 告警唯一标识
 * @param timestamp 触发时间（UTC）
 * @param level 告警等级
 * @param ruleName 规则名称
 * @param message 告警内容（可读文本）
 * @param auditEventId 关联的审计事件 ID
 * @param protocol 关联协议类型
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
