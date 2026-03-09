package com.netaudit.storage

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseTablesTest {
    @AfterTest
    fun cleanup() {
        transaction {
            SchemaUtils.drop(*allTables)
        }
    }

    @Test
    fun `tables are defined and creatable`() {
        Database.connect(
            url = "jdbc:h2:mem:tables;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(*allTables)
        }

        assertEquals(7, allTables.size)
        assertEquals("packets", PacketsTable.tableName)
        assertEquals("http_sessions", HttpSessionsTable.tableName)
        assertEquals("ftp_sessions", FtpSessionsTable.tableName)
        assertEquals("telnet_sessions", TelnetSessionsTable.tableName)
        assertEquals("dns_queries", DnsQueriesTable.tableName)
        assertEquals("smtp_sessions", SmtpSessionsTable.tableName)
        assertEquals("pop3_sessions", Pop3SessionsTable.tableName)
        assertTrue(allTables.toSet().contains(PacketsTable))
    }

    @Test
    fun `table columns are accessible`() {
        assertEquals("timestamp", PacketsTable.timestamp.name)
        assertEquals("protocol", PacketsTable.protocol.name)
        assertEquals("src_ip", PacketsTable.srcIp.name)
        assertEquals("dst_ip", PacketsTable.dstIp.name)
        assertEquals("src_port", PacketsTable.srcPort.name)
        assertEquals("dst_port", PacketsTable.dstPort.name)
        assertEquals("payload_size", PacketsTable.payloadSize.name)
        assertEquals("raw_data", PacketsTable.rawData.name)
        assertEquals("created_at", PacketsTable.createdAt.name)

        assertEquals("packet_id", HttpSessionsTable.packetId.name)
        assertEquals("method", HttpSessionsTable.method.name)
        assertEquals("url", HttpSessionsTable.url.name)
        assertEquals("host", HttpSessionsTable.host.name)
        assertEquals("user_agent", HttpSessionsTable.userAgent.name)
        assertEquals("status_code", HttpSessionsTable.statusCode.name)
        assertEquals("content_type", HttpSessionsTable.contentType.name)
        assertEquals("created_at", HttpSessionsTable.createdAt.name)

        assertEquals("packet_id", FtpSessionsTable.packetId.name)
        assertEquals("command", FtpSessionsTable.command.name)
        assertEquals("argument", FtpSessionsTable.argument.name)
        assertEquals("response_code", FtpSessionsTable.responseCode.name)
        assertEquals("response_message", FtpSessionsTable.responseMessage.name)
        assertEquals("created_at", FtpSessionsTable.createdAt.name)

        assertEquals("packet_id", TelnetSessionsTable.packetId.name)
        assertEquals("command", TelnetSessionsTable.command.name)
        assertEquals("data", TelnetSessionsTable.data.name)
        assertEquals("created_at", TelnetSessionsTable.createdAt.name)

        assertEquals("packet_id", DnsQueriesTable.packetId.name)
        assertEquals("query_type", DnsQueriesTable.queryType.name)
        assertEquals("domain", DnsQueriesTable.domain.name)
        assertEquals("response", DnsQueriesTable.response.name)
        assertEquals("created_at", DnsQueriesTable.createdAt.name)

        assertEquals("packet_id", SmtpSessionsTable.packetId.name)
        assertEquals("command", SmtpSessionsTable.command.name)
        assertEquals("from", SmtpSessionsTable.from.name)
        assertEquals("to", SmtpSessionsTable.to.name)
        assertEquals("subject", SmtpSessionsTable.subject.name)
        assertEquals("created_at", SmtpSessionsTable.createdAt.name)

        assertEquals("packet_id", Pop3SessionsTable.packetId.name)
        assertEquals("command", Pop3SessionsTable.command.name)
        assertEquals("argument", Pop3SessionsTable.argument.name)
        assertEquals("response", Pop3SessionsTable.response.name)
        assertEquals("created_at", Pop3SessionsTable.createdAt.name)
    }
}
