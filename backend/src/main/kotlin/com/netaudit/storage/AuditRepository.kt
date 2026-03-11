package com.netaudit.storage

import com.netaudit.model.*
import kotlinx.datetime.Instant

/**
 * 审计事件仓储接口。
 *
 * 约定：查询结果按时间倒序返回，分页参数以 0 为起始页。
 */
interface AuditRepository {
    /** 保存单条事件。 */
    suspend fun save(event: AuditEvent)

    /** 批量保存事件。 */
    suspend fun saveBatch(events: List<AuditEvent>)

    /** 查询全部事件（分页）。 */
    suspend fun findAll(page: Int = 0, size: Int = 50): List<AuditEvent>

    /** 按协议查询事件（分页）。 */
    suspend fun findByProtocol(protocol: ProtocolType, page: Int = 0, size: Int = 50): List<AuditEvent>

    /** 按源 IP 查询事件（分页）。 */
    suspend fun findBySourceIp(srcIp: String, page: Int = 0, size: Int = 50): List<AuditEvent>

    /** 按时间区间查询事件（分页）。 */
    suspend fun findBetween(start: Instant, end: Instant, page: Int = 0, size: Int = 50): List<AuditEvent>

    /** 查询最近 N 条事件。 */
    suspend fun findRecent(limit: Int = 20): List<AuditEvent>

    /** 根据事件 ID 精确查询。 */
    suspend fun findByEventId(eventId: String): AuditEvent?

    /** 总事件数。 */
    suspend fun countAll(): Long

    /** 各协议事件统计。 */
    suspend fun countByProtocol(): Map<ProtocolType, Long>
}
