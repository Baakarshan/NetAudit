package com.netaudit.parser.email

import com.netaudit.model.AuditEvent
import com.netaudit.model.Direction
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.parser.ProtocolParser
import java.util.UUID

/**
 * SMTP 协议解析器。
 *
 * 通过会话状态跟踪邮件发送流程，并在 DATA 阶段产出审计事件。
 */
class SmtpParser : ProtocolParser {
    override val protocolType = ProtocolType.SMTP
    override val ports = setOf(25, 587)

    companion object {
        private const val SESSION_KEY = "smtp.session"
    }

    override fun parse(context: StreamContext): AuditEvent? {
        val text = context.payloadAsText()
        if (text.isBlank()) return null

        val session = context.sessionState.getOrPut(SESSION_KEY) {
            SmtpSessionState()
        } as SmtpSessionState

        return when (context.direction) {
            Direction.CLIENT_TO_SERVER -> handleClientData(context, session, text)
            Direction.SERVER_TO_CLIENT -> handleServerResponse(session, text)
        }
    }

    /**
     * 处理客户端命令与数据输入。
     *
     * @param context 解析上下文
     * @param session SMTP 会话状态
     * @param text 客户端命令文本
     */
    private fun handleClientData(
        context: StreamContext,
        session: SmtpSessionState,
        text: String
    ): AuditEvent? {
        if (session.inDataMode) {
            return handleDataMode(context, session, text)
        }

        val lines = text.split("\r\n", "\n").filter { it.isNotBlank() }
        for (line in lines) {
            val upper = line.uppercase()
            when {
                upper.startsWith("EHLO") || upper.startsWith("HELO") -> {
                    session.phase = SmtpPhase.GREETED
                }
                upper.startsWith("MAIL FROM:") -> {
                    session.from = extractAddress(line.substring(10))
                    session.to.clear()
                    session.phase = SmtpPhase.FROM_SET
                }
                upper.startsWith("RCPT TO:") -> {
                    session.to.add(extractAddress(line.substring(8)))
                    session.phase = SmtpPhase.RCPT_SET
                }
                upper.startsWith("DATA") -> {
                    // wait for 354 response
                }
                upper.startsWith("QUIT") -> {
                    session.phase = SmtpPhase.CONNECTED
                    session.from = null
                    session.to.clear()
                }
            }
        }

        return null
    }

    /**
     * 处理 DATA 模式内容，解析邮件头与附件信息。
     *
     * @param context 解析上下文
     * @param session SMTP 会话状态
     * @param text DATA 模式下的正文片段
     */
    private fun handleDataMode(
        context: StreamContext,
        session: SmtpSessionState,
        text: String
    ): AuditEvent? {
        session.dataBuffer.append(text)

        val bufStr = session.dataBuffer.toString()
        val endMarkerIndex = bufStr.indexOf("\r\n.\r\n")
        val endIndex = if (endMarkerIndex >= 0) endMarkerIndex else bufStr.indexOf("\n.\n")
        if (endIndex < 0) return null

        session.inDataMode = false
        session.phase = SmtpPhase.COMPLETED

        val mailContent = bufStr.substring(0, endIndex)
        val headers = EmailHeaderParser.parseHeaders(mailContent)

        val bodyStart = mailContent.indexOf("\r\n\r\n").let {
            if (it >= 0) it + 4 else mailContent.indexOf("\n\n").let { i -> if (i >= 0) i + 2 else 0 }
        }
        val body = mailContent.substring(bodyStart)
        val attachments = EmailHeaderParser.extractAttachments(body, headers.boundary)

        val event = AuditEvent.SmtpEvent(
            id = generateId(),
            timestamp = context.timestamp,
            srcIp = context.srcIp,
            dstIp = context.dstIp,
            srcPort = context.srcPort,
            dstPort = context.dstPort,
            from = headers.from ?: session.from,
            to = headers.to.ifEmpty { session.to.toList() },
            subject = headers.subject,
            attachmentNames = attachments.map { it.filename },
            attachmentSizes = attachments.map { it.estimatedSize },
            stage = "DATA"
        )

        session.dataBuffer.clear()
        return event
    }

    /**
     * 处理服务端响应，识别进入 DATA 模式的时机。
     *
     * @param session SMTP 会话状态
     * @param text 服务端响应文本
     */
    private fun handleServerResponse(session: SmtpSessionState, text: String): AuditEvent? {
        val lines = text.split("\r\n", "\n").filter { it.isNotBlank() }
        for (line in lines) {
            val code = line.take(3).toIntOrNull() ?: continue
            if (code == 354) {
                session.inDataMode = true
                session.dataBuffer.clear()
                session.phase = SmtpPhase.DATA_MODE
            }
        }
        return null
    }

    /**
     * 提取邮件地址，兼容带尖括号的格式。
     *
     * @param raw 原始地址字段
     * @return 清洗后的邮箱地址
     */
    private fun extractAddress(raw: String): String {
        val match = Regex("<(.+?)>").find(raw)
        return match?.groupValues?.get(1)?.trim() ?: raw.trim()
    }

    private fun generateId(): String = UUID.randomUUID().toString()
}
