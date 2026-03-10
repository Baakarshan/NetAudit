package com.netaudit.coverage

import com.netaudit.model.AlertLevel
import com.netaudit.model.AuditEvent
import com.netaudit.model.Direction
import com.netaudit.model.PacketMetadata
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.model.StreamKey
import com.netaudit.model.TransportProtocol
import com.netaudit.parser.email.EmailHeaderParser
import com.netaudit.parser.email.Pop3SessionState
import com.netaudit.parser.email.SmtpSessionState
import com.netaudit.parser.ftp.FtpPhase
import com.netaudit.storage.tables.AlertsTable
import com.netaudit.storage.tables.AuditLogsTable
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelDefaultCoverageTest {
    @Test
    fun `audit event defaults and session state defaults`() {
        val ts = Instant.parse("2024-01-01T00:00:00Z")

        val ftp = AuditEvent.FtpEvent(
            id = "ftp-default",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 21,
            dstPort = 1000,
            username = "alice",
            command = "USER",
            argument = "alice",
            responseCode = 230
        )
        assertEquals(ProtocolType.FTP, ftp.protocol)
        assertEquals(AlertLevel.INFO, ftp.alertLevel)
        assertNull(ftp.currentDirectory)

        val telnet = AuditEvent.TelnetEvent(
            id = "telnet-default",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 23,
            dstPort = 1001,
            username = "root",
            commandLine = "whoami",
            direction = Direction.CLIENT_TO_SERVER
        )
        assertEquals(AlertLevel.INFO, telnet.alertLevel)

        val dns = AuditEvent.DnsEvent(
            id = "dns-default",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "8.8.8.8",
            srcPort = 5353,
            dstPort = 53,
            transactionId = 1,
            queryDomain = "example.com",
            queryType = "A",
            isResponse = false
        )
        assertTrue(dns.resolvedIps.isEmpty())
        assertNull(dns.responseTtl)

        val smtp = AuditEvent.SmtpEvent(
            id = "smtp-default",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1111,
            dstPort = 25,
            from = "alice@example.com"
        )
        assertTrue(smtp.to.isEmpty())
        assertTrue(smtp.attachmentNames.isEmpty())
        assertTrue(smtp.attachmentSizes.isEmpty())
        assertNull(smtp.stage)

        val pop3 = AuditEvent.Pop3Event(
            id = "pop3-default",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1112,
            dstPort = 110,
            username = "alice",
            command = "USER"
        )
        assertTrue(pop3.to.isEmpty())
        assertTrue(pop3.attachmentNames.isEmpty())
        assertTrue(pop3.attachmentSizes.isEmpty())
        assertNull(pop3.mailSize)

        val metadata = PacketMetadata(
            timestamp = ts,
            srcMac = "aa",
            dstMac = "bb",
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            ipProtocol = TransportProtocol.UDP,
            srcPort = 10,
            dstPort = 20,
            tcpFlags = null,
            seqNumber = null,
            ackNumber = null,
            payload = "hi".toByteArray()
        )
        val context = StreamContext(
            key = StreamKey("1.1.1.1", 10, "2.2.2.2", 20),
            metadata = metadata,
            payload = metadata.payload,
            direction = Direction.CLIENT_TO_SERVER
        )
        assertTrue(context.sessionState.isEmpty())

        val defaultHeaders = EmailHeaderParser.EmailHeaders()
        assertNull(defaultHeaders.from)
        assertTrue(defaultHeaders.to.isEmpty())

        val smtpState = SmtpSessionState()
        assertEquals(null, smtpState.from)
        assertTrue(smtpState.to.isEmpty())
        assertEquals(0, smtpState.dataBuffer.length)
        assertTrue(smtpState.attachmentNames.isEmpty())
        assertTrue(smtpState.attachmentSizes.isEmpty())

        val pop3State = Pop3SessionState()
        assertEquals(null, pop3State.username)
        assertTrue(!pop3State.inRetrMode)
        assertEquals(0, pop3State.retrBuffer.length)

        assertTrue(!FtpPhase.IDLE.isAuthenticated())
    }

    @Test
    fun `table columns expose createdAt`() {
        assertTrue(AuditLogsTable.columns.contains(AuditLogsTable.createdAt))
        assertTrue(AlertsTable.columns.contains(AlertsTable.createdAt))
    }
}
