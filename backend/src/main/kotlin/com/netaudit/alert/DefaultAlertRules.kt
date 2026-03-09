package com.netaudit.alert

import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRule
import com.netaudit.model.AuditEvent

object DefaultAlertRules {
    fun all(): List<AlertRule> = listOf(
        telnetLoginDetection(),
        sensitiveUrlAccess(),
        largeAttachment(),
        dnsLongDomain()
    )

    private fun telnetLoginDetection() = AlertRule(
        id = "rule-telnet-login",
        name = "TELNET Login Detection",
        description = "TELNET session detected - potential security risk",
        level = AlertLevel.CRITICAL,
        condition = { event ->
            event is AuditEvent.TelnetEvent && event.username != null
        }
    )

    private fun sensitiveUrlAccess() = AlertRule(
        id = "rule-sensitive-url",
        name = "Sensitive URL Access",
        description = "Access to sensitive URL path detected",
        level = AlertLevel.WARN,
        condition = { event ->
            event is AuditEvent.HttpEvent &&
                SENSITIVE_PATHS.any { event.url.lowercase().contains(it) }
        }
    )

    private fun largeAttachment() = AlertRule(
        id = "rule-large-attachment",
        name = "Large Email Attachment",
        description = "Email with large attachment detected (>5MB)",
        level = AlertLevel.WARN,
        condition = { event ->
            event is AuditEvent.SmtpEvent &&
                event.attachmentSizes.any { it > 5 * 1024 * 1024 }
        }
    )

    private fun dnsLongDomain() = AlertRule(
        id = "rule-dns-tunnel",
        name = "DNS Tunnel Suspicion",
        description = "Unusually long DNS domain query detected",
        level = AlertLevel.WARN,
        condition = { event ->
            event is AuditEvent.DnsEvent && event.queryDomain.length > 50
        }
    )

    private val SENSITIVE_PATHS = listOf(
        "/admin", "/config", "/secret", "/passwd",
        "/env", "/.git", "/backup", "/debug"
    )
}
