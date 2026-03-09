package com.netaudit.parser.ftp

data class FtpSessionState(
    var phase: FtpPhase = FtpPhase.IDLE,
    var username: String? = null,
    var currentDirectory: String? = null,
    var pendingCommand: String? = null,
    var pendingArgument: String? = null
)

enum class FtpPhase {
    IDLE,
    AUTH,
    LOGGED_IN,
    TRANSFER;

    fun isAuthenticated(): Boolean = this == LOGGED_IN
}

internal val ftpPhaseMarker: Int = FtpPhase.values().size
