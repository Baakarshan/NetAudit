package com.netaudit.parser.http

import com.netaudit.model.Direction
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

        val event = parser.parse(context)
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

        val event = parser.parse(context)
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

        val event = parser.parse(responseContext)
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
