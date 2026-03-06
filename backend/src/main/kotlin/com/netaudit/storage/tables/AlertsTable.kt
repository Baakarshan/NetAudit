package com.netaudit.storage.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

/**
 * alerts 表 — 存储告警记录。
 */
object AlertsTable : Table("alerts") {
    val id = long("id").autoIncrement()
    val alertId = varchar("alert_id", 36).uniqueIndex()
    val timestamp = timestampWithTimeZone("timestamp").index()
    val level = varchar("level", 10).index()
    val ruleName = varchar("rule_name", 100)
    val message = text("message")
    val auditEventId = varchar("audit_event_id", 36)
    val protocol = varchar("protocol", 10)
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
