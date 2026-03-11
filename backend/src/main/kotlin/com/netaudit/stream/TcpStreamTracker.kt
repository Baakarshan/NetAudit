package com.netaudit.stream

import com.netaudit.model.*
import com.netaudit.parser.ParserRegistry
import com.netaudit.parser.ProtocolParser
import com.netaudit.event.AuditEventBus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.coroutineContext

private val logger = KotlinLogging.logger {}

/**
 * TCP 流重组追踪器。
 *
 * 设计要点：
 * - 使用 canonical `StreamKey` 合并双向流，避免同一连接重复建模。
 * - 方向判断以 canonicalKey 作为“客户端→服务端”基准，保证方向一致性。
 * - 解析异常只记录不抛出，确保主链路稳定。
 *
 * 线程安全：
 * - 由单协程串行调用更安全，避免共享状态并发写入。
 */
class TcpStreamTracker(
    private val registry: ParserRegistry,
    private val eventBus: AuditEventBus,
    private val scope: CoroutineScope,
    private val streamTimeoutSeconds: Long = 60,
    private val cleanupIntervalMs: Long = 30_000,
    private val nowProvider: () -> Instant = Clock.System::now
) : StreamTracker {
    private val streams = mutableMapOf<StreamKey, TcpStreamBuffer>()

    /**
     * 处理一个已解码的 TCP 包。
     * 调用时机: PacketDecoder 解码成功后、且为 TCP 包时。
     */
    override suspend fun handleTcpPacket(metadata: PacketMetadata) {
        val parser = registry.findByEitherPort(metadata.srcPort, metadata.dstPort)
            ?: return  // 不关心的端口

        val key = StreamKey(
            metadata.srcIp, metadata.srcPort,
            metadata.dstIp, metadata.dstPort
        )
        val canonicalKey = key.canonical()

        // 查找或创建 Buffer
        val buffer = streams.getOrPut(canonicalKey) {
            logger.debug { "New TCP stream: $canonicalKey" }
            TcpStreamBuffer(canonicalKey, nowProvider)
        }

        // 判断方向：canonicalKey 作为“客户端 → 服务端”基准
        val direction = if (key == canonicalKey)
            Direction.CLIENT_TO_SERVER else Direction.SERVER_TO_CLIENT

        // 处理 FIN/RST → 清理
        val flags = metadata.tcpFlags
        if (flags != null && (flags.fin || flags.rst)) {
            // 先处理残余 payload（如果有）
            if (metadata.payload.isNotEmpty()) {
                processPayload(buffer, metadata, direction, parser)
            }
            streams.remove(canonicalKey)
            logger.debug { "TCP stream closed: $canonicalKey (${if (flags.fin) "FIN" else "RST"})" }
            return
        }

        // 追加 payload
        if (metadata.payload.isNotEmpty()) {
            processPayload(buffer, metadata, direction, parser)
        }
    }

    /**
     * 处理 UDP 包。
     * UDP 无需流重组，直接构造 StreamContext 调用 Parser。
     */
    override suspend fun handleUdpPacket(metadata: PacketMetadata) {
        val parser = registry.findByEitherPort(metadata.srcPort, metadata.dstPort)
            ?: return  // 不关心的端口

        // 以目的端口是否为已注册服务端端口来判断方向
        val direction = if (registry.findByPort(metadata.dstPort) != null)
            Direction.CLIENT_TO_SERVER else Direction.SERVER_TO_CLIENT

        val key = StreamKey(metadata.srcIp, metadata.srcPort, metadata.dstIp, metadata.dstPort)
        val context = StreamContext(
            key = key,
            metadata = metadata,
            payload = metadata.payload,
            direction = direction
        )

        try {
            val event = parser.parse(context)
            if (event != null) {
                eventBus.emitAudit(event)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Parser error for ${parser.protocolType}: ${e.message}" }
        }
    }

    private suspend fun processPayload(
        buffer: TcpStreamBuffer,
        metadata: PacketMetadata,
        direction: Direction,
        parser: ProtocolParser
    ) {
        // 追加到 buffer
        when (direction) {
            Direction.CLIENT_TO_SERVER -> buffer.appendClientData(metadata.payload)
            Direction.SERVER_TO_CLIENT -> buffer.appendServerData(metadata.payload)
        }

        // sessionState 由 TcpStreamBuffer 持有，用于协议解析的会话态
        val context = StreamContext(
            key = buffer.key,
            metadata = metadata,
            payload = metadata.payload,
            direction = direction,
            sessionState = buffer.sessionState
        )

        // 调用 Parser
        try {
            val event = parser.parse(context)
            if (event != null) {
                eventBus.emitAudit(event)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Parser error for ${parser.protocolType}: ${e.message}" }
        }
    }

    /**
     * 启动定时清理协程（每 30 秒清理超时连接）。
     */
    override fun startCleanupJob(): Job {
        return scope.launch {
            while (true) {
                // 先检查取消，保证 stop 时能及时退出
                coroutineContext.ensureActive()
                delay(cleanupIntervalMs)
                val before = streams.size
                streams.entries.removeAll { it.value.isExpired(streamTimeoutSeconds) }
                val removed = before - streams.size
                if (removed > 0) {
                    logger.info { "Cleaned up $removed expired TCP streams. Active: ${streams.size}" }
                }
            }
        }
    }

    /** 获取当前活跃流数量（监控用） */
    override fun activeStreamCount(): Int = streams.size
}
