package com.netaudit.api

import com.netaudit.model.AlertRecord
import com.netaudit.model.AppJson
import com.netaudit.model.AuditEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

private val logger = KotlinLogging.logger {}

/**
 * SSE 路由。
 *
 * 以 text/event-stream 形式推送审计与告警事件。
 */
fun Route.sseRoutes(
    auditEvents: Flow<AuditEvent>,
    alertEvents: Flow<AlertRecord>,
    auditEncoder: (AuditEvent) -> String = { AppJson.encodeToString(it) },
    alertEncoder: (AlertRecord) -> String = { AppJson.encodeToString(it) }
) {
    get("/api/sse/events") {
        call.response.cacheControl(CacheControl.NoCache(null))
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            logger.info { "SSE client connected: ${call.request.local.remoteAddress}" }
            handleSseSession(
                auditEvents = auditEvents,
                alertEvents = alertEvents,
                auditEncoder = auditEncoder,
                alertEncoder = alertEncoder,
                writeLine = { line -> write(line) },
                flush = { flush() }
            )
        }
    }
}

/**
 * 处理 SSE 会话。
 *
 * 将审计与告警流并行推送给前端，并在连接建立时发送注释行。
 */
internal suspend fun handleSseSession(
    auditEvents: Flow<AuditEvent>,
    alertEvents: Flow<AlertRecord>,
    auditEncoder: (AuditEvent) -> String,
    alertEncoder: (AlertRecord) -> String,
    writeLine: (String) -> Unit,
    flush: () -> Unit
) {
    try {
        // 发送 SSE 注释行，确保连接建立并立即刷新
        writeLine(sseComment("connected"))
        flush()

        kotlinx.coroutines.supervisorScope {
            launch {
                try {
                    auditEvents.collect { event ->
                        val json = auditEncoder(event)
                        writeLine(sseEvent("audit", json))
                        flush()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.warn(e) { "Audit events stream error: ${e.message}" }
                }
            }
            launch {
                try {
                    alertEvents.collect { alert ->
                        val json = alertEncoder(alert)
                        writeLine(sseEvent("alert", json))
                        flush()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.warn(e) { "Alert events stream error: ${e.message}" }
                }
            }
        }
    } catch (e: Exception) {
        logger.info { "SSE client disconnected: ${e.message}" }
    }
}
