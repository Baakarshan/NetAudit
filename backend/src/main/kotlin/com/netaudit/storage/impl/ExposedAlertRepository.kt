package com.netaudit.storage.impl

import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord
import com.netaudit.model.ProtocolType
import com.netaudit.storage.AlertRepository
import com.netaudit.storage.DatabaseFactory
import com.netaudit.storage.tables.AlertsTable
import com.netaudit.storage.util.toJavaOffsetDateTime
import com.netaudit.storage.util.toKotlinxInstant
import kotlinx.coroutines.yield
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll

class ExposedAlertRepository : AlertRepository {
    override suspend fun save(alert: AlertRecord) {
        // 测试用挂起开关，便于覆盖协程让出与取消路径
        if (DatabaseFactory.forceSuspend) {
            yield()
        }
        DatabaseFactory.dbQuery {
            AlertsTable.insert { row ->
                row[AlertsTable.alertId] = alert.id
                row[AlertsTable.timestamp] = alert.timestamp.toJavaOffsetDateTime()
                row[AlertsTable.level] = alert.level.name
                row[AlertsTable.ruleName] = alert.ruleName
                row[AlertsTable.message] = alert.message
                row[AlertsTable.auditEventId] = alert.auditEventId
                row[AlertsTable.protocol] = alert.protocol.name
            }
            Unit
        }
    }

    override suspend fun findRecent(limit: Int): List<AlertRecord> = DatabaseFactory.dbQuery {
        AlertsTable.selectAll()
            .orderBy(AlertsTable.timestamp, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                AlertRecord(
                    id = row[AlertsTable.alertId],
                    timestamp = row[AlertsTable.timestamp].toKotlinxInstant(),
                    level = AlertLevel.valueOf(row[AlertsTable.level]),
                    ruleName = row[AlertsTable.ruleName],
                    message = row[AlertsTable.message],
                    auditEventId = row[AlertsTable.auditEventId],
                    protocol = ProtocolType.valueOf(row[AlertsTable.protocol])
                )
            }
    }

    override suspend fun countByLevel(): Map<AlertLevel, Long> = DatabaseFactory.dbQuery {
        AlertsTable.select(AlertsTable.level, AlertsTable.id.count())
            .groupBy(AlertsTable.level)
            .associate { row ->
                AlertLevel.valueOf(row[AlertsTable.level]) to row[AlertsTable.id.count()]
            }
    }
}
