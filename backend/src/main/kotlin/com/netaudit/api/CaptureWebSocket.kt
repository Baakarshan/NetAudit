package com.netaudit.api

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import com.netaudit.model.AuditEvent
import com.netaudit.model.AppJson
import com.netaudit.event.AuditEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.ReceiveChannel

private val logger = KotlinLogging.logger {}

/**
 * 配置 WebSocket 路由。
 *
 * @param eventBus 审计事件总线
 */
fun Route.captureWebSocket(eventBus: AuditEventBus) {
    captureWebSocket(eventBus.auditEvents)
}

/**
 * 内部可测试封装：允许注入事件流与编码器。
 *
 * @param auditEvents 审计事件流
 * @param encode 事件编码函数
 */
internal fun Route.captureWebSocket(
    auditEvents: Flow<AuditEvent>,
    encode: (AuditEvent) -> String = { AppJson.encodeToString(it) }
) {
    webSocket("/ws/capture") {
        logger.info { "WebSocket client connected: ${call.request.local.remoteHost}" }
        handleCaptureWebSocketSession(
            auditEvents = auditEvents,
            incoming = incoming,
            sendFrame = { frame -> send(frame) },
            encode = encode,
            scope = this
        )
    }
}

/**
 * 处理单个 WebSocket 会话。
 *
 * - 后台协程持续推送事件流。
 * - 前台循环处理入站心跳消息。
 *
 * @param auditEvents 审计事件流
 * @param incoming 客户端入站帧通道
 * @param sendFrame 发送帧回调
 * @param encode 事件编码函数
 * @param scope 会话协程作用域
 */
internal suspend fun handleCaptureWebSocketSession(
    auditEvents: Flow<AuditEvent>,
    incoming: ReceiveChannel<Frame>,
    sendFrame: suspend (Frame) -> Unit,
    encode: (AuditEvent) -> String,
    scope: CoroutineScope
) {
    val job = scope.launch {
        auditEvents
            .catch { e: Throwable ->
                logger.error(e) { "Error in event stream" }
            }
            .collect { event: AuditEvent ->
                try {
                    val json = encode(event)
                    sendFrame(Frame.Text(json))
                } catch (e: Exception) {
                    logger.error(e) { "Failed to send event to WebSocket client" }
                }
            }
    }

    try {
        while (true) {
            when (val frame = incoming.receive()) {
                is Frame.Text -> {
                    val text = frame.readText()
                    if (text == "ping") {
                        sendFrame(Frame.Text("pong"))
                    }
                }
                else -> {}
            }
        }
    } catch (e: ClosedReceiveChannelException) {
        logger.info { "WebSocket client disconnected normally" }
    } catch (e: Exception) {
        logger.error(e) { "WebSocket error: ${e.message}" }
    } finally {
        job.cancel()
        logger.info { "WebSocket connection closed" }
    }
}
