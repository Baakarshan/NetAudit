package com.netaudit.parser.email

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

enum class SmtpPhase {
    CONNECTED, GREETED, FROM_SET, RCPT_SET, DATA_MODE, COMPLETED
}
