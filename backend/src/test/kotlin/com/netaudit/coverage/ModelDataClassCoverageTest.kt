package com.netaudit.coverage

import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord
import com.netaudit.model.AuditEvent
import com.netaudit.model.Direction
import com.netaudit.model.PacketMetadata
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.model.StreamKey
import com.netaudit.model.TcpFlags
import com.netaudit.model.TransportProtocol
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ModelDataClassCoverageTest {
    @Test
    fun `audit event data classes support copy and components`() {
        val ts = Instant.parse("2024-01-01T00:00:00Z")
        val http = AuditEvent.HttpEvent(
            id = "http-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1000,
            dstPort = 80,
            method = "GET",
            url = "http://example.com/",
            host = "example.com"
        )
        assertEquals("http-1", http.component1())
        assertEquals(ts, http.component2())
        assertEquals("1.1.1.1", http.component3())
        assertEquals("2.2.2.2", http.component4())
        assertEquals(1000, http.component5())
        assertEquals(80, http.component6())
        assertEquals(ProtocolType.HTTP, http.component7())
        assertEquals(AlertLevel.INFO, http.component8())
        assertEquals("GET", http.component9())
        assertEquals("http://example.com/", http.component10())
        assertEquals("example.com", http.component11())
        assertEquals(http, http.copy())
        assertTrue(http.toString().contains("HttpEvent"))

        val ftp = AuditEvent.FtpEvent(
            id = "ftp-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 21,
            dstPort = 1001,
            username = "alice",
            command = "USER",
            argument = "alice",
            responseCode = 230,
            currentDirectory = "/"
        )
        assertEquals("ftp-1", ftp.component1())
        assertEquals("alice", ftp.component9())
        assertEquals(ftp, ftp.copy())

        val telnet = AuditEvent.TelnetEvent(
            id = "telnet-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 23,
            dstPort = 1002,
            username = "root",
            commandLine = "whoami",
            direction = Direction.CLIENT_TO_SERVER
        )
        assertEquals("telnet-1", telnet.component1())
        assertEquals(ts, telnet.component2())
        assertEquals("1.1.1.1", telnet.component3())
        assertEquals("2.2.2.2", telnet.component4())
        assertEquals(23, telnet.component5())
        assertEquals(1002, telnet.component6())
        assertEquals(ProtocolType.TELNET, telnet.component7())
        assertEquals(AlertLevel.INFO, telnet.component8())
        assertEquals("root", telnet.component9())
        assertEquals("whoami", telnet.component10())
        assertEquals(Direction.CLIENT_TO_SERVER, telnet.component11())
        assertEquals(telnet, telnet.copy())

        val dns = AuditEvent.DnsEvent(
            id = "dns-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "8.8.8.8",
            srcPort = 1003,
            dstPort = 53,
            transactionId = 1,
            queryDomain = "example.com",
            queryType = "A",
            isResponse = true,
            resolvedIps = listOf("93.184.216.34"),
            responseTtl = 60
        )
        assertEquals("dns-1", dns.component1())
        assertEquals(ts, dns.component2())
        assertEquals("1.1.1.1", dns.component3())
        assertEquals("8.8.8.8", dns.component4())
        assertEquals(1003, dns.component5())
        assertEquals(53, dns.component6())
        assertEquals(ProtocolType.DNS, dns.component7())
        assertEquals(AlertLevel.INFO, dns.component8())
        assertEquals(1, dns.component9())
        assertEquals("example.com", dns.component10())
        assertEquals("A", dns.component11())
        assertEquals(true, dns.component12())
        assertEquals(listOf("93.184.216.34"), dns.component13())
        assertEquals(60, dns.component14())
        assertEquals(dns, dns.copy())

        val smtp = AuditEvent.SmtpEvent(
            id = "smtp-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1004,
            dstPort = 25,
            from = "alice@example.com",
            to = listOf("bob@example.com"),
            subject = "hi",
            attachmentNames = listOf("a.txt"),
            attachmentSizes = listOf(10),
            stage = "DATA"
        )
        assertEquals("smtp-1", smtp.component1())
        assertEquals(ts, smtp.component2())
        assertEquals("1.1.1.1", smtp.component3())
        assertEquals("2.2.2.2", smtp.component4())
        assertEquals(1004, smtp.component5())
        assertEquals(25, smtp.component6())
        assertEquals(ProtocolType.SMTP, smtp.component7())
        assertEquals(AlertLevel.INFO, smtp.component8())
        assertEquals("alice@example.com", smtp.component9())
        assertEquals(listOf("bob@example.com"), smtp.component10())
        assertEquals("hi", smtp.component11())
        assertEquals(listOf("a.txt"), smtp.component12())
        assertEquals(listOf(10), smtp.component13())
        assertEquals("DATA", smtp.component14())
        assertEquals(smtp, smtp.copy())

        val pop3 = AuditEvent.Pop3Event(
            id = "pop3-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 110,
            dstPort = 1005,
            username = "alice",
            command = "LIST",
            from = "a@example.com",
            to = listOf("b@example.com"),
            subject = "sub",
            attachmentNames = listOf("b.pdf"),
            attachmentSizes = listOf(20),
            mailSize = 100
        )
        assertEquals("pop3-1", pop3.component1())
        assertEquals(ts, pop3.component2())
        assertEquals("1.1.1.1", pop3.component3())
        assertEquals("2.2.2.2", pop3.component4())
        assertEquals(110, pop3.component5())
        assertEquals(1005, pop3.component6())
        assertEquals(ProtocolType.POP3, pop3.component7())
        assertEquals(AlertLevel.INFO, pop3.component8())
        assertEquals("alice", pop3.component9())
        assertEquals("LIST", pop3.component10())
        assertEquals("a@example.com", pop3.component11())
        assertEquals(listOf("b@example.com"), pop3.component12())
        assertEquals("sub", pop3.component13())
        assertEquals(listOf("b.pdf"), pop3.component14())
        assertEquals(listOf(20), pop3.component15())
        assertEquals(100, pop3.component16())
        assertEquals(pop3, pop3.copy())
    }

    @Test
    fun `packet metadata and tcp flags components`() {
        val ts = Instant.parse("2024-01-01T00:00:00Z")
        val flags = TcpFlags(syn = true, ack = false, fin = false, rst = false, psh = true)
        assertEquals(true, flags.component1())
        assertEquals(false, flags.component2())
        assertEquals(false, flags.component3())
        assertEquals(false, flags.component4())
        assertEquals(true, flags.component5())
        assertEquals(true, flags.psh)
        assertNotEquals(flags, flags.copy(psh = false))

        val metadata = PacketMetadata(
            timestamp = ts,
            srcMac = "aa",
            dstMac = "bb",
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            ipProtocol = TransportProtocol.TCP,
            srcPort = 1,
            dstPort = 2,
            tcpFlags = flags,
            seqNumber = 1,
            ackNumber = 2,
            payload = byteArrayOf(1, 2, 3)
        )
        assertEquals(ts, metadata.component1())
        assertEquals("aa", metadata.component2())
        assertEquals("bb", metadata.component3())
        assertEquals("1.1.1.1", metadata.component4())
        assertEquals("2.2.2.2", metadata.component5())
        assertEquals(TransportProtocol.TCP, metadata.component6())
        assertEquals(1, metadata.component7())
        assertEquals(2, metadata.component8())
        assertEquals(flags, metadata.component9())
        assertEquals(1L, metadata.component10())
        assertEquals(2L, metadata.component11())
        assertEquals(3, metadata.component12().size)
        assertEquals("aa", metadata.srcMac)
        assertEquals("bb", metadata.dstMac)
        assertEquals(1L, metadata.seqNumber)
        assertEquals(2L, metadata.ackNumber)
        assertEquals(metadata, metadata.copy(srcMac = "aa"))
        assertTrue(metadata.toString().contains("PacketMetadata"))
    }

    @Test
    fun `stream context accessors and alert record`() {
        val ts = Instant.parse("2024-01-01T00:00:00Z")
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
        assertEquals("1.1.1.1", context.srcIp)
        assertEquals("2.2.2.2", context.dstIp)
        assertEquals(10, context.srcPort)
        assertEquals(20, context.dstPort)
        assertEquals(ts, context.timestamp)
        assertEquals("hi", context.payloadAsText())
        assertEquals(Direction.CLIENT_TO_SERVER, context.direction)
        assertTrue(context.sessionState.isEmpty())

        val alert = AlertRecord(
            id = "alert-1",
            timestamp = ts,
            level = AlertLevel.WARN,
            ruleName = "r1",
            message = "m",
            auditEventId = "event-1",
            protocol = ProtocolType.HTTP
        )
        assertEquals("alert-1", alert.component1())
        assertEquals(AlertLevel.WARN, alert.component3())
        assertEquals(alert, alert.copy())
    }
}
