package com.netaudit.coverage

import com.netaudit.parser.email.EmailHeaderParser
import com.netaudit.parser.email.SmtpPhase
import com.netaudit.parser.email.SmtpSessionState
import com.netaudit.parser.email.smtpPhaseMarker
import com.netaudit.parser.ftp.FtpPhase
import com.netaudit.parser.ftp.FtpSessionState
import com.netaudit.parser.ftp.ftpPhaseMarker
import com.netaudit.stream.PacketBuffer
import com.netaudit.stream.TcpStreamBuffer
import com.netaudit.model.StreamKey
import kotlinx.datetime.Instant
import kotlinx.coroutines.test.runTest
import org.pcap4j.packet.UnknownPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiscCoverageTest {
    @Test
    fun `instantiate small data holders`() {
        val headers = EmailHeaderParser.EmailHeaders(from = "alice@example.com")
        assertEquals("alice@example.com", headers.from)

        val smtpState = SmtpSessionState(phase = SmtpPhase.GREETED)
        assertEquals(SmtpPhase.GREETED, smtpState.phase)
        assertTrue(SmtpPhase.values().isNotEmpty())
        assertTrue(SmtpPhase.entries.isNotEmpty())
        assertEquals(SmtpPhase.CONNECTED, SmtpSessionState().phase)
        assertEquals(SmtpPhase.CONNECTED, SmtpPhase.valueOf("CONNECTED"))
        assertTrue(SmtpPhase.COMPLETED.isTerminal())
        assertTrue(smtpPhaseMarker > 0)

        val ftpState = FtpSessionState(phase = FtpPhase.LOGGED_IN)
        assertEquals(FtpPhase.LOGGED_IN, ftpState.phase)
        assertTrue(FtpPhase.values().isNotEmpty())
        assertTrue(FtpPhase.entries.isNotEmpty())
        assertEquals(FtpPhase.IDLE, FtpSessionState().phase)
        assertEquals(FtpPhase.IDLE, FtpPhase.valueOf("IDLE"))
        assertTrue(FtpPhase.LOGGED_IN.isAuthenticated())
        assertTrue(ftpPhaseMarker > 0)
    }

    @Test
    fun `packet buffer and tcp stream buffer basic usage`() = runTest {
        val buffer = PacketBuffer(capacity = 1)
        val defaultBuffer = PacketBuffer()
        val packet = UnknownPacket.Builder().rawData(byteArrayOf(1)).build()
        buffer.add(packet)
        assertEquals(1, buffer.getRecent(1).size)
        assertTrue(buffer.getRecent(0).isEmpty())
        assertTrue(defaultBuffer.getRecent(0).isEmpty())

        val key = StreamKey("1.1.1.1", 1234, "2.2.2.2", 80)
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val tcpBuffer = TcpStreamBuffer(key, nowProvider = { now })
        tcpBuffer.appendClientData("hi".toByteArray())
        tcpBuffer.appendServerData("ok".toByteArray())
        assertEquals("hi", tcpBuffer.consumeClientData())
        assertEquals("ok", tcpBuffer.consumeServerData())
        assertTrue(tcpBuffer.isExpired(timeoutSeconds = -1))

        val defaultTcpBuffer = TcpStreamBuffer(key)
        defaultTcpBuffer.appendClientData("ping".toByteArray())
        assertEquals("ping", defaultTcpBuffer.consumeClientData())
        defaultTcpBuffer.isExpired()
    }
}
