package com.netaudit.storage.tables

import com.netaudit.model.AppJson
import com.netaudit.model.AuditEvent
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb

/**
 * audit_logs 表 — 所有协议共用一张表，协议特有字段存 JSONB。
 */
object AuditLogsTable : Table("audit_logs") {
    val id = long("id").autoIncrement()
    val eventId = varchar("event_id", 36).uniqueIndex()
    val protocol = varchar("protocol", 10).index()
    val srcIp = varchar("src_ip", 45).index()
    val dstIp = varchar("dst_ip", 45)
    val srcPort = integer("src_port")
    val dstPort = integer("dst_port")
    val alertLevel = varchar("alert_level", 10).default("INFO")
    val capturedAt = timestampWithTimeZone("captured_at").index()
    val details = jsonb<AuditEvent>("details", AppJson)
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
