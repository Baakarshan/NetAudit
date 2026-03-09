package com.netaudit.api

import com.netaudit.storage.AlertRepository
import com.netaudit.storage.AuditRepository
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class DashboardStats(
    val totalEvents: Long,
    val protocolCounts: Map<String, Long>,
    val alertCounts: Map<String, Long>
)

fun Route.statsRoutes(
    auditRepository: AuditRepository,
    alertRepository: AlertRepository
) {
    route("/api/stats") {
        get("/dashboard") {
            val totalEvents = auditRepository.countAll()
            val protocolCounts = auditRepository.countByProtocol().mapKeys { it.key.name }
            val alertCounts = alertRepository.countByLevel().mapKeys { it.key.name }
            call.respond(DashboardStats(totalEvents, protocolCounts, alertCounts))
        }

        get("/protocols") {
            val counts = auditRepository.countByProtocol().mapKeys { it.key.name }
            call.respond(counts)
        }
    }
}
