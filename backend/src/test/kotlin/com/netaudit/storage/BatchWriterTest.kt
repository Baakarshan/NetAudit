package com.netaudit.storage

import com.netaudit.event.AuditEventBus
import com.netaudit.model.AuditEvent
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue

class BatchWriterTest {
    @Test
    fun `test flush on reaching batchSize`() = runTest {
        val mockRepo = mockk<AuditRepository>()
        coEvery { mockRepo.saveBatch(any()) } just Runs

        val eventBus = AuditEventBus()
        val writer = BatchWriter(
            repository = mockRepo,
            eventBus = eventBus,
            scope = this,
            batchSize = 3,
            flushIntervalMs = 10000
        )
        writer.start()

        try {
            repeat(3) { i ->
                eventBus.emitAudit(httpEvent("test-$i"))
            }

            runCurrent()

            coVerify(exactly = 1) { mockRepo.saveBatch(match { it.size == 3 }) }
        } finally {
            writer.shutdown()
        }
    }

    @Test
    fun `test flush on timer`() = runTest {
        val mockRepo = mockk<AuditRepository>()
        coEvery { mockRepo.saveBatch(any()) } just Runs

        val eventBus = AuditEventBus()
        val writer = BatchWriter(
            repository = mockRepo,
            eventBus = eventBus,
            scope = this,
            batchSize = 100,
            flushIntervalMs = 500
        )
        writer.start()

        try {
            repeat(2) { i ->
                eventBus.emitAudit(httpEvent("test-$i"))
            }

            advanceTimeBy(600)
            runCurrent()

            coVerify(exactly = 1) { mockRepo.saveBatch(match { it.size == 2 }) }
        } finally {
            writer.shutdown()
        }
    }

    @Test
    fun `test no flush on empty buffer`() = runTest {
        val mockRepo = mockk<AuditRepository>()
        coEvery { mockRepo.saveBatch(any()) } just Runs

        val eventBus = AuditEventBus()
        val writer = BatchWriter(
            repository = mockRepo,
            eventBus = eventBus,
            scope = this,
            batchSize = 100,
            flushIntervalMs = 500
        )
        writer.start()

        try {
            advanceTimeBy(600)
            runCurrent()

            coVerify(exactly = 0) { mockRepo.saveBatch(any()) }
        } finally {
            writer.shutdown()
        }
    }

    @Test
    fun `retry and dead letter queue after failures`() = runTest {
        val mockRepo = mockk<AuditRepository>()
        coEvery { mockRepo.saveBatch(any()) } throws RuntimeException("db down")

        val eventBus = AuditEventBus()
        val writer = BatchWriter(
            repository = mockRepo,
            eventBus = eventBus,
            scope = this,
            batchSize = 1,
            flushIntervalMs = 1
        )
        writer.start()

        val dlqFile = File("logs/dead-letter-queue.jsonl")
        if (dlqFile.exists()) {
            dlqFile.delete()
        }

        try {
            eventBus.emitAudit(httpEvent("retry-1"))
            runCurrent()

            advanceTimeBy(10)
            runCurrent()

            writer.shutdown()
            assertTrue(dlqFile.exists())
        } finally {
            writer.stop()
        }
    }

    private fun httpEvent(id: String): AuditEvent.HttpEvent =
        AuditEvent.HttpEvent(
            id = id,
            timestamp = Clock.System.now(),
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 80,
            method = "GET",
            url = "/test",
            host = "example.com",
            userAgent = "TestAgent/1.0",
            contentType = "application/json",
            statusCode = 200
        )
}
