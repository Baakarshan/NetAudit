package com.netaudit.storage

import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord

/**
 * 告警仓储接口。
 */
interface AlertRepository {
    /** 保存告警记录。 */
    suspend fun save(alert: AlertRecord)

    /** 查询最近 N 条告警。 */
    suspend fun findRecent(limit: Int = 20): List<AlertRecord>

    /** 按告警等级统计数量。 */
    suspend fun countByLevel(): Map<AlertLevel, Long>
}
