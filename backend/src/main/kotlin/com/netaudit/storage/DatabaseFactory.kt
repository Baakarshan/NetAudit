package com.netaudit.storage

import com.netaudit.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * 数据库工厂类，负责初始化数据库连接池和表结构
 */
object DatabaseFactory {
    private lateinit var dataSource: HikariDataSource

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
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"

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
    private fun createTables() {
        transaction {
            logger.info { "Creating database tables if not exist..." }
            SchemaUtils.create(*allTables)
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
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
