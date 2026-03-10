package com.netaudit.storage.impl

import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import com.netaudit.storage.AuditRepository
import com.netaudit.storage.DatabaseFactory
import com.netaudit.storage.tables.AuditLogsTable
import com.netaudit.storage.util.toJavaOffsetDateTime
import kotlinx.datetime.Instant
import kotlinx.coroutines.yield
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.batchInsert

/**
 * AuditRepository 的 Exposed 实现。
 */
class ExposedAuditRepository : AuditRepository {
    override suspend fun save(event: AuditEvent) {
        // 测试场景触发一次挂起，确保协程路径覆盖
        if (DatabaseFactory.forceSuspend) {
            yield()
        }
        DatabaseFactory.dbQuery {
            AuditLogsTable.insert { row ->
                mapEventToRow(row, event)
            }
            Unit
        }
    }

    override suspend fun saveBatch(events: List<AuditEvent>) {
        // 测试场景触发一次挂起，确保协程路径覆盖
        if (DatabaseFactory.forceSuspend) {
            yield()
        }
        DatabaseFactory.dbQuery {
            if (events.isEmpty()) return@dbQuery
            AuditLogsTable.batchInsert(events, shouldReturnGeneratedValues = false) { event ->
                mapEventToRow(this, event)
            }
        }
    }

    override suspend fun findAll(page: Int, size: Int): List<AuditEvent> = DatabaseFactory.dbQuery {
        AuditLogsTable.selectAll()
            .orderBy(AuditLogsTable.capturedAt, SortOrder.DESC)
            .limit(size, (page * size).toLong())
            .map { rowToEvent(it) }
    }

    override suspend fun findByProtocol(protocol: ProtocolType, page: Int, size: Int): List<AuditEvent> =
        DatabaseFactory.dbQuery {
            AuditLogsTable.selectAll()
                .where { AuditLogsTable.protocol eq protocol.name }
                .orderBy(AuditLogsTable.capturedAt, SortOrder.DESC)
                .limit(size, (page * size).toLong())
                .map { rowToEvent(it) }
        }

    override suspend fun findBySourceIp(srcIp: String, page: Int, size: Int): List<AuditEvent> =
        DatabaseFactory.dbQuery {
            AuditLogsTable.selectAll()
                .where { AuditLogsTable.srcIp eq srcIp }
                .orderBy(AuditLogsTable.capturedAt, SortOrder.DESC)
                .limit(size, (page * size).toLong())
                .map { rowToEvent(it) }
        }

    override suspend fun findBetween(start: Instant, end: Instant, page: Int, size: Int): List<AuditEvent> =
        DatabaseFactory.dbQuery {
            AuditLogsTable.selectAll()
                .where {
                    AuditLogsTable.capturedAt.between(
                        start.toJavaOffsetDateTime(),
                        end.toJavaOffsetDateTime()
                    )
                }
                .orderBy(AuditLogsTable.capturedAt, SortOrder.DESC)
                .limit(size, (page * size).toLong())
                .map { rowToEvent(it) }
        }

    override suspend fun findRecent(limit: Int): List<AuditEvent> = DatabaseFactory.dbQuery {
        AuditLogsTable.selectAll()
            .orderBy(AuditLogsTable.capturedAt, SortOrder.DESC)
            .limit(limit)
            .map { rowToEvent(it) }
    }

    override suspend fun findByEventId(eventId: String): AuditEvent? = DatabaseFactory.dbQuery {
        AuditLogsTable.selectAll()
            .where { AuditLogsTable.eventId eq eventId }
            .limit(1)
            .map { rowToEvent(it) }
            .firstOrNull()
    }

    override suspend fun countAll(): Long = DatabaseFactory.dbQuery {
        AuditLogsTable.selectAll().count()
    }

    override suspend fun countByProtocol(): Map<ProtocolType, Long> = DatabaseFactory.dbQuery {
        AuditLogsTable
            .select(AuditLogsTable.protocol, AuditLogsTable.id.count())
            .groupBy(AuditLogsTable.protocol)
            .associate { row ->
                ProtocolType.valueOf(row[AuditLogsTable.protocol]) to row[AuditLogsTable.id.count()]
            }
    }

    private fun mapEventToRow(row: UpdateBuilder<*>, event: AuditEvent) {
        row[AuditLogsTable.eventId] = event.id
        row[AuditLogsTable.protocol] = event.protocol.name
        row[AuditLogsTable.srcIp] = event.srcIp
        row[AuditLogsTable.dstIp] = event.dstIp
        row[AuditLogsTable.srcPort] = event.srcPort
        row[AuditLogsTable.dstPort] = event.dstPort
        row[AuditLogsTable.alertLevel] = event.alertLevel.name
        row[AuditLogsTable.capturedAt] = event.timestamp.toJavaOffsetDateTime()
        row[AuditLogsTable.details] = event
    }

    private fun rowToEvent(row: ResultRow): AuditEvent {
        return row[AuditLogsTable.details]
    }
}
