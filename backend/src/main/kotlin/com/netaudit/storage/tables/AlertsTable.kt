package com.netaudit.storage.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

/**
 * alerts 表 — 存储告警记录。
 */
object AlertsTable : Table("alerts") {
    val id = long("id").autoIncrement()
    val alertId = varchar("alert_id", 36).uniqueIndex() // 告警 UUID
    val timestamp = timestampWithTimeZone("timestamp").index() // 触发时间
    val level = varchar("level", 10).index() // 告警级别
    val ruleName = varchar("rule_name", 100) // 规则名称
    val message = text("message") // 告警详情
    val auditEventId = varchar("audit_event_id", 36) // 关联的审计事件 ID
    val protocol = varchar("protocol", 10) // 关联协议类型
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
