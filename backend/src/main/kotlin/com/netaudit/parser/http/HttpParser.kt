package com.netaudit.parser.http

import com.netaudit.model.AuditEvent
import com.netaudit.model.Direction
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.parser.ProtocolParser
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * HTTP 协议解析器 — 无状态，每个含 HTTP 请求/响应的 payload 直接解析。
 */
class HttpParser : ProtocolParser {
    override val protocolType = ProtocolType.HTTP
    override val ports = setOf(80, 8080, 8888)

    companion object {
        private const val SESSION_LAST_METHOD = "http.lastMethod"
        private const val SESSION_LAST_URL = "http.lastUrl"
        private const val SESSION_LAST_HOST = "http.lastHost"
        private const val SESSION_LAST_UA = "http.lastUserAgent"
        private const val SESSION_LAST_CT = "http.lastContentType"
    }

    override fun parse(context: StreamContext): AuditEvent? {
        val text = context.payloadAsText()
        if (text.isBlank()) return null

        return when (context.direction) {
            Direction.CLIENT_TO_SERVER -> parseRequest(context, text)
            Direction.SERVER_TO_CLIENT -> parseResponse(context, text)
        }
    }

    private fun parseRequest(context: StreamContext, text: String): AuditEvent.HttpEvent? {
        val lines = text.split("\r\n")
        val requestLine = lines[0]
        val parts = requestLine.split(" ", limit = 3)
        if (parts.size < 2) return null

        val method = parts[0].uppercase()
        if (!isValidHttpMethod(method)) return null

        val path = parts[1]
        val headers = parseHeaders(lines.drop(1))
        val host = headers["host"] ?: context.dstIp
        val userAgent = headers["user-agent"]
        val contentType = headers["content-type"]

        val url = if (path.startsWith("http")) path else "http://$host$path"

        context.sessionState[SESSION_LAST_METHOD] = method
        context.sessionState[SESSION_LAST_URL] = url
        context.sessionState[SESSION_LAST_HOST] = host
        userAgent?.let { context.sessionState[SESSION_LAST_UA] = it }
        contentType?.let { context.sessionState[SESSION_LAST_CT] = it }

        logger.debug { "HTTP Request: $method $url from ${context.srcIp}" }

        return AuditEvent.HttpEvent(
            id = generateId(),
            timestamp = context.timestamp,
            srcIp = context.srcIp,
            dstIp = context.dstIp,
            srcPort = context.srcPort,
            dstPort = context.dstPort,
            method = method,
            url = url,
            host = host,
            userAgent = userAgent,
            contentType = contentType,
            statusCode = null
        )
    }

    private fun parseResponse(context: StreamContext, text: String): AuditEvent.HttpEvent? {
        val lines = text.split("\r\n")
        val statusLine = lines[0]
        if (!statusLine.startsWith("HTTP/")) return null

        val statusParts = statusLine.split(" ", limit = 3)
        if (statusParts.size < 2) return null

        val statusCode = statusParts[1].toIntOrNull() ?: return null

        val lastMethod = context.sessionState[SESSION_LAST_METHOD] as? String ?: return null
        val lastUrl = context.sessionState[SESSION_LAST_URL] as? String ?: return null
        val lastHost = context.sessionState[SESSION_LAST_HOST] as? String ?: ""
        val lastUserAgent = context.sessionState[SESSION_LAST_UA] as? String
        val lastContentType = context.sessionState[SESSION_LAST_CT] as? String

        context.sessionState.remove(SESSION_LAST_METHOD)
        context.sessionState.remove(SESSION_LAST_URL)
        context.sessionState.remove(SESSION_LAST_HOST)
        context.sessionState.remove(SESSION_LAST_UA)
        context.sessionState.remove(SESSION_LAST_CT)

        val headers = parseHeaders(lines.drop(1))

        logger.debug { "HTTP Response: $statusCode for $lastMethod $lastUrl" }

        return AuditEvent.HttpEvent(
            id = generateId(),
            timestamp = context.timestamp,
            srcIp = context.dstIp,
            dstIp = context.srcIp,
            srcPort = context.dstPort,
            dstPort = context.srcPort,
            method = lastMethod,
            url = lastUrl,
            host = lastHost,
            userAgent = lastUserAgent,
            contentType = headers["content-type"] ?: lastContentType,
            statusCode = statusCode
        )
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        for (line in lines) {
            if (line.isBlank()) break
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().lowercase()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }
        return headers
    }

    private val validMethods = setOf(
        "GET", "POST", "PUT", "DELETE", "HEAD",
        "OPTIONS", "PATCH", "CONNECT", "TRACE"
    )

    private fun isValidHttpMethod(method: String): Boolean = method in validMethods

    private fun generateId(): String = UUID.randomUUID().toString()
}
