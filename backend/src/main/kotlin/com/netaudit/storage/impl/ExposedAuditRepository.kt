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
 *
 * 设计要点：
 * - 通用字段拆列存储，协议特有字段写入 JSONB（`details`）。
 * - 查询结果直接从 JSONB 反序列化为 `AuditEvent`。
 */
class ExposedAuditRepository : AuditRepository {
    /**
     * 保存单条事件到数据库。
     *
     * @param event 审计事件
     */
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

    /**
     * 批量保存事件。
     *
     * @param events 事件列表；为空时直接返回
     */
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

    /**
     * 查询全部事件（分页）。
     *
     * @param page 页码（从 0 开始）
     * @param size 每页数量
     */
    override suspend fun findAll(page: Int, size: Int): List<AuditEvent> = DatabaseFactory.dbQuery {
        AuditLogsTable.selectAll()
            .orderBy(AuditLogsTable.capturedAt, SortOrder.DESC)
            .limit(size, (page * size).toLong())
            .map { rowToEvent(it) }
    }

    /**
     * 按协议查询事件（分页）。
     *
     * @param protocol 协议类型
     * @param page 页码（从 0 开始）
     * @param size 每页数量
     */
    override suspend fun findByProtocol(protocol: ProtocolType, page: Int, size: Int): List<AuditEvent> =
        DatabaseFactory.dbQuery {
            AuditLogsTable.selectAll()
                .where { AuditLogsTable.protocol eq protocol.name }
                .orderBy(AuditLogsTable.capturedAt, SortOrder.DESC)
                .limit(size, (page * size).toLong())
                .map { rowToEvent(it) }
        }

    /**
     * 按源 IP 查询事件（分页）。
     *
     * @param srcIp 源 IP
     * @param page 页码（从 0 开始）
     * @param size 每页数量
     */
    override suspend fun findBySourceIp(srcIp: String, page: Int, size: Int): List<AuditEvent> =
        DatabaseFactory.dbQuery {
            AuditLogsTable.selectAll()
                .where { AuditLogsTable.srcIp eq srcIp }
                .orderBy(AuditLogsTable.capturedAt, SortOrder.DESC)
                .limit(size, (page * size).toLong())
                .map { rowToEvent(it) }
        }

    /**
     * 按时间区间查询事件（分页）。
     *
     * @param start 起始时间（包含）
     * @param end 结束时间（包含）
     * @param page 页码（从 0 开始）
     * @param size 每页数量
     */
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

    /**
     * 查询最近 N 条事件。
     *
     * @param limit 返回条数上限
     */
    override suspend fun findRecent(limit: Int): List<AuditEvent> = DatabaseFactory.dbQuery {
        AuditLogsTable.selectAll()
            .orderBy(AuditLogsTable.capturedAt, SortOrder.DESC)
            .limit(limit)
            .map { rowToEvent(it) }
    }

    /**
     * 根据事件 ID 精确查询。
     *
     * @param eventId 事件 ID
     * @return 匹配的事件；不存在则返回 null
     */
    override suspend fun findByEventId(eventId: String): AuditEvent? = DatabaseFactory.dbQuery {
        AuditLogsTable.selectAll()
            .where { AuditLogsTable.eventId eq eventId }
            .limit(1)
            .map { rowToEvent(it) }
            .firstOrNull()
    }

    /**
     * 统计事件总数。
     *
     * @return 总事件数
     */
    override suspend fun countAll(): Long = DatabaseFactory.dbQuery {
        AuditLogsTable.selectAll().count()
    }

    /**
     * 按协议统计事件数量。
     *
     * @return 协议到数量的映射
     */
    override suspend fun countByProtocol(): Map<ProtocolType, Long> = DatabaseFactory.dbQuery {
        AuditLogsTable
            .select(AuditLogsTable.protocol, AuditLogsTable.id.count())
            .groupBy(AuditLogsTable.protocol)
            .associate { row ->
                ProtocolType.valueOf(row[AuditLogsTable.protocol]) to row[AuditLogsTable.id.count()]
            }
    }

    /**
     * 将事件映射为数据库行。
     *
     * 注意：details 列使用 JSONB 存储完整事件，方便协议字段扩展。
     *
     * @param row Exposed 的更新/插入上下文
     * @param event 审计事件
     */
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

    /**
     * 将数据库行还原为事件对象。
     *
     * @param row 数据库查询结果行
     * @return 还原后的审计事件
     */
    private fun rowToEvent(row: ResultRow): AuditEvent {
        return row[AuditLogsTable.details]
    }
}
