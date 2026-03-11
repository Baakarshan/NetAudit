package com.netaudit.parser.email

/**
 * POP3 会话状态。
 *
 * 保存用户名及 RETR 模式下的邮件缓存。
 */
data class Pop3SessionState(
    var username: String? = null,
    var inRetrMode: Boolean = false,
    var retrBuffer: StringBuilder = StringBuilder()
)
