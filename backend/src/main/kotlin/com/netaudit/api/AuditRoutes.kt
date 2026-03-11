package com.netaudit.api

import com.netaudit.model.ProtocolType
import com.netaudit.storage.AuditRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.Instant

/**
 * 审计日志 REST 路由。
 *
 * 输入参数使用查询字符串，默认分页大小 50，最大 200。
 */
fun Route.auditRoutes(repository: AuditRepository) {
    route("/api/audit") {
        get("/logs") {
            val page = call.parameters["page"]?.toIntOrNull() ?: 0
            val size = (call.parameters["size"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
            val protocol = call.parameters["protocol"]?.let {
                try {
                    ProtocolType.valueOf(it.uppercase())
                } catch (_: Exception) {
                    null
                }
            }
            val srcIp = call.parameters["srcIp"]
            val start = call.parameters["start"]?.let { Instant.parse(it) }
            val end = call.parameters["end"]?.let { Instant.parse(it) }

            val events = when {
                protocol != null -> repository.findByProtocol(protocol, page, size)
                srcIp != null -> repository.findBySourceIp(srcIp, page, size)
                start != null && end != null -> repository.findBetween(start, end, page, size)
                else -> repository.findAll(page, size)
            }

            call.respond(events)
        }

        get("/recent") {
            val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
            val events = repository.findRecent(limit)
            call.respond(events)
        }

        get("/{id}") {
            val eventId = call.parameters["id"] ?: ""
            if (eventId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing audit event id"))
                return@get
            }
            val event = repository.findByEventId(eventId)
            if (event == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Audit event not found"))
            } else {
                call.respond(event)
            }
        }
    }
}
