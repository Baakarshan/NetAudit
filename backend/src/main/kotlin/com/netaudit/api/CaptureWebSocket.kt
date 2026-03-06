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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * 配置 WebSocket 路由
 */
fun Route.captureWebSocket(eventBus: AuditEventBus) {
    webSocket("/ws/capture") {
        logger.info { "WebSocket client connected: ${call.request.local.remoteHost}" }

        try {
            // 订阅事件总线
            val job = launch {
                eventBus.auditEvents
                    .catch { e: Throwable ->
                        logger.error(e) { "Error in event stream" }
                    }
                    .collect { event: AuditEvent ->
                        try {
                            val json = AppJson.encodeToString(event)
                            send(Frame.Text(json))
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to send event to WebSocket client" }
                        }
                    }
            }

            // 处理客户端消息（心跳等）
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        if (text == "ping") {
                            send(Frame.Text("pong"))
                        }
                    }
                    else -> {}
                }
            }

            job.cancel()
        } catch (e: ClosedReceiveChannelException) {
            logger.info { "WebSocket client disconnected normally" }
        } catch (e: Exception) {
            logger.error(e) { "WebSocket error: ${e.message}" }
        } finally {
            logger.info { "WebSocket connection closed" }
        }
    }
}
