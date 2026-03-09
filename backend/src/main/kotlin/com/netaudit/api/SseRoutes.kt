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

fun Route.sseRoutes(
    auditEvents: Flow<AuditEvent>,
    alertEvents: Flow<AlertRecord>
) {
    get("/api/sse/events") {
        call.response.cacheControl(CacheControl.NoCache(null))
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            logger.info { "SSE client connected: ${call.request.local.remoteAddress}" }

            // 发送 SSE 注释行，确保连接建立并立即刷新
            write(sseComment("connected"))
            flush()

            try {
                kotlinx.coroutines.supervisorScope {
                    launch {
                        try {
                            auditEvents.collect { event ->
                                val json = AppJson.encodeToString<AuditEvent>(event)
                                write(sseEvent("audit", json))
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
                                val json = AppJson.encodeToString(alert)
                                write(sseEvent("alert", json))
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
    }
}
