package com.netaudit.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import com.netaudit.storage.DatabaseFactory
import com.netaudit.storage.tables.AuditLogsTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.count

@Serializable
data class SystemStats(
    val totalPackets: Long,
    val protocolStats: Map<String, Long>,
    val status: String = "running"
)

/**
 * 配置统计 API 路由
 */
fun Route.statsRoutes() {
    route("/api") {
        get("/stats") {
            val stats = DatabaseFactory.dbQuery {
                val total = AuditLogsTable.selectAll().count()

                val protocolCounts = AuditLogsTable
                    .select(AuditLogsTable.protocol, AuditLogsTable.id.count())
                    .groupBy(AuditLogsTable.protocol)
                    .associate {
                        it[AuditLogsTable.protocol] to it[AuditLogsTable.id.count()]
                    }

                SystemStats(
                    totalPackets = total,
                    protocolStats = protocolCounts
                )
            }

            call.respond(stats)
        }
    }
}
