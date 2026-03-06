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
import kotlin.test.assertTrue

class Pop3ParserTest {
    private val parser = Pop3Parser()

    @Test
    fun `test USER command`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(buildContext("USER alice\r\n", Direction.CLIENT_TO_SERVER, sessionState))
            as com.netaudit.model.AuditEvent.Pop3Event
        assertEquals("USER", event.command)
        assertEquals("alice", event.username)
    }

    @Test
    fun `test RETR mail`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("RETR 1\r\n", Direction.CLIENT_TO_SERVER, sessionState))

        val response =
            "+OK\r\n" +
                "From: alice@test.com\r\n" +
                "To: bob@test.com\r\n" +
                "Subject: Hi\r\n\r\n" +
                "Body\r\n" +
                ".\r\n"

        val event = parser.parse(buildContext(response, Direction.SERVER_TO_CLIENT, sessionState))
            as com.netaudit.model.AuditEvent.Pop3Event
        assertEquals("RETR", event.command)
        assertEquals("alice@test.com", event.from)
        assertEquals(listOf("bob@test.com"), event.to)
        assertEquals("Hi", event.subject)
    }

    @Test
    fun `test attachment mail`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("RETR 1\r\n", Direction.CLIENT_TO_SERVER, sessionState))

        val boundary = "----=_Part_123"
        val response =
            "+OK\r\n" +
                "Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n\r\n" +
                "--$boundary\r\n" +
                "Content-Disposition: attachment; filename=\"report.pdf\"\r\n" +
                "Content-Transfer-Encoding: base64\r\n\r\n" +
                "SGVsbG8=\r\n" +
                "--$boundary--\r\n" +
                ".\r\n"

        val event = parser.parse(buildContext(response, Direction.SERVER_TO_CLIENT, sessionState))
            as com.netaudit.model.AuditEvent.Pop3Event
        assertTrue(event.attachmentNames.isNotEmpty())
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
            srcIp = if (isClientToServer) "192.168.1.100" else "10.0.0.3",
            dstIp = if (isClientToServer) "10.0.0.3" else "192.168.1.100",
            ipProtocol = TransportProtocol.TCP,
            srcPort = if (isClientToServer) 54321 else 110,
            dstPort = if (isClientToServer) 110 else 54321,
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
