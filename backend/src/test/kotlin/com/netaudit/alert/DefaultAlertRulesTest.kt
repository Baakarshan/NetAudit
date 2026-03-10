package com.netaudit.alert

import com.netaudit.model.AuditEvent
import com.netaudit.model.AlertLevel
import com.netaudit.model.Direction
import com.netaudit.model.ProtocolType
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultAlertRulesTest {
    @Test
    fun `default rules cover true and false branches`() {
        val rules = DefaultAlertRules.all().associateBy { it.id }

        val telnetRule = rules.getValue("rule-telnet-login")
        assertTrue(telnetRule.condition(telnetEvent(username = "admin")))
        assertFalse(telnetRule.condition(telnetEvent(username = null)))
        assertFalse(telnetRule.condition(httpEvent("http://example.com/")))

        val sensitiveRule = rules.getValue("rule-sensitive-url")
        assertTrue(sensitiveRule.condition(httpEvent("http://example.com/admin/config")))
        assertFalse(sensitiveRule.condition(httpEvent("http://example.com/index.html")))
        assertFalse(sensitiveRule.condition(dnsEvent("example.com")))

        val largeAttachmentRule = rules.getValue("rule-large-attachment")
        assertTrue(largeAttachmentRule.condition(smtpEvent(attachmentSizes = listOf(6 * 1024 * 1024))))
        assertFalse(largeAttachmentRule.condition(smtpEvent(attachmentSizes = listOf(1))))
        assertFalse(largeAttachmentRule.condition(httpEvent("http://example.com/")))

        val dnsRule = rules.getValue("rule-dns-tunnel")
        assertTrue(dnsRule.condition(dnsEvent("a".repeat(60) + ".example.com")))
        assertFalse(dnsRule.condition(dnsEvent("short.example.com")))
        assertFalse(dnsRule.condition(httpEvent("http://example.com/")))
    }

    private fun telnetEvent(username: String?): AuditEvent.TelnetEvent = AuditEvent.TelnetEvent(
        id = "telnet-1",
        timestamp = Clock.System.now(),
        srcIp = "192.168.1.100",
        dstIp = "10.0.0.1",
        srcPort = 54321,
        dstPort = 23,
        username = username,
        commandLine = "whoami",
        direction = Direction.CLIENT_TO_SERVER
    )

    private fun httpEvent(url: String): AuditEvent.HttpEvent = AuditEvent.HttpEvent(
        id = "http-1",
        timestamp = Clock.System.now(),
        srcIp = "192.168.1.100",
        dstIp = "93.184.216.34",
        srcPort = 54321,
        dstPort = 80,
        method = "GET",
        url = url,
        host = "example.com",
        userAgent = "TestAgent/1.0",
        contentType = "text/html",
        statusCode = 200,
        protocol = ProtocolType.HTTP,
        alertLevel = AlertLevel.INFO
    )

    private fun smtpEvent(attachmentSizes: List<Int>): AuditEvent.SmtpEvent = AuditEvent.SmtpEvent(
        id = "smtp-1",
        timestamp = Clock.System.now(),
        srcIp = "192.168.1.100",
        dstIp = "10.0.0.2",
        srcPort = 54322,
        dstPort = 25,
        from = "alice@example.com",
        to = listOf("bob@example.com"),
        subject = "hi",
        attachmentNames = listOf("a.txt"),
        attachmentSizes = attachmentSizes,
        stage = "DATA"
    )

    private fun dnsEvent(domain: String): AuditEvent.DnsEvent = AuditEvent.DnsEvent(
        id = "dns-1",
        timestamp = Clock.System.now(),
        srcIp = "192.168.1.100",
        dstIp = "8.8.8.8",
        srcPort = 5353,
        dstPort = 53,
        transactionId = 1234,
        queryDomain = domain,
        queryType = "A",
        isResponse = false,
        resolvedIps = emptyList(),
        responseTtl = null
    )
}
