package com.netaudit.parser.telnet

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

class TelnetParserTest {
    private val parser = TelnetParser()

    @Test
    fun `test simple command`() {
        val context = buildContext("ls -la\r\n".toByteArray())
        val event = parser.parse(context) as com.netaudit.model.AuditEvent.TelnetEvent
        assertEquals("ls -la", event.commandLine)
    }

    @Test
    fun `test username detection`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("login: ".toByteArray(), Direction.SERVER_TO_CLIENT, sessionState))
        val userEvent = parser.parse(buildContext("admin\r\n".toByteArray(), Direction.CLIENT_TO_SERVER, sessionState))
        assertNull(userEvent)

        val commandEvent = parser.parse(buildContext("whoami\r\n".toByteArray(), Direction.CLIENT_TO_SERVER, sessionState))
            as com.netaudit.model.AuditEvent.TelnetEvent
        assertEquals("admin", commandEvent.username)
        assertEquals("whoami", commandEvent.commandLine)
    }

    @Test
    fun `test password not recorded`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("password: ".toByteArray(), Direction.SERVER_TO_CLIENT, sessionState))
        val event = parser.parse(buildContext("secret123\r\n".toByteArray(), Direction.CLIENT_TO_SERVER, sessionState))
        assertNull(event)
    }

    @Test
    fun `test IAC filtering`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFD.toByte(), 0x18.toByte()) + "ls\r\n".toByteArray()
        val event = parser.parse(buildContext(bytes)) as com.netaudit.model.AuditEvent.TelnetEvent
        assertEquals("ls", event.commandLine)
    }

    @Test
    fun `test byte by byte accumulation`() {
        val sessionState = mutableMapOf<String, Any>()
        assertNull(parser.parse(buildContext("l".toByteArray(), Direction.CLIENT_TO_SERVER, sessionState)))
        assertNull(parser.parse(buildContext("s".toByteArray(), Direction.CLIENT_TO_SERVER, sessionState)))
        val event = parser.parse(buildContext("\r\n".toByteArray(), Direction.CLIENT_TO_SERVER, sessionState))
            as com.netaudit.model.AuditEvent.TelnetEvent
        assertEquals("ls", event.commandLine)
    }

    @Test
    fun `test empty payload`() {
        val event = parser.parse(buildContext(byteArrayOf()))
        assertNull(event)
    }

    @Test
    fun `test pure IAC`() {
        val event = parser.parse(buildContext(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x01.toByte())))
        assertNull(event)
    }

    @Test
    fun `test blank command line`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(buildContext("\r\n".toByteArray(), Direction.CLIENT_TO_SERVER, sessionState))
        assertNull(event)
        val buffer = sessionState["telnet.inputBuffer"] as StringBuilder
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `test username prompt variant`() {
        val sessionState = mutableMapOf<String, Any>()
        parser.parse(buildContext("username: ".toByteArray(), Direction.SERVER_TO_CLIENT, sessionState))
        val event = parser.parse(buildContext("bob\r\n".toByteArray(), Direction.CLIENT_TO_SERVER, sessionState))
        assertNull(event)

        val commandEvent = parser.parse(buildContext("id\r\n".toByteArray(), Direction.CLIENT_TO_SERVER, sessionState))
            as com.netaudit.model.AuditEvent.TelnetEvent
        assertEquals("bob", commandEvent.username)
    }

    @Test
    fun `test server buffer trimming`() {
        val sessionState = mutableMapOf<String, Any>()
        val longText = "x".repeat(300).toByteArray()
        val event = parser.parse(buildContext(longText, Direction.SERVER_TO_CLIENT, sessionState))
        assertNull(event)
        val buffer = sessionState["telnet.serverBuffer"] as StringBuilder
        assertTrue(buffer.length <= 256)
    }

    @Test
    fun `test server data with only IAC`() {
        val sessionState = mutableMapOf<String, Any>()
        val event = parser.parse(buildContext(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x01.toByte()), Direction.SERVER_TO_CLIENT, sessionState))
        assertNull(event)
    }

    @Test
    fun `test IAC subnegotiation and literal`() {
        val sessionState = mutableMapOf<String, Any>()
        val payload = byteArrayOf(
            0xFF.toByte(), 0xFA.toByte(), 0x01.toByte(), 0x02.toByte(),
            0xFF.toByte(), 0xF0.toByte(),
            0xFF.toByte(), 0xFF.toByte()
        ) + "ls\r\n".toByteArray()
        val event = parser.parse(buildContext(payload, Direction.CLIENT_TO_SERVER, sessionState))
            as com.netaudit.model.AuditEvent.TelnetEvent
        assertTrue(event.commandLine.contains("ls"))
    }

    private fun buildContext(
        payload: ByteArray,
        direction: Direction = Direction.CLIENT_TO_SERVER,
        sessionState: MutableMap<String, Any> = mutableMapOf()
    ): StreamContext {
        val metadata = PacketMetadata(
            timestamp = Clock.System.now(),
            srcMac = "00:00:00:00:00:01",
            dstMac = "00:00:00:00:00:02",
            srcIp = if (direction == Direction.CLIENT_TO_SERVER) "192.168.1.100" else "10.0.0.1",
            dstIp = if (direction == Direction.CLIENT_TO_SERVER) "10.0.0.1" else "192.168.1.100",
            ipProtocol = TransportProtocol.TCP,
            srcPort = if (direction == Direction.CLIENT_TO_SERVER) 54321 else 23,
            dstPort = if (direction == Direction.CLIENT_TO_SERVER) 23 else 54321,
            tcpFlags = TcpFlags(false, false, false, false, false),
            seqNumber = 1,
            ackNumber = 1,
            payload = payload
        )

        return StreamContext(
            key = com.netaudit.model.StreamKey(metadata.srcIp, metadata.srcPort, metadata.dstIp, metadata.dstPort),
            metadata = metadata,
            payload = payload,
            direction = direction,
            sessionState = sessionState
        )
    }
}
