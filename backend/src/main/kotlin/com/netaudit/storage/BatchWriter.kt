package com.netaudit.storage

import com.netaudit.event.AuditEventBus
import com.netaudit.model.AppJson
import com.netaudit.model.AuditEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlin.coroutines.coroutineContext

private val logger = KotlinLogging.logger {}

/**
 * 批量写入器 — 双触发条件（数量 OR 时间间隔）。
 */
class BatchWriter(
    private val repository: AuditRepository,
    private val eventBus: AuditEventBus,
    private val scope: CoroutineScope,
    private val batchSize: Int = 200,
    private val flushIntervalMs: Long = 2000
) {
    private val buffer = mutableListOf<AuditEvent>()
    private val retryCountMap = mutableMapOf<String, Int>()
    private val mutex = Mutex()
    private var collectJob: Job? = null
    private var flushJob: Job? = null

    fun start() {
        // 避免重复启动导致多条消费/flush 协程
        if (collectJob != null || flushJob != null) {
            return
        }

        collectJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // 立即开始消费，减少启动窗口期丢事件风险
            eventBus.auditEvents.collect { event ->
                val shouldFlush = mutex.withLock {
                    buffer.add(event)
                    buffer.size >= batchSize
                }
                if (shouldFlush) {
                    flush()
                }
            }
        }

        flushJob = scope.launch {
            try {
                while (true) {
                    // 取消优先，避免 delay 后残留一次 flush
                    coroutineContext.ensureActive()
                    delay(flushIntervalMs)
                    flush()
                }
            } finally {
                logger.debug { "BatchWriter flush loop stopped" }
            }
        }

        logger.info { "BatchWriter started (batchSize=$batchSize, flushInterval=${flushIntervalMs}ms)" }
    }

    suspend fun shutdown() {
        flush()
        stop()
        logger.info { "BatchWriter shutdown complete" }
    }

    suspend fun stop() {
        val currentCollectJob = collectJob
        val currentFlushJob = flushJob
        collectJob = null
        flushJob = null
        currentCollectJob?.cancelAndJoin()
        currentFlushJob?.cancelAndJoin()
    }

    private suspend fun flush() {
        val batch = mutex.withLock {
            if (buffer.isEmpty()) return
            val copy = buffer.toList()
            buffer.clear()
            copy
        }

        try {
            repository.saveBatch(batch)
            logger.debug { "Flushed ${batch.size} audit events to database" }
            batch.forEach { retryCountMap.remove(it.id) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to flush ${batch.size} events: ${e.message}" }

            // 使用批次首条事件作为重试计数键，保证同一批次退避一致
            val firstEventId = batch.firstOrNull()?.id ?: return
            val retryCount = retryCountMap.getOrDefault(firstEventId, 0)

            if (retryCount < 3) {
                mutex.withLock {
                    buffer.addAll(0, batch)
                    batch.forEach { retryCountMap[it.id] = retryCount + 1 }
                }
                logger.warn { "Retry ${retryCount + 1}/3 for ${batch.size} events" }
            } else {
                writeToDeadLetterQueue(batch, e)
                batch.forEach { retryCountMap.remove(it.id) }
                logger.error { "Dropped ${batch.size} events after 3 retries" }
            }
        }
    }

    private fun writeToDeadLetterQueue(batch: List<AuditEvent>, error: Exception) {
        try {
            val dlqFile = java.io.File("logs/dead-letter-queue.jsonl")
            dlqFile.parentFile?.mkdirs()
            dlqFile.appendText(
                "# Error: ${error.message}\n" +
                    batch.joinToString("\n") { event ->
                        AppJson.encodeToString(event)
                    } + "\n"
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to write to DLQ: ${e.message}" }
        }
    }
}
