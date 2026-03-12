package com.netaudit.storage

import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord

/**
 * 告警仓储接口。
 */
interface AlertRepository {
    /**
     * 保存告警记录。
     *
     * @param alert 告警记录
     */
    suspend fun save(alert: AlertRecord)

    /**
     * 查询最近 N 条告警。
     *
     * @param limit 返回条数上限
     */
    suspend fun findRecent(limit: Int = 20): List<AlertRecord>

    /**
     * 按告警等级统计数量。
     *
     * @return 告警等级到数量的映射
     */
    suspend fun countByLevel(): Map<AlertLevel, Long>
}
