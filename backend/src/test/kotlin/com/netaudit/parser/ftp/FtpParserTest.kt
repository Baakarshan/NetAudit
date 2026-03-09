package com.netaudit.parser.ftp

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

class FtpParserTest {
    private val parser = FtpParser()

    @Test
    fun `test USER command`() {
        val sessionState = mutableMapOf<String, Any>()
        val context = buildContext("USER alice\r\n", sessionState = sessionState)

        val event = parser.parse(context)
        assertNotNull(event)
        val ftpEvent = event as com.netaudit.model.AuditEvent.FtpEvent
        assertEquals("USER", ftpEvent.command)
        assertEquals("alice", ftpEvent.argument)

        val session = sessionState["ftp.session"] as FtpSessionState
        assertEquals("alice", session.username)
    }

    @Test
    fun `test login flow`() {
        val sessionState = mutableMapOf<String, Any>()
        val userContext = buildContext("USER alice\r\n", sessionState = sessionState)
        parser.parse(userContext)

        val passContext = buildContext("PASS secret\r\n", sessionState = sessionState)
        val passEvent = parser.parse(passContext)
        assertNull(passEvent)

        val respContext = buildContext(
            payload = "230 Login successful\r\n",
            direction = Direction.SERVER_TO_CLIENT,
            srcIp = "10.0.0.1",
            dstIp = "192.168.1.100",
            srcPort = 21,
            dstPort = 54321,
            sessionState = sessionState
        )

        val respEvent = parser.parse(respContext) as com.netaudit.model.AuditEvent.FtpEvent
        assertEquals("LOGIN", respEvent.command)
        assertEquals(230, respEvent.responseCode)
    }

