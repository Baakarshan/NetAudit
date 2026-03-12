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
    val eventId = varchar("event_id", 36).uniqueIndex() // 业务侧事件 UUID
    val protocol = varchar("protocol", 10).index() // 协议类型
    val srcIp = varchar("src_ip", 45).index() // 客户端 IP
    val dstIp = varchar("dst_ip", 45) // 服务端 IP
    val srcPort = integer("src_port") // 客户端端口
    val dstPort = integer("dst_port") // 服务端端口
    val alertLevel = varchar("alert_level", 10).default("INFO") // 告警等级
    val capturedAt = timestampWithTimeZone("captured_at").index() // 抓包时间
    val details = jsonb<AuditEvent>("details", AppJson) // 协议特有字段 JSONB
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
