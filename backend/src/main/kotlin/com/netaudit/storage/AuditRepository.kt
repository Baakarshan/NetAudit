package com.netaudit.storage

import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import kotlinx.datetime.Instant

/**
 * 审计事件存储接口。
 *
 * 负责审计事件的持久化和查询。
 * 实现类将使用 Exposed ORM 操作 PostgreSQL。
 */
interface AuditRepository {
    /**
     * 保存单个审计事件
     */
    suspend fun save(event: AuditEvent): Long

    /**
     * 批量保存审计事件（用于高吞吐场景）
     */
    suspend fun saveBatch(events: List<AuditEvent>): List<Long>

    /**
     * 根据 ID 查询审计事件
     */
    suspend fun findById(id: Long): AuditEvent?

    /**
     * 分页查询审计事件
     *
     * @param offset 偏移量
     * @param limit 每页数量
     * @param protocolType 协议类型过滤（可选）
     * @param startTime 开始时间过滤（可选）
     * @param endTime 结束时间过滤（可选）
     * @return 审计事件列表
     */
    suspend fun findAll(
        offset: Int = 0,
        limit: Int = 100,
        protocolType: ProtocolType? = null,
        startTime: Instant? = null,
        endTime: Instant? = null
    ): List<AuditEvent>

    /**
     * 统计审计事件总数
     */
    suspend fun count(
        protocolType: ProtocolType? = null,
        startTime: Instant? = null,
        endTime: Instant? = null
    ): Long

    /**
     * 根据源 IP 查询审计事件
     */
    suspend fun findBySourceIp(sourceIp: String, limit: Int = 100): List<AuditEvent>

    /**
     * 根据目标 IP 查询审计事件
     */
    suspend fun findByDestIp(destIp: String, limit: Int = 100): List<AuditEvent>
}
