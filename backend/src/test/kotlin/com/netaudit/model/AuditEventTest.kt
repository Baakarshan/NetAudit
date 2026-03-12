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

    @Test
    fun `test TelnetEvent serialization`() {
        val event = AuditEvent.TelnetEvent(
            id = "test-id-5",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54325,
            dstPort = 23,
            username = "root",
            commandLine = "whoami",
            direction = Direction.CLIENT_TO_SERVER
        )

        val json = AppJson.encodeToString<AuditEvent>(event)
        assertTrue(json.contains("\"protocol\":\"TELNET\""))

        val decoded = AppJson.decodeFromString<AuditEvent>(json)
        assertEquals(event, decoded)
    }

    @Test
    fun `test Pop3Event serialization`() {
        val event = AuditEvent.Pop3Event(
            id = "test-id-6",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54326,
            dstPort = 110,
            username = "alice",
            command = "RETR",
            from = "sender@example.com",
            to = listOf("recipient@example.com"),
            subject = "Test Email",
            attachmentNames = listOf("file.txt"),
            attachmentSizes = listOf(128),
            mailSize = 256
        )

        val json = AppJson.encodeToString<AuditEvent>(event)
        assertTrue(json.contains("\"protocol\":\"POP3\""))

        val decoded = AppJson.decodeFromString<AuditEvent>(json)
        assertEquals(event, decoded)
    }

    @Test
    fun `test TlsEvent serialization`() {
        val event = AuditEvent.TlsEvent(
            id = "test-id-7",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            srcIp = "192.168.1.100",
            dstIp = "192.168.1.1",
            srcPort = 54327,
            dstPort = 443,
            serverName = "example.com",
            alpn = listOf("h2"),
            clientVersion = "TLS 1.2",
            supportedVersions = listOf("TLS 1.3", "TLS 1.2")
        )

        val json = AppJson.encodeToString<AuditEvent>(event)
        assertTrue(json.contains("\"protocol\":\"TLS\""))

        val decoded = AppJson.decodeFromString<AuditEvent>(json)
        assertEquals(event, decoded)
    }
}
