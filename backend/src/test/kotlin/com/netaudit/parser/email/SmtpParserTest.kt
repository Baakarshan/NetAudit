package com.netaudit.parser.email

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

class SmtpParserTest {
    private val parser = SmtpParser()

    @Test
    fun `test full smtp session`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("EHLO example.com\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("MAIL FROM:<alice@test.com>\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("RCPT TO:<bob@test.com>\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("354 End data with <CRLF>.<CRLF>\r\n", Direction.SERVER_TO_CLIENT, sessionState))

        val data =
            "From: alice@test.com\r\n" +
                "To: bob@test.com\r\n" +
                "Subject: Hello World\r\n" +
                "Content-Type: text/plain\r\n\r\n" +
                "Hello\r\n" +
                ".\r\n"

        val event = parser.parse(buildContext(data, Direction.CLIENT_TO_SERVER, sessionState))
            as com.netaudit.model.AuditEvent.SmtpEvent
        assertEquals("alice@test.com", event.from)
        assertEquals(listOf("bob@test.com"), event.to)
        assertEquals("Hello World", event.subject)
    }

    @Test
    fun `test multiple recipients`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("EHLO example.com\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("MAIL FROM:<alice@test.com>\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("RCPT TO:<bob@test.com>\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("RCPT TO:<carol@test.com>\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("354 End data with <CRLF>.<CRLF>\r\n", Direction.SERVER_TO_CLIENT, sessionState))

        val data =
            "From: alice@test.com\r\n" +
                "Subject: Hello\r\n\r\n" +
                "Body\r\n" +
                ".\r\n"

        val event = parser.parse(buildContext(data, Direction.CLIENT_TO_SERVER, sessionState))
            as com.netaudit.model.AuditEvent.SmtpEvent
        assertEquals(2, event.to.size)
        assertTrue(event.to.contains("bob@test.com"))
        assertTrue(event.to.contains("carol@test.com"))
    }

    @Test
    fun `test attachment detection`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("EHLO example.com\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("MAIL FROM:<alice@test.com>\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("RCPT TO:<bob@test.com>\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("354 End data with <CRLF>.<CRLF>\r\n", Direction.SERVER_TO_CLIENT, sessionState))

        val boundary = "----=_Part_123"
        val data =
            "From: alice@test.com\r\n" +
                "To: bob@test.com\r\n" +
                "Subject: With Attachment\r\n" +
                "Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n\r\n" +
                "--$boundary\r\n" +
                "Content-Disposition: attachment; filename=\"report.pdf\"\r\n" +
                "Content-Transfer-Encoding: base64\r\n\r\n" +
                "SGVsbG8=\r\n" +
                "--$boundary--\r\n" +
                ".\r\n"

        val event = parser.parse(buildContext(data, Direction.CLIENT_TO_SERVER, sessionState))
            as com.netaudit.model.AuditEvent.SmtpEvent
        assertTrue(event.attachmentNames.isNotEmpty())
    }

    @Test
    fun `test no data mode before 354`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("DATA\r\n", Direction.CLIENT_TO_SERVER, sessionState))

        val data =
            "From: alice@test.com\r\n" +
                "To: bob@test.com\r\n\r\n" +
                "Hello\r\n" +
                ".\r\n"

        val event = parser.parse(buildContext(data, Direction.CLIENT_TO_SERVER, sessionState))
        assertNull(event)
    }

    @Test
    fun `test blank payload`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(buildContext(" ", Direction.CLIENT_TO_SERVER, sessionState))
        assertNull(event)
    }

    @Test
    fun `test quit resets state`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("MAIL FROM:alice@test.com\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("RCPT TO:bob@test.com\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("QUIT\r\n", Direction.CLIENT_TO_SERVER, sessionState))

        val session = sessionState["smtp.session"] as SmtpSessionState
        assertEquals(SmtpPhase.CONNECTED, session.phase)
        assertNull(session.from)
        assertTrue(session.to.isEmpty())
    }

    @Test
    fun `test data mode with lf terminator and session from`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("MAIL FROM:alice@test.com\r\n", Direction.CLIENT_TO_SERVER, sessionState))
        parser.parse(buildContext("354 Go ahead\r\n", Direction.SERVER_TO_CLIENT, sessionState))

        val data =
            "Subject: Hi\n" +
                "\n" +
                "Body\n" +
                ".\n"

        val event = parser.parse(buildContext(data, Direction.CLIENT_TO_SERVER, sessionState))
            as com.netaudit.model.AuditEvent.SmtpEvent
        assertEquals("alice@test.com", event.from)
        assertEquals("Hi", event.subject)
    }

    @Test
    fun `test server response non numeric does not enable data mode`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("ABC text\r\n", Direction.SERVER_TO_CLIENT, sessionState))
        val session = sessionState["smtp.session"] as SmtpSessionState
        assertTrue(!session.inDataMode)
    }

    private fun buildContext(
        payload: String,
        direction: Direction,
        sessionState: MutableMap<String, Any>
    ): StreamContext {
        val payloadBytes = payload.toByteArray()
        val isClientToServer = direction == Direction.CLIENT_TO_SERVER
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:00:00:00:00:01",
            dstMac = "00:00:00:00:00:02",
            srcIp = if (isClientToServer) "192.168.1.100" else "10.0.0.2",
            dstIp = if (isClientToServer) "10.0.0.2" else "192.168.1.100",
            ipProtocol = TransportProtocol.TCP,
            srcPort = if (isClientToServer) 54321 else 25,
            dstPort = if (isClientToServer) 25 else 54321,
            tcpFlags = TcpFlags(false, false, false, false, false),
            seqNumber = 1,
            ackNumber = 1,
            payload = payloadBytes
        )

        return StreamContext(
            key = com.netaudit.model.StreamKey(metadata.srcIp, metadata.srcPort, metadata.dstIp, metadata.dstPort),
            metadata = metadata,
            payload = payloadBytes,
            direction = direction,
            sessionState = sessionState
        )
    }
}
