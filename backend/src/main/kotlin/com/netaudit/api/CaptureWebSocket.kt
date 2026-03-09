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
 * 配置 WebSocket 路由
 */
fun Route.captureWebSocket(eventBus: AuditEventBus) {
    captureWebSocket(eventBus.auditEvents)
}

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
