package com.netaudit.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * 审计事件基类 — 所有协议的审计记录。
 *
 * ⚠️ IP 地址约定（全局统一）：
 * - srcIp/srcPort: 始终为**客户端**（发起连接的一方）
 * - dstIp/dstPort: 始终为**服务端**（提供服务的一方）
 * - 即使 Parser 在解析服务端→客户端方向的包时，也需反转 IP 以符合此约定
 * - 目的：前端查询时可统一按"客户端 IP"过滤，无需关心包方向
 */
@Serializable
sealed interface AuditEvent {
    val id: String               // UUID string
    val timestamp: Instant
    val srcIp: String
    val dstIp: String
    val srcPort: Int
    val dstPort: Int
    val protocol: ProtocolType
    val alertLevel: AlertLevel

    @Serializable
    data class HttpEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        override val protocol: ProtocolType = ProtocolType.HTTP,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val method: String,          // GET, POST, etc.
        val url: String,             // 完整 URL (host + path)
        val host: String,            // Host header
        val userAgent: String? = null,
        val contentType: String? = null,
        val statusCode: Int? = null  // 来自响应（如果能捕获到）
    ) : AuditEvent

    @Serializable
    data class FtpEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        override val protocol: ProtocolType = ProtocolType.FTP,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val username: String?,
        val command: String,         // USER, PASS, RETR, STOR, CWD, LIST, QUIT...
        val argument: String?,       // 命令参数 (文件名/目录名)
        val responseCode: Int?,      // 服务端响应码 (230, 530, 226...)
        val currentDirectory: String? = null
    ) : AuditEvent

    @Serializable
    data class TelnetEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        override val protocol: ProtocolType = ProtocolType.TELNET,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val username: String?,
        val commandLine: String,     // 用户输入的完整命令行
        val direction: Direction     // 标注数据方向
    ) : AuditEvent

    @Serializable
    data class DnsEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,  // DNS 服务器地址
        override val srcPort: Int,
        override val dstPort: Int,
        override val protocol: ProtocolType = ProtocolType.DNS,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val transactionId: Int,
        val queryDomain: String,
        val queryType: String,       // A, AAAA, CNAME, MX...
        val isResponse: Boolean,
        val resolvedIps: List<String> = emptyList(),  // 仅响应时有值
        val responseTtl: Int? = null
    ) : AuditEvent

    @Serializable
    data class SmtpEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        override val protocol: ProtocolType = ProtocolType.SMTP,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val from: String?,                        // MAIL FROM / From header
        val to: List<String> = emptyList(),       // RCPT TO / To header
        val subject: String? = null,
        val attachmentNames: List<String> = emptyList(),
        val attachmentSizes: List<Int> = emptyList(), // bytes (最大 2GB，Int 足够)
        val stage: String? = null                 // EHLO/MAIL/RCPT/DATA/QUIT
    ) : AuditEvent

    @Serializable
    data class Pop3Event(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        override val protocol: ProtocolType = ProtocolType.POP3,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val username: String?,
        val command: String,                      // USER, PASS, RETR, LIST, QUIT...
        val from: String? = null,                 // 邮件 From header
        val to: List<String> = emptyList(),       // 邮件 To header
        val subject: String? = null,
        val attachmentNames: List<String> = emptyList(),
        val attachmentSizes: List<Int> = emptyList(),
        val mailSize: Int? = null
    ) : AuditEvent
}
