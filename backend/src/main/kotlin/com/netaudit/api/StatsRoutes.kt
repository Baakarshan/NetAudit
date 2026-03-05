package com.netaudit.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import com.netaudit.storage.DatabaseFactory
import com.netaudit.storage.PacketsTable
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
                val total = PacketsTable.selectAll().count()

                val protocolCounts = PacketsTable
                    .select(PacketsTable.protocol, PacketsTable.id.count())
                    .groupBy(PacketsTable.protocol)
                    .associate {
                        it[PacketsTable.protocol] to it[PacketsTable.id.count()]
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
