package com.netaudit.model

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditEventTest {
    @Test
    fun `test HttpEvent serialization`() {
        val event = AuditEvent.HttpEvent(
            id = "test-id-1",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54321,
            dstPort = 80,
            method = "GET",
            url = "http://example.com/api/test",
            host = "example.com",
            userAgent = "TestAgent/1.0",
            contentType = "application/json",
            statusCode = 200
        )

        val json = AppJson.encodeToString<AuditEvent>(event)
        assertTrue(json.contains("\"protocol\":\"HTTP\""))

        val decoded = AppJson.decodeFromString<AuditEvent>(json)
        assertEquals(event, decoded)
    }

    @Test
    fun `test SmtpEvent serialization`() {
        val event = AuditEvent.SmtpEvent(
            id = "test-id-2",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54322,
            dstPort = 25,
            from = "sender@example.com",
            to = listOf("recipient@example.com"),
            subject = "Test Email",
            attachmentNames = listOf("file.txt"),
            attachmentSizes = listOf(1024),
            stage = "DATA"
        )

        val json = AppJson.encodeToString<AuditEvent>(event)
        assertTrue(json.contains("\"protocol\":\"SMTP\""))

        val decoded = AppJson.decodeFromString<AuditEvent>(json)
        assertEquals(event, decoded)
    }

    @Test
    fun `test FtpEvent serialization`() {
        val event = AuditEvent.FtpEvent(
            id = "test-id-3",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54323,
            dstPort = 21,
            username = "testuser",
            command = "RETR",
            argument = "file.txt",
            responseCode = 226,
            currentDirectory = "/home/testuser"
        )

        val json = AppJson.encodeToString<AuditEvent>(event)
        assertTrue(json.contains("\"protocol\":\"FTP\""))

        val decoded = AppJson.decodeFromString<AuditEvent>(json)
        assertEquals(event, decoded)
    }

    @Test
    fun `test DnsEvent serialization`() {
        val event = AuditEvent.DnsEvent(
            id = "test-id-4",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            srcIp = "192.168.1.100",
            dstIp = "8.8.8.8",
            srcPort = 54324,
            dstPort = 53,
            transactionId = 12345,
            queryDomain = "example.com",
            queryType = "A",
            isResponse = true,
            resolvedIps = listOf("93.184.216.34"),
            responseTtl = 3600
        )

        val json = AppJson.encodeToString<AuditEvent>(event)
        assertTrue(json.contains("\"protocol\":\"DNS\""))

        val decoded = AppJson.decodeFromString<AuditEvent>(json)
        assertEquals(event, decoded)
    }
}
