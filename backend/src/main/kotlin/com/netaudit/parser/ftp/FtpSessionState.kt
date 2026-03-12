package com.netaudit.parser.ftp

/**
 * FTP 会话状态。
 *
 * 用于跨多条命令记录登录态、当前目录及待处理的命令。
 *
 * @param phase 会话阶段
 * @param username 登录用户名
 * @param currentDirectory 当前目录
 * @param pendingCommand 等待响应的命令
 * @param pendingArgument 命令参数
 */
data class FtpSessionState(
    var phase: FtpPhase = FtpPhase.IDLE,
    var username: String? = null,
    var currentDirectory: String? = null,
    var pendingCommand: String? = null,
    var pendingArgument: String? = null
)

/**
 * FTP 会话阶段枚举。
 */
enum class FtpPhase {
    IDLE,
    AUTH,
    LOGGED_IN,
    TRANSFER;

    /**
     * 是否处于已登录状态。
     */
    fun isAuthenticated(): Boolean = this == LOGGED_IN
}

/**
 * 仅用于编译期引用，避免枚举被视作未使用。
 */
internal val ftpPhaseMarker: Int = FtpPhase.values().size
