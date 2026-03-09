package com.netaudit.storage

import com.netaudit.config.DatabaseConfig
import com.netaudit.storage.tables.AuditLogsTable
import com.netaudit.storage.tables.AlertsTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect

private val logger = KotlinLogging.logger {}

/**
 * 数据库工厂类，负责初始化数据库连接池和表结构
 */
object DatabaseFactory {
    private lateinit var dataSource: HikariDataSource
    internal var forceSuspend: Boolean = false

    /**
     * 初始化数据库连接
     */
    fun init(config: DatabaseConfig) {
        logger.info { "Initializing database connection pool..." }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            driverClassName = config.driver
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"

            // 连接池配置
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000

            // 连接测试
            validate()
        }

        dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        logger.info { "Database connection pool initialized successfully" }

        // 创建表结构
        createTables()
    }

    /**
     * 创建数据库表
     */
    fun createTables() {
        transaction {
            logger.info { "Creating database tables if not exist..." }
            SchemaUtils.create(AuditLogsTable, AlertsTable)
            configureJsonbIfPostgres(this)
            logger.info { "Database tables created successfully" }
        }
    }

    /**
     * 关闭数据库连接池
     */
    fun close() {
        if (::dataSource.isInitialized) {
            logger.info { "Closing database connection pool..." }
            dataSource.close()
            logger.info { "Database connection pool closed" }
        }
    }

    /**
     * 在数据库事务中执行操作
     */
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) {
            if (forceSuspend) {
                yield()
            }
            block()
        }

    private fun configureJsonbIfPostgres(tx: Transaction) {
        val isPostgres = TransactionManager.current().db.dialect is PostgreSQLDialect
        configureJsonbIfPostgres(isPostgres, tx::exec)
    }

    internal fun configureJsonbIfPostgres(isPostgres: Boolean, exec: (String) -> Unit) {
        if (!isPostgres) {
            logger.debug { "Skip JSONB setup: non-PostgreSQL dialect" }
            return
        }

        exec(
            """
            ALTER TABLE audit_logs
            ALTER COLUMN details TYPE jsonb USING details::jsonb
            """.trimIndent()
        )
        exec(
            """
            CREATE INDEX IF NOT EXISTS idx_audit_details
            ON audit_logs USING GIN (details)
            """.trimIndent()
        )
        logger.info { "JSONB type and GIN index created for audit_logs.details" }
    }
}
