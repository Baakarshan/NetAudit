package com.netaudit.storage

import com.netaudit.model.*
import kotlinx.datetime.Instant

interface AuditRepository {
    suspend fun save(event: AuditEvent)
    suspend fun saveBatch(events: List<AuditEvent>)
    suspend fun findAll(page: Int = 0, size: Int = 50): List<AuditEvent>
    suspend fun findByProtocol(protocol: ProtocolType, page: Int = 0, size: Int = 50): List<AuditEvent>
    suspend fun findBySourceIp(srcIp: String, page: Int = 0, size: Int = 50): List<AuditEvent>
    suspend fun findBetween(start: Instant, end: Instant, page: Int = 0, size: Int = 50): List<AuditEvent>
    suspend fun findRecent(limit: Int = 20): List<AuditEvent>
    suspend fun countAll(): Long
    suspend fun countByProtocol(): Map<ProtocolType, Long>
}
