package com.netaudit.stream

import com.netaudit.model.StreamKey
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * 单个 TCP 连接的数据缓冲区。
 * Builder 模式：逐段追加 payload，直到协议分隔符出现后输出完整消息。
 */
class TcpStreamBuffer(
    val key: StreamKey,
    val createdAt: Instant = Clock.System.now()
) {
    private val clientToServerBuf = StringBuilder()
    private val serverToClientBuf = StringBuilder()
    private var lastActivityAt: Instant = createdAt

    // 会话级状态（跨多个包的 FTP/SMTP/TELNET 状态机数据）
    val sessionState: MutableMap<String, Any> = mutableMapOf()

    /** 追加来自客户端方向的 payload */
    fun appendClientData(data: ByteArray) {
        clientToServerBuf.append(String(data, Charsets.UTF_8))
        lastActivityAt = Clock.System.now()
    }

    /** 追加来自服务端方向的 payload */
    fun appendServerData(data: ByteArray) {
        serverToClientBuf.append(String(data, Charsets.UTF_8))
        lastActivityAt = Clock.System.now()
    }

    /** 获取客户端→服务端方向的缓冲内容（不清除） */
    fun clientData(): String = clientToServerBuf.toString()

    /** 获取服务端→客户端方向的缓冲内容（不清除） */
    fun serverData(): String = serverToClientBuf.toString()

    /** 消费客户端缓冲（取出并清除） */
    fun consumeClientData(): String {
        val data = clientToServerBuf.toString()
        clientToServerBuf.clear()
        return data
    }

    /** 消费服务端缓冲（取出并清除） */
    fun consumeServerData(): String {
        val data = serverToClientBuf.toString()
        serverToClientBuf.clear()
        return data
    }

    /** 判断是否超时（用于定期清理） */
    fun isExpired(timeoutSeconds: Long = 60): Boolean {
        return (Clock.System.now() - lastActivityAt).inWholeSeconds > timeoutSeconds
    }
}
