package com.netaudit.storage

import com.netaudit.model.Alert
import com.netaudit.model.AlertLevel
import kotlinx.datetime.Instant

/**
 * 告警存储接口。
 *
 * 负责告警的持久化和查询。
 * 实现类将使用 Exposed ORM 操作 PostgreSQL。
 */
interface AlertRepository {
    /**
     * 保存单个告警
     */
    suspend fun save(alert: Alert): Long

    /**
     * 批量保存告警
     */
    suspend fun saveBatch(alerts: List<Alert>): List<Long>

    /**
     * 根据 ID 查询告警
     */
    suspend fun findById(id: Long): Alert?

    /**
     * 分页查询告警
     *
     * @param offset 偏移量
     * @param limit 每页数量
     * @param level 告警级别过滤（可选）
     * @param startTime 开始时间过滤（可选）
     * @param endTime 结束时间过滤（可选）
     * @return 告警列表
     */
    suspend fun findAll(
        offset: Int = 0,
        limit: Int = 100,
        level: AlertLevel? = null,
        startTime: Instant? = null,
        endTime: Instant? = null
    ): List<Alert>

    /**
     * 统计告警总数
     */
    suspend fun count(
        level: AlertLevel? = null,
        startTime: Instant? = null,
        endTime: Instant? = null
    ): Long

    /**
     * 查询未处理的告警
     */
    suspend fun findUnresolved(limit: Int = 100): List<Alert>

    /**
     * 标记告警为已处理
     */
    suspend fun markResolved(id: Long): Boolean

    /**
     * 根据关联的审计事件 ID 查询告警
     */
    suspend fun findByAuditEventId(auditEventId: Long): List<Alert>
}
