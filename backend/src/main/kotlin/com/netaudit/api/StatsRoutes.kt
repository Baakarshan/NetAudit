package com.netaudit.api

import com.netaudit.storage.AlertRepository
import com.netaudit.storage.AuditRepository
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/**
 * Dashboard 统计返回结构。
 *
 * @param totalEvents 总事件数
 * @param protocolCounts 协议维度统计（key 为协议名称）
 * @param alertCounts 告警等级统计（key 为等级名称）
 */
@Serializable
data class DashboardStats(
    val totalEvents: Long,
    val protocolCounts: Map<String, Long>,
    val alertCounts: Map<String, Long>
)

/**
 * 统计相关路由。
 *
 * @param auditRepository 审计事件仓储
 * @param alertRepository 告警仓储
 */
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
