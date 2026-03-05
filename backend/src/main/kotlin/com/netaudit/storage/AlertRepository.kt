package com.netaudit.storage

import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord

interface AlertRepository {
    suspend fun save(alert: AlertRecord)
    suspend fun findRecent(limit: Int = 20): List<AlertRecord>
    suspend fun countByLevel(): Map<AlertLevel, Long>
}
