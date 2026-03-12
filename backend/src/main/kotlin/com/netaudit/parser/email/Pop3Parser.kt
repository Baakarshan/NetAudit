package com.netaudit.parser.email

import com.netaudit.model.AuditEvent
import com.netaudit.model.Direction
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.parser.ProtocolParser
import java.util.UUID

/**
 * POP3 协议解析器。
 *
 * 通过会话状态跟踪 RETR 模式，解析邮件内容并产出审计事件。
 */
class Pop3Parser : ProtocolParser {
    override val protocolType = ProtocolType.POP3
    override val ports = setOf(110)

    companion object {
        private const val SESSION_KEY = "pop3.session"
    }

    override fun parse(context: StreamContext): AuditEvent? {
        val text = context.payloadAsText()
        if (text.isBlank()) return null

        val session = context.sessionState.getOrPut(SESSION_KEY) {
            Pop3SessionState()
        } as Pop3SessionState

        return when (context.direction) {
            Direction.CLIENT_TO_SERVER -> handleCommand(context, session, text)
            Direction.SERVER_TO_CLIENT -> handleResponse(context, session, text)
        }
    }

    /**
     * 处理客户端命令并生成基础事件。
     *
     * @param context 解析上下文
     * @param session POP3 会话状态
     * @param text 客户端命令文本
     */
    private fun handleCommand(
        context: StreamContext,
        session: Pop3SessionState,
        text: String
    ): AuditEvent.Pop3Event? {
        val lines = text.split("\r\n", "\n").filter { it.isNotBlank() }

        for (line in lines) {
            val parts = line.split(" ", limit = 2)
            val command = parts[0].uppercase()
            val argument = parts.getOrNull(1)?.trim()

            when (command) {
                "USER" -> {
                    session.username = argument
                    return AuditEvent.Pop3Event(
                        id = generateId(),
                        timestamp = context.timestamp,
                        srcIp = context.srcIp,
                        dstIp = context.dstIp,
                        srcPort = context.srcPort,
                        dstPort = context.dstPort,
                        username = argument,
                        command = "USER"
                    )
                }
                "RETR" -> {
                    session.inRetrMode = true
                    session.retrBuffer.clear()
                    return AuditEvent.Pop3Event(
                        id = generateId(),
                        timestamp = context.timestamp,
                        srcIp = context.srcIp,
                        dstIp = context.dstIp,
                        srcPort = context.srcPort,
                        dstPort = context.dstPort,
                        username = session.username,
                        command = "RETR"
                    )
                }
                "QUIT" -> {
                    return AuditEvent.Pop3Event(
                        id = generateId(),
                        timestamp = context.timestamp,
                        srcIp = context.srcIp,
                        dstIp = context.dstIp,
                        srcPort = context.srcPort,
                        dstPort = context.dstPort,
                        username = session.username,
                        command = "QUIT"
                    )
                }
                "LIST", "STAT", "DELE" -> {
                    return AuditEvent.Pop3Event(
                        id = generateId(),
                        timestamp = context.timestamp,
                        srcIp = context.srcIp,
                        dstIp = context.dstIp,
                        srcPort = context.srcPort,
                        dstPort = context.dstPort,
                        username = session.username,
                        command = command
                    )
                }
            }
        }

        return null
    }

    /**
     * 处理服务端响应，补齐 RETR 内容解析。
     *
     * @param context 解析上下文
     * @param session POP3 会话状态
     * @param text 服务端响应文本
     */
    private fun handleResponse(
        context: StreamContext,
        session: Pop3SessionState,
        text: String
    ): AuditEvent.Pop3Event? {
        if (!session.inRetrMode) return null

        session.retrBuffer.append(text)

        val bufStr = session.retrBuffer.toString()
        val endMarkers = listOf("\r\n.\r\n", "\n.\n")
        if (!endMarkers.any { bufStr.contains(it) }) return null

        session.inRetrMode = false
        val mailContent = bufStr.substringAfter("+OK")
            .substringBefore("\r\n.\r\n")
            .substringBefore("\n.\n")
            .trimStart()

        val headers = EmailHeaderParser.parseHeaders(mailContent)

        val bodyStart = mailContent.indexOf("\r\n\r\n").let {
            if (it >= 0) it + 4 else mailContent.indexOf("\n\n").let { i -> if (i >= 0) i + 2 else 0 }
        }
        val body = mailContent.substring(bodyStart)
        val attachments = EmailHeaderParser.extractAttachments(body, headers.boundary)

        val event = AuditEvent.Pop3Event(
            id = generateId(),
            timestamp = context.timestamp,
            srcIp = context.dstIp,
            dstIp = context.srcIp,
            srcPort = context.dstPort,
            dstPort = context.srcPort,
            username = session.username,
            command = "RETR",
            from = headers.from,
            to = headers.to,
            subject = headers.subject,
            attachmentNames = attachments.map { it.filename },
            attachmentSizes = attachments.map { it.estimatedSize },
            mailSize = mailContent.length
        )

        session.retrBuffer.clear()
        return event
    }

    private fun generateId(): String = UUID.randomUUID().toString()
}
