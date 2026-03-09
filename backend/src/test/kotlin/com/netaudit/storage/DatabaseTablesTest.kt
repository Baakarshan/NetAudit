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
}
