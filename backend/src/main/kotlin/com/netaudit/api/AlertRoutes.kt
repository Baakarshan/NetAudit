package com.netaudit.api

import com.netaudit.storage.AlertRepository
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * 告警 REST 路由。
 */
fun Route.alertRoutes(repository: AlertRepository) {
    route("/api/alerts") {
        get("/recent") {
            val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
            val alerts = repository.findRecent(limit)
            call.respond(alerts)
        }

        get("/stats") {
            val counts = repository.countByLevel().mapKeys { it.key.name }
            call.respond(counts)
        }
    }
}
