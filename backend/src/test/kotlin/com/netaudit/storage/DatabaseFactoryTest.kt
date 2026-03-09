package com.netaudit.storage

import com.netaudit.config.DatabaseConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseFactoryTest {
    @Test
    fun `init creates tables and dbQuery works`() = runTest {
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
            maxPoolSize = 3
        )

        DatabaseFactory.init(config)
        val result = DatabaseFactory.dbQuery { 42 }
        assertEquals(42, result)
        DatabaseFactory.close()
    }

    @Test
    fun `configureJsonbIfPostgres branches`() {
        var executed = false
        DatabaseFactory.configureJsonbIfPostgres(isPostgres = false) {
            executed = true
        }
        assertTrue(!executed)

        val statements = mutableListOf<String>()
        DatabaseFactory.configureJsonbIfPostgres(isPostgres = true) { sql ->
            statements.add(sql.trim())
        }
        assertEquals(2, statements.size)
        assertTrue(statements[0].startsWith("ALTER TABLE audit_logs"))
        assertTrue(statements[1].startsWith("CREATE INDEX IF NOT EXISTS idx_audit_details"))
    }

    @Test
    fun `close without init is safe`() {
        val field = DatabaseFactory::class.java.getDeclaredField("dataSource")
        field.isAccessible = true
        field.set(DatabaseFactory, null)

        DatabaseFactory.close()
        assertTrue(true)
    }
}