    @Test
    fun `test RETR command after login`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("USER alice\r\n", sessionState = sessionState))
        parser.parse(buildContext("PASS secret\r\n", sessionState = sessionState))
        parser.parse(
            buildContext(
                payload = "230 Login successful\r\n",
                direction = Direction.SERVER_TO_CLIENT,
                srcIp = "10.0.0.1",
                dstIp = "192.168.1.100",
                srcPort = 21,
                dstPort = 54321,
                sessionState = sessionState
            )
        )

        val retrContext = buildContext("RETR secret.doc\r\n", sessionState = sessionState)
        val event = parser.parse(retrContext) as com.netaudit.model.AuditEvent.FtpEvent
        assertEquals("RETR", event.command)
        assertEquals("secret.doc", event.argument)
        assertEquals("alice", event.username)
    }

    @Test
    fun `test CWD command updates directory`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(buildContext("CWD /pub/data\r\n", sessionState = sessionState))
            as com.netaudit.model.AuditEvent.FtpEvent
        assertEquals("CWD", event.command)
        assertEquals("/pub/data", event.argument)

        val session = sessionState["ftp.session"] as FtpSessionState
        assertEquals("/pub/data", session.currentDirectory)
    }

    @Test
    fun `test PWD response updates directory`() {
        val sessionState = mutableMapOf<String, Any>()
        val respContext = buildContext(
            payload = "257 \"/home/alice\" is current directory\r\n",
            direction = Direction.SERVER_TO_CLIENT,
            srcIp = "10.0.0.1",
            dstIp = "192.168.1.100",
            srcPort = 21,
            dstPort = 54321,
            sessionState = sessionState
        )
        val event = parser.parse(respContext)
        assertNull(event)

        val session = sessionState["ftp.session"] as FtpSessionState
        assertEquals("/home/alice", session.currentDirectory)
    }

    @Test
    fun `test login failed`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("USER alice\r\n", sessionState = sessionState))

        val respContext = buildContext(
            payload = "530 Login incorrect\r\n",
            direction = Direction.SERVER_TO_CLIENT,
            srcIp = "10.0.0.1",
            dstIp = "192.168.1.100",
            srcPort = 21,
            dstPort = 54321,
            sessionState = sessionState
        )

        val event = parser.parse(respContext) as com.netaudit.model.AuditEvent.FtpEvent
        assertEquals(530, event.responseCode)
        val session = sessionState["ftp.session"] as FtpSessionState
        assertEquals(FtpPhase.IDLE, session.phase)
    }

    @Test
    fun `test QUIT command`() {
        val event = parser.parse(buildContext("QUIT\r\n")) as com.netaudit.model.AuditEvent.FtpEvent
        assertEquals("QUIT", event.command)
    }

    @Test
    fun `test blank payload`() {
        val event = parser.parse(buildContext("  \r\n"))
        assertNull(event)
    }

    @Test
    fun `test unknown command`() {
        val event = parser.parse(buildContext("FOO bar\r\n"))
        assertNull(event)
    }

    @Test
    fun `test DELE command updates pending state`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(buildContext("DELE old.txt\r\n", sessionState = sessionState))
            as com.netaudit.model.AuditEvent.FtpEvent
        assertEquals("DELE", event.command)

        val session = sessionState["ftp.session"] as FtpSessionState
        assertEquals("DELE", session.pendingCommand)
        assertEquals("old.txt", session.pendingArgument)
    }

    @Test
    fun `test STOR command updates transfer phase`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(buildContext("STOR file.bin\r\n", sessionState = sessionState))
            as com.netaudit.model.AuditEvent.FtpEvent
        assertEquals("STOR", event.command)
        val session = sessionState["ftp.session"] as FtpSessionState
        assertEquals(FtpPhase.TRANSFER, session.phase)
    }

    @Test
    fun `test MKD command updates pending state`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(buildContext("MKD newdir\r\n", sessionState = sessionState))
            as com.netaudit.model.AuditEvent.FtpEvent
        assertEquals("MKD", event.command)
        val session = sessionState["ftp.session"] as FtpSessionState
        assertEquals("MKD", session.pendingCommand)
    }

    @Test
    fun `test response short line`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(
            buildContext(
                payload = "5",
                direction = Direction.SERVER_TO_CLIENT,
                sessionState = sessionState
            )
        )
        assertNull(event)
    }

    @Test
    fun `test response invalid code`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(
            buildContext(
                payload = "ABC Not a code\r\n",
                direction = Direction.SERVER_TO_CLIENT,
                sessionState = sessionState
            )
        )
        assertNull(event)
    }

    @Test
    fun `test transfer complete response updates phase`() {
        val sessionState = mutableMapOf<String, Any>(
            "ftp.session" to FtpSessionState(phase = FtpPhase.TRANSFER)
        )
        val event = parser.parse(
            buildContext(
                payload = "226 Transfer complete\r\n",
                direction = Direction.SERVER_TO_CLIENT,
                sessionState = sessionState
            )
        )
        assertNull(event)
        val session = sessionState["ftp.session"] as FtpSessionState
        assertEquals(FtpPhase.LOGGED_IN, session.phase)
    }

    @Test
    fun `test PWD response without quotes keeps directory`() {
        val sessionState = mutableMapOf<String, Any>(
            "ftp.session" to FtpSessionState(currentDirectory = "/prev")
        )
        val event = parser.parse(
            buildContext(
                payload = "257 /no/quotes\r\n",
                direction = Direction.SERVER_TO_CLIENT,
                sessionState = sessionState
            )
        )
        assertNull(event)
        val session = sessionState["ftp.session"] as FtpSessionState
        assertEquals("/prev", session.currentDirectory)
    }

    @Test
    fun `test non ftp text`() {
        val event = parser.parse(buildContext("HELLO"))
        assertNull(event)
    }

    private fun buildContext(
        payload: String,
        direction: Direction = Direction.CLIENT_TO_SERVER,
        srcIp: String = "192.168.1.100",
        dstIp: String = "10.0.0.1",
        srcPort: Int = 54321,
        dstPort: Int = 21,
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
