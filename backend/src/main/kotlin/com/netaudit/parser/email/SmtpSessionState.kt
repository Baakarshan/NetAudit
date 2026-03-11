package com.netaudit.parser.email

/**
 * SMTP 会话状态。
 *
 * 用于跨多条 SMTP 命令维护邮件收发的上下文信息。
 */
data class SmtpSessionState(
    var phase: SmtpPhase = SmtpPhase.CONNECTED,
    var from: String? = null,
    var to: MutableList<String> = mutableListOf(),
    var subject: String? = null,
    var dataBuffer: StringBuilder = StringBuilder(),
    var inDataMode: Boolean = false,
    var attachmentNames: MutableList<String> = mutableListOf(),
    var attachmentSizes: MutableList<Int> = mutableListOf()
)

/**
 * SMTP 会话阶段枚举。
 */
enum class SmtpPhase {
    CONNECTED, GREETED, FROM_SET, RCPT_SET, DATA_MODE, COMPLETED;

    /**
     * 是否进入终态。
     */
    fun isTerminal(): Boolean = this == COMPLETED
}

/**
 * 仅用于编译期引用，避免枚举被视作未使用。
 */
internal val smtpPhaseMarker: Int = SmtpPhase.values().size
