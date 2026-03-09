package com.netaudit.parser.http

import com.netaudit.model.Direction
import com.netaudit.model.AuditEvent
import com.netaudit.model.PacketMetadata
import com.netaudit.model.StreamContext
import com.netaudit.model.TcpFlags
import com.netaudit.model.TransportProtocol
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpParserTest {
    private val parser = HttpParser()

    @Test
    fun `test GET request`() {
        val payload = "GET /index.html HTTP/1.1\r\nHost: example.com\r\nUser-Agent: curl/7.68.0\r\n\r\n"
        val context = buildContext(payload)

        val event = parser.parse(context) as? AuditEvent.HttpEvent
        assertNotNull(event)
        assertEquals("GET", event.method)
        assertEquals("http://example.com/index.html", event.url)
        assertEquals("example.com", event.host)
        assertEquals("curl/7.68.0", event.userAgent)
        assertNull(event.statusCode)
        assertEquals("192.168.1.100", event.srcIp)
    }

    @Test
    fun `test POST request`() {
        val payload = "POST /api/login HTTP/1.1\r\nHost: api.example.com\r\nContent-Type: application/json\r\n\r\n{}"
        val context = buildContext(payload)

        val event = parser.parse(context) as? AuditEvent.HttpEvent
        assertNotNull(event)
        assertEquals("POST", event.method)
        assertEquals("application/json", event.contentType)
    }

    @Test
    fun `test response with session association`() {
        val sessionState = mutableMapOf<String, Any>()
        val requestPayload = "GET /index.html HTTP/1.1\r\nHost: example.com\r\n\r\n"
        val requestContext = buildContext(
            payload = requestPayload,
            direction = Direction.CLIENT_TO_SERVER,
            sessionState = sessionState
        )
        parser.parse(requestContext)

        val responsePayload = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n"
        val responseContext = buildContext(
            payload = responsePayload,
            direction = Direction.SERVER_TO_CLIENT,
            srcIp = "93.184.216.34",
            dstIp = "192.168.1.100",
            srcPort = 80,
            dstPort = 54321,
            sessionState = sessionState
        )

        val event = parser.parse(responseContext) as? AuditEvent.HttpEvent
        assertNotNull(event)
        assertEquals(200, event.statusCode)
        assertEquals("GET", event.method)
        assertEquals("192.168.1.100", event.srcIp)
        assertEquals("93.184.216.34", event.dstIp)
    }

    @Test
    fun `test response without session`() {
        val responsePayload = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n"
        val context = buildContext(
            payload = responsePayload,
            direction = Direction.SERVER_TO_CLIENT
        )

        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test non http payload`() {
        val context = buildContext("HELLO WORLD")
        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test empty payload`() {
        val context = buildContext("")
        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test invalid request line`() {
        val context = buildContext("GET\r\n\r\n")
        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test invalid http method`() {
        val context = buildContext("FOO / HTTP/1.1\r\n\r\n")
        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test request uses dst host and absolute url`() {
        val payload = "GET http://example.com/path HTTP/1.1\r\nUser-Agent: test-agent\r\n\r\n"
        val context = buildContext(payload, dstIp = "9.9.9.9")

        val event = parser.parse(context) as? AuditEvent.HttpEvent
        assertNotNull(event)
        assertEquals("http://example.com/path", event.url)
        assertEquals("9.9.9.9", event.host)
        assertEquals("test-agent", event.userAgent)
    }

    @Test
    fun `test headers stop at blank line`() {
        val payload = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\nUser-Agent: later\r\n\r\n"
        val context = buildContext(payload)

        val event = parser.parse(context) as? AuditEvent.HttpEvent
        assertNotNull(event)
        assertEquals("example.com", event.host)
        assertNull(event.userAgent)
    }

    @Test
    fun `test headers ignore invalid lines`() {
        val payload = "GET / HTTP/1.1\r\nBadHeader\r\n:invalid\r\nHost: example.com\r\n\r\n"
        val context = buildContext(payload)

        val event = parser.parse(context) as? AuditEvent.HttpEvent
        assertNotNull(event)
        assertEquals("example.com", event.host)
    }

    @Test
    fun `test response invalid status line`() {
        val context = buildContext(
            payload = "NOTHTTP 200 OK\r\n\r\n",
            direction = Direction.SERVER_TO_CLIENT
        )
        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test response invalid status code`() {
        val sessionState = mutableMapOf<String, Any>(
            "http.lastMethod" to "GET",
            "http.lastUrl" to "http://example.com",
            "http.lastHost" to "example.com"
        )
        val context = buildContext(
            payload = "HTTP/1.1 abc OK\r\n\r\n",
            direction = Direction.SERVER_TO_CLIENT,
            sessionState = sessionState
        )
        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test response status line missing code`() {
        val sessionState = mutableMapOf<String, Any>(
            "http.lastMethod" to "GET",
            "http.lastUrl" to "http://example.com"
        )
        val context = buildContext(
            payload = "HTTP/1.1\r\n\r\n",
            direction = Direction.SERVER_TO_CLIENT,
            sessionState = sessionState
        )
        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test response missing last url`() {
        val sessionState = mutableMapOf<String, Any>(
            "http.lastMethod" to "GET"
        )
        val context = buildContext(
            payload = "HTTP/1.1 200 OK\r\n\r\n",
            direction = Direction.SERVER_TO_CLIENT,
            sessionState = sessionState
        )
        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test response missing last host uses empty`() {
        val sessionState = mutableMapOf<String, Any>(
            "http.lastMethod" to "GET",
            "http.lastUrl" to "http://example.com"
        )
        val context = buildContext(
            payload = "HTTP/1.1 200 OK\r\n\r\n",
            direction = Direction.SERVER_TO_CLIENT,
            sessionState = sessionState
        )
        val event = parser.parse(context) as? AuditEvent.HttpEvent
        assertNotNull(event)
        assertEquals("", event.host)
    }

    @Test
    fun `test response uses session fallback and clears`() {
        val sessionState = mutableMapOf<String, Any>(
            "http.lastMethod" to "GET",
            "http.lastUrl" to "http://example.com",
            "http.lastHost" to "example.com",
            "http.lastUserAgent" to "ua",
            "http.lastContentType" to "text/plain"
        )
        val context = buildContext(
            payload = "HTTP/1.1 200 OK\r\n\r\n",
            direction = Direction.SERVER_TO_CLIENT,
            srcIp = "93.184.216.34",
            dstIp = "192.168.1.100",
            srcPort = 80,
            dstPort = 54321,
            sessionState = sessionState
        )

        val event = parser.parse(context) as? AuditEvent.HttpEvent
        assertNotNull(event)
        assertEquals("text/plain", event.contentType)
        assertEquals("ua", event.userAgent)
        assertTrue(sessionState.isEmpty())
    }

    @Test
    fun `test response requires session`() {
        val context = buildContext(
            payload = "HTTP/1.1 200 OK\r\n\r\n",
            direction = Direction.SERVER_TO_CLIENT,
            sessionState = mutableMapOf("http.lastUrl" to "http://example.com")
        )
        val event = parser.parse(context)
        assertNull(event)
    }

    @Test
    fun `test ports include 8080`() {
        assertTrue(parser.ports.contains(8080))
    }

    private fun buildContext(
        payload: String,
        direction: Direction = Direction.CLIENT_TO_SERVER,
        srcIp: String = "192.168.1.100",
        dstIp: String = "93.184.216.34",
        srcPort: Int = 54321,
        dstPort: Int = 80,
        sessionState: MutableMap<String, Any> = mutableMapOf()
    ): StreamContext {
        val payloadBytes = payload.toByteArray()
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:00:00:00:00:01",
            dstMac = "00:00:00:00:00:02",
            srcIp = srcIp,
            dstIp = dstIp,
            ipProtocol = TransportProtocol.TCP,
            srcPort = srcPort,
            dstPort = dstPort,
            tcpFlags = TcpFlags(false, false, false, false, false),
            seqNumber = 1,
            ackNumber = 1,
            payload = payloadBytes
        )

        return StreamContext(
            key = com.netaudit.model.StreamKey(srcIp, srcPort, dstIp, dstPort),
            metadata = metadata,
            payload = payloadBytes,
            direction = direction,
            sessionState = sessionState
        )
    }
}
