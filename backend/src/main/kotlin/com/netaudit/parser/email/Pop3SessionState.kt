package com.netaudit.parser.email

data class Pop3SessionState(
    var username: String? = null,
    var inRetrMode: Boolean = false,
    var retrBuffer: StringBuilder = StringBuilder()
)
