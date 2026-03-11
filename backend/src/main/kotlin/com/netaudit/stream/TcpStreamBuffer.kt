package com.netaudit.stream

import com.netaudit.model.StreamKey
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * 单个 TCP 连接的数据缓冲区。
 *
 * 用途：
 * - 为 TCP 会话提供双向缓冲（客户端/服务端）。
 * - 维护协议解析所需的跨包状态。
 *
 * 线程安全：
 * - 本类不保证并发安全，默认由 `TcpStreamTracker` 单协程串行调用。
 */
class TcpStreamBuffer(
    val key: StreamKey,
    private val nowProvider: () -> Instant = Clock.System::now,
    val createdAt: Instant = nowProvider()
) {
    private val clientToServerBuf = StringBuilder()
    private val serverToClientBuf = StringBuilder()
    private var lastActivityAt: Instant = createdAt

    /**
     * 会话级状态（跨多个包的 FTP/SMTP/TELNET 状态机数据）。
     *
     * 约定：解析器自行定义 key，且应避免冲突。
     */
    val sessionState: MutableMap<String, Any> = mutableMapOf()

    /**
     * 追加来自客户端方向的 payload。
     *
     * 这里使用 UTF-8 解码，适用于文本协议；二进制协议不会走该分支。
     */
    fun appendClientData(data: ByteArray) {
        clientToServerBuf.append(String(data, Charsets.UTF_8))
        lastActivityAt = nowProvider()
    }

    /**
     * 追加来自服务端方向的 payload。
     */
    fun appendServerData(data: ByteArray) {
        serverToClientBuf.append(String(data, Charsets.UTF_8))
        lastActivityAt = nowProvider()
    }

    /** 获取客户端→服务端方向的缓冲内容（不清除）。 */
    fun clientData(): String = clientToServerBuf.toString()

    /** 获取服务端→客户端方向的缓冲内容（不清除）。 */
    fun serverData(): String = serverToClientBuf.toString()

    /**
     * 消费客户端缓冲（取出并清除）。
     *
     * 适用于解析器按行或分隔符读取完整消息的场景。
     */
    fun consumeClientData(): String {
        val data = clientToServerBuf.toString()
        clientToServerBuf.clear()
        return data
    }

    /**
     * 消费服务端缓冲（取出并清除）。
     */
    fun consumeServerData(): String {
        val data = serverToClientBuf.toString()
        serverToClientBuf.clear()
        return data
    }

    /**
     * 判断是否超时（用于定期清理）。
     *
     * timeoutSeconds 采用相对超时，避免依赖外部时钟精度。
     */
    fun isExpired(timeoutSeconds: Long = 60): Boolean {
        return (nowProvider() - lastActivityAt).inWholeSeconds > timeoutSeconds
    }
}
