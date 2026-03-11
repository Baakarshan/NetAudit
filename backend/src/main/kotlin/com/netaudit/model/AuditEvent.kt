package com.netaudit.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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

    /**
     * HTTP 协议审计事件。
     *
     * @param method HTTP 方法（GET/POST 等）
     * @param url 完整 URL（host + path）
     * @param host 请求的 Host 头
     * @param userAgent 客户端 User-Agent（可能为空）
     * @param contentType 请求或响应的 Content-Type（可能为空）
     * @param statusCode 响应状态码（仅捕获到响应时有值）
     */
    @Serializable
    @SerialName("HTTP")
    data class HttpEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        @Transient override val protocol: ProtocolType = ProtocolType.HTTP,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val method: String,          // GET, POST, etc.
        val url: String,             // 完整 URL (host + path)
        val host: String,            // Host header
        val userAgent: String? = null,
        val contentType: String? = null,
        val statusCode: Int? = null  // 来自响应（如果能捕获到）
    ) : AuditEvent

    /**
     * FTP 协议审计事件。
     *
     * @param username 登录用户名（未认证前可能为空）
     * @param command 命令名称（如 USER/PASS/RETR）
     * @param argument 命令参数（文件/目录等）
     * @param responseCode 服务端响应码（可能为空）
     * @param currentDirectory 解析出的当前目录（若可用）
     */
    @Serializable
    @SerialName("FTP")
    data class FtpEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        @Transient override val protocol: ProtocolType = ProtocolType.FTP,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val username: String?,
        val command: String,         // USER, PASS, RETR, STOR, CWD, LIST, QUIT...
        val argument: String?,       // 命令参数 (文件名/目录名)
        val responseCode: Int?,      // 服务端响应码 (230, 530, 226...)
        val currentDirectory: String? = null
    ) : AuditEvent

    /**
     * TELNET 协议审计事件。
     *
     * @param username 解析到的用户名（可能为空）
     * @param commandLine 用户输入的完整命令行
     * @param direction 数据方向（客户端到服务端/反向）
     */
    @Serializable
    @SerialName("TELNET")
    data class TelnetEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        @Transient override val protocol: ProtocolType = ProtocolType.TELNET,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val username: String?,
        val commandLine: String,     // 用户输入的完整命令行
        val direction: Direction     // 标注数据方向
    ) : AuditEvent

    /**
     * DNS 协议审计事件。
     *
     * @param transactionId DNS 事务 ID
     * @param queryDomain 查询域名
     * @param queryType 查询类型（A/AAAA/CNAME 等）
     * @param isResponse 是否为响应报文
     * @param resolvedIps 解析出的 IP 列表（仅响应时）
     * @param responseTtl 响应 TTL（可能为空）
     */
    @Serializable
    @SerialName("DNS")
    data class DnsEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,  // DNS 服务器地址
        override val srcPort: Int,
        override val dstPort: Int,
        @Transient override val protocol: ProtocolType = ProtocolType.DNS,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val transactionId: Int,
        val queryDomain: String,
        val queryType: String,       // A, AAAA, CNAME, MX...
        val isResponse: Boolean,
        val resolvedIps: List<String> = emptyList(),  // 仅响应时有值
        val responseTtl: Int? = null
    ) : AuditEvent

    /**
     * SMTP 协议审计事件。
     *
     * @param from 发件人地址（可能来自 MAIL FROM 或邮件头）
     * @param to 收件人地址列表（可能为空）
     * @param subject 邮件主题
     * @param attachmentNames 附件名称列表
     * @param attachmentSizes 附件大小列表（字节）
     * @param stage 当前会话阶段（EHLO/MAIL/RCPT/DATA/QUIT）
     */
    @Serializable
    @SerialName("SMTP")
    data class SmtpEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        @Transient override val protocol: ProtocolType = ProtocolType.SMTP,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val from: String?,                        // MAIL FROM / From header
        val to: List<String> = emptyList(),       // RCPT TO / To header
        val subject: String? = null,
        val attachmentNames: List<String> = emptyList(),
        val attachmentSizes: List<Int> = emptyList(), // bytes (最大 2GB，Int 足够)
        val stage: String? = null                 // EHLO/MAIL/RCPT/DATA/QUIT
    ) : AuditEvent

    /**
     * POP3 协议审计事件。
     *
     * @param username 登录用户名（可能为空）
     * @param command 客户端命令（USER/PASS/RETR 等）
     * @param from 邮件发件人
     * @param to 邮件收件人列表
     * @param subject 邮件主题
     * @param attachmentNames 附件名称列表
     * @param attachmentSizes 附件大小列表（字节）
     * @param mailSize 邮件大小（若可用）
     */
    @Serializable
    @SerialName("POP3")
    data class Pop3Event(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        @Transient override val protocol: ProtocolType = ProtocolType.POP3,
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

    /**
     * TLS 协议审计事件。
     *
     * @param serverName SNI 服务器名称
     * @param alpn 协商协议列表
     * @param clientVersion ClientHello 版本
     * @param supportedVersions 支持版本扩展
     */
    @Serializable
    @SerialName("TLS")
    data class TlsEvent(
        override val id: String,
        override val timestamp: Instant,
        override val srcIp: String,
        override val dstIp: String,
        override val srcPort: Int,
        override val dstPort: Int,
        @Transient override val protocol: ProtocolType = ProtocolType.TLS,
        override val alertLevel: AlertLevel = AlertLevel.INFO,
        val serverName: String? = null,           // SNI
        val alpn: List<String> = emptyList(),     // ALPN 协商协议
        val clientVersion: String? = null,        // ClientHello 版本
        val supportedVersions: List<String> = emptyList() // Supported Versions 扩展
    ) : AuditEvent
}
