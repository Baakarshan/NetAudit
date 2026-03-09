package com.netaudit.coverage

import com.netaudit.config.loadConfig
import com.netaudit.model.AlertLevel
import com.netaudit.model.AlertRecord
import com.netaudit.model.AlertRule
import com.netaudit.model.AuditEvent
import com.netaudit.model.Direction
import com.netaudit.model.PacketMetadata
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.model.StreamKey
import com.netaudit.model.TcpFlags
import com.netaudit.model.TransportProtocol
import com.netaudit.parser.ParserRegistry
import com.netaudit.parser.ProtocolParser
import com.netaudit.storage.allTables
import com.netaudit.storage.tables.AlertsTable
import com.netaudit.storage.tables.AuditLogsTable
import com.netaudit.storage.DnsQueriesTable
import com.netaudit.storage.FtpSessionsTable
import com.netaudit.storage.HttpSessionsTable
import com.netaudit.storage.PacketsTable
import com.netaudit.storage.Pop3SessionsTable
import com.netaudit.storage.SmtpSessionsTable
import com.netaudit.storage.TelnetSessionsTable
import io.ktor.server.config.MapApplicationConfig
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelAndConfigCoverageTest {
    @Test
    fun `enums and audit events defaults`() {
        assertTrue(ProtocolType.entries.isNotEmpty())
        assertEquals(ProtocolType.HTTP, ProtocolType.valueOf("HTTP"))
        assertTrue(AlertLevel.entries.isNotEmpty())
        assertEquals(AlertLevel.INFO, AlertLevel.valueOf("INFO"))
        assertTrue(TransportProtocol.entries.isNotEmpty())
        assertEquals(TransportProtocol.TCP, TransportProtocol.valueOf("TCP"))
        assertEquals(TransportProtocol.TCP, TransportProtocol.fromName("TCP"))
        val companion = TransportProtocol.Companion
        assertEquals(TransportProtocol.TCP, companion.fromName("TCP"))
        assertTrue(companion.toString().isNotBlank())
        assertTrue(Direction.entries.isNotEmpty())
        assertEquals(Direction.CLIENT_TO_SERVER, Direction.valueOf("CLIENT_TO_SERVER"))

        val ts = Instant.parse("2024-01-01T00:00:00Z")
        val http = AuditEvent.HttpEvent(
            id = "http-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1234,
            dstPort = 80,
            method = "GET",
            url = "http://example.com/",
            host = "example.com"
        )
        assertEquals(ProtocolType.HTTP, http.protocol)
        assertEquals(AlertLevel.INFO, http.alertLevel)

        val ftp = AuditEvent.FtpEvent(
            id = "ftp-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 21,
            dstPort = 54321,
            username = "alice",
            command = "USER",
            argument = "alice",
            responseCode = 230
        )
        assertEquals(ProtocolType.FTP, ftp.protocol)

        val telnet = AuditEvent.TelnetEvent(
            id = "telnet-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 23,
            dstPort = 1234,
            username = "root",
            commandLine = "whoami",
            direction = Direction.CLIENT_TO_SERVER
        )
        assertEquals(ProtocolType.TELNET, telnet.protocol)

        val dns = AuditEvent.DnsEvent(
            id = "dns-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "8.8.8.8",
            srcPort = 33333,
            dstPort = 53,
            transactionId = 1,
            queryDomain = "example.com",
            queryType = "A",
            isResponse = false
        )
        assertEquals(ProtocolType.DNS, dns.protocol)

        val smtp = AuditEvent.SmtpEvent(
            id = "smtp-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1111,
            dstPort = 25,
            from = "alice@example.com"
        )
        assertEquals(ProtocolType.SMTP, smtp.protocol)
        assertTrue(smtp.to.isEmpty())

        val pop3 = AuditEvent.Pop3Event(
            id = "pop3-1",
            timestamp = ts,
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1111,
            dstPort = 110,
            username = "alice",
            command = "USER"
        )
        assertEquals(ProtocolType.POP3, pop3.protocol)
        assertTrue(pop3.to.isEmpty())
    }

    @Test
    fun `stream key and context helpers`() {
        val key = StreamKey("1.1.1.1", 1000, "2.2.2.2", 80)
        val reversed = key.reverse()
        assertEquals("2.2.2.2", reversed.srcIp)
        assertEquals(80, reversed.srcPort)

        val canonical = key.canonical()
        assertEquals(key, canonical)

        val swapped = StreamKey("9.9.9.9", 9000, "1.1.1.1", 80)
        val swappedCanonical = swapped.canonical()
        assertEquals(swapped.reverse(), swappedCanonical)

        val flags = TcpFlags(syn = true, ack = false, fin = false, rst = false, psh = true)
        val metadata = PacketMetadata(
            timestamp = Instant.parse("2024-01-01T00:00:01Z"),
            srcMac = "aa",
            dstMac = "bb",
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            ipProtocol = TransportProtocol.TCP,
            srcPort = 1234,
            dstPort = 80,
            tcpFlags = flags,
            seqNumber = 1,
            ackNumber = 2,
            payload = "hi".toByteArray()
        )

        val context = StreamContext(
            key = key,
            metadata = metadata,
            payload = "hello".toByteArray(),
            direction = Direction.CLIENT_TO_SERVER
        )
        assertEquals("1.1.1.1", context.srcIp)
        assertEquals("2.2.2.2", context.dstIp)
        assertEquals(1234, context.srcPort)
        assertEquals(80, context.dstPort)
        assertEquals("hello", context.payloadAsText())
    }

    @Test
    fun `packet metadata equality and hash`() {
        val base = PacketMetadata(
            timestamp = Instant.parse("2024-01-01T00:00:02Z"),
            srcMac = "aa",
            dstMac = "bb",
            srcIp = "10.0.0.1",
            dstIp = "10.0.0.2",
            ipProtocol = TransportProtocol.UDP,
            srcPort = 1234,
            dstPort = 53,
            tcpFlags = null,
            seqNumber = null,
            ackNumber = null,
            payload = byteArrayOf(1, 2)
        )

        val sameKey = base.copy(dstIp = "10.0.0.99", payload = byteArrayOf(9))
        assertEquals(base, sameKey)
        assertEquals(base.hashCode(), sameKey.hashCode())

        val differentSeq = base.copy(seqNumber = 10)
        assertNotEquals(base, differentSeq)
        assertFalse(base.equals("not-metadata"))
    }

    @Test
    fun `alert models and registry and tables`() {
        val rule = AlertRule(
            id = "rule-1",
            name = "test",
            description = "desc",
            level = AlertLevel.WARN
        ) { event -> event is AuditEvent.HttpEvent }
        val event = AuditEvent.HttpEvent(
            id = "http-2",
            timestamp = Instant.parse("2024-01-01T00:00:03Z"),
            srcIp = "1.1.1.1",
            dstIp = "2.2.2.2",
            srcPort = 1234,
            dstPort = 80,
            method = "GET",
            url = "http://example.com",
            host = "example.com"
        )
        assertTrue(rule.condition(event))

        val record = AlertRecord(
            id = "alert-1",
            timestamp = Instant.parse("2024-01-01T00:00:04Z"),
            level = AlertLevel.INFO,
            ruleName = rule.name,
            message = "ok",
            auditEventId = event.id,
            protocol = event.protocol
        )
        assertEquals("alert-1", record.id)

        val registry = ParserRegistry()
        val testParser = object : ProtocolParser {
            override val protocolType = ProtocolType.HTTP
            override val ports = setOf(80, 8080)
            override fun parse(context: StreamContext): AuditEvent? = null
        }
        registry.register(testParser)
        assertEquals(testParser, registry.findByPort(80))
        assertEquals(testParser, registry.findByEitherPort(9999, 8080))
        assertTrue(registry.allParsers().isNotEmpty())
        assertTrue(registry.allPorts().contains(8080))

        assertTrue(PacketsTable.columns.isNotEmpty())
        assertTrue(HttpSessionsTable.columns.isNotEmpty())
        assertTrue(FtpSessionsTable.columns.isNotEmpty())
        assertTrue(TelnetSessionsTable.columns.isNotEmpty())
        assertTrue(DnsQueriesTable.columns.isNotEmpty())
        assertTrue(SmtpSessionsTable.columns.isNotEmpty())
        assertTrue(Pop3SessionsTable.columns.isNotEmpty())
        assertNotNull(AuditLogsTable.primaryKey)
        assertNotNull(AlertsTable.primaryKey)
        assertEquals(7, allTables.size)
    }

    @Test
    fun `loadConfig uses all env overrides`() {
        val config = MapApplicationConfig(
            "database.url" to "jdbc:h2:mem:cfg;DB_CLOSE_DELAY=-1",
            "database.driver" to "org.h2.Driver",
            "database.user" to "sa",
            "database.password" to "",
            "database.maxPoolSize" to "4",
            "capture.interface" to "lo",
            "capture.promiscuous" to "true",
            "capture.snapshotLength" to "65536",
            "capture.readTimeout" to "200",
            "capture.channelBufferSize" to "16",
            "alert.enabled" to "true"
        )

        val env = mapOf(
            "DATABASE_URL" to "jdbc:h2:mem:env;DB_CLOSE_DELAY=-1",
            "DATABASE_DRIVER" to "org.h2.Driver",
            "DATABASE_USER" to "env-user",
            "DATABASE_PASSWORD" to "env-pass",
            "DATABASE_MAX_POOL_SIZE" to "8",
            "CAPTURE_INTERFACE" to "eth0",
            "CAPTURE_PROMISCUOUS" to "false",
            "CAPTURE_SNAPSHOT_LENGTH" to "1024",
            "CAPTURE_READ_TIMEOUT" to "50",
            "CAPTURE_CHANNEL_BUFFER_SIZE" to "64",
            "ALERT_ENABLED" to "false"
        )

        val loaded = loadConfig(config, env)
        assertEquals("jdbc:h2:mem:env;DB_CLOSE_DELAY=-1", loaded.database.url)
        assertEquals("env-user", loaded.database.user)
        assertEquals("env-pass", loaded.database.password)
        assertEquals(8, loaded.database.maxPoolSize)
        assertEquals("eth0", loaded.capture.interfaceName)
        assertFalse(loaded.capture.promiscuous)
        assertEquals(1024, loaded.capture.snapshotLength)
        assertEquals(50, loaded.capture.readTimeoutMs)
        assertEquals(64, loaded.capture.channelBufferSize)
        assertFalse(loaded.alertEnabled)
    }
}
