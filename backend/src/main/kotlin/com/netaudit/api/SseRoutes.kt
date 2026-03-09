package com.netaudit.api

import com.netaudit.event.AuditEventBus
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
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

private val logger = KotlinLogging.logger {}

fun Route.sseRoutes(eventBus: AuditEventBus) {
    get("/api/sse/events") {
        call.response.cacheControl(CacheControl.NoCache(null))
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            logger.info { "SSE client connected: ${call.request.local.remoteAddress}" }

            try {
                kotlinx.coroutines.supervisorScope {
                    launch {
                        try {
                            eventBus.auditEvents.collect { event ->
                                val json = AppJson.encodeToString<AuditEvent>(event)
                                write("event: audit\n")
                                write("data: $json\n\n")
                                flush()
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "Audit events stream error: ${e.message}" }
                        }
                    }
                    launch {
                        try {
                            eventBus.alertEvents.collect { alert ->
                                val json = AppJson.encodeToString(alert)
                                write("event: alert\n")
                                write("data: $json\n\n")
                                flush()
                            }
                        } catch (e: Exception) {
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
