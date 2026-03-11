package com.netaudit.storage

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * 网络数据包基础表。
 *
 * 用于存储原始包级元信息，可作为历史结构的兼容保留。
 */
object PacketsTable : UUIDTable("packets") {
    val timestamp = timestamp("timestamp")
    val protocol = varchar("protocol", 20)
    val srcIp = varchar("src_ip", 45)
    val dstIp = varchar("dst_ip", 45)
    val srcPort = integer("src_port").nullable()
    val dstPort = integer("dst_port").nullable()
    val payloadSize = integer("payload_size")
    val rawData = binary("raw_data").nullable()
    val createdAt = timestamp("created_at")
}

/**
 * HTTP 会话表。
 */
object HttpSessionsTable : UUIDTable("http_sessions") {
    val packetId = uuid("packet_id").references(PacketsTable.id)
    val method = varchar("method", 10).nullable()
    val url = text("url").nullable()
    val host = varchar("host", 255).nullable()
    val userAgent = text("user_agent").nullable()
    val statusCode = integer("status_code").nullable()
    val contentType = varchar("content_type", 100).nullable()
    val createdAt = timestamp("created_at")
}

/**
 * FTP 会话表。
 */
object FtpSessionsTable : UUIDTable("ftp_sessions") {
    val packetId = uuid("packet_id").references(PacketsTable.id)
    val command = varchar("command", 20).nullable()
    val argument = text("argument").nullable()
    val responseCode = integer("response_code").nullable()
    val responseMessage = text("response_message").nullable()
    val createdAt = timestamp("created_at")
}

/**
 * TELNET 会话表。
 */
object TelnetSessionsTable : UUIDTable("telnet_sessions") {
    val packetId = uuid("packet_id").references(PacketsTable.id)
    val command = varchar("command", 50).nullable()
    val data = text("data").nullable()
    val createdAt = timestamp("created_at")
}

/**
 * DNS 查询表。
 */
object DnsQueriesTable : UUIDTable("dns_queries") {
    val packetId = uuid("packet_id").references(PacketsTable.id)
    val queryType = varchar("query_type", 10)
    val domain = varchar("domain", 255)
    val response = text("response").nullable()
    val createdAt = timestamp("created_at")
}

/**
 * SMTP 会话表。
 */
object SmtpSessionsTable : UUIDTable("smtp_sessions") {
    val packetId = uuid("packet_id").references(PacketsTable.id)
    val command = varchar("command", 20).nullable()
    val from = varchar("from", 255).nullable()
    val to = varchar("to", 255).nullable()
    val subject = text("subject").nullable()
    val createdAt = timestamp("created_at")
}

/**
 * POP3 会话表。
 */
object Pop3SessionsTable : UUIDTable("pop3_sessions") {
    val packetId = uuid("packet_id").references(PacketsTable.id)
    val command = varchar("command", 20).nullable()
    val argument = text("argument").nullable()
    val response = text("response").nullable()
    val createdAt = timestamp("created_at")
}

/**
 * 所有表的列表，用于批量操作与测试。
 */
val allTables = arrayOf(
    PacketsTable,
    HttpSessionsTable,
    FtpSessionsTable,
    TelnetSessionsTable,
    DnsQueriesTable,
    SmtpSessionsTable,
    Pop3SessionsTable
)
