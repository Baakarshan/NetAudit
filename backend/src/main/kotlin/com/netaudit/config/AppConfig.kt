package com.netaudit.config

import io.ktor.server.config.*

/**
 * 数据库连接配置。
 *
 * 约定：优先读取环境变量覆盖配置文件，适配本地与容器化运行场景。
 */
data class DatabaseConfig(
    val url: String,
    val driver: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int
)

/**
 * 抓包配置。
 *
 * interfaceName 在 Windows 通常是 Npcap 设备名（\\Device\\NPF_{GUID}）。
 */
data class CaptureConfig(
    val interfaceName: String,
    val promiscuous: Boolean,
    val snapshotLength: Int,
    val readTimeoutMs: Int,
    val channelBufferSize: Int
)

/**
 * 应用级配置聚合。
 */
data class AppConfig(
    val database: DatabaseConfig,
    val capture: CaptureConfig,
    val alertEnabled: Boolean
)

/**
 * 从 HOCON 与环境变量加载配置。
 *
 * 优先级：环境变量 > application.conf。
 * 这样可以在不改配置文件的情况下复用同一镜像或构建产物。
 */
fun loadConfig(config: ApplicationConfig, env: Map<String, String> = System.getenv()): AppConfig {

    val databaseUrl = env["DATABASE_URL"] ?: config.property("database.url").getString()
    val databaseDriver = env["DATABASE_DRIVER"] ?: config.property("database.driver").getString()
    val databaseUser = env["DATABASE_USER"] ?: config.property("database.user").getString()
    val databasePassword = env["DATABASE_PASSWORD"] ?: config.property("database.password").getString()
    val databasePoolSize = env["DATABASE_MAX_POOL_SIZE"]?.toIntOrNull()
        ?: config.property("database.maxPoolSize").getString().toInt()

    val captureInterface = env["CAPTURE_INTERFACE"] ?: config.property("capture.interface").getString()
    val capturePromiscuous = env["CAPTURE_PROMISCUOUS"]?.toBoolean()
        ?: config.property("capture.promiscuous").getString().toBoolean()
    val captureSnapshot = env["CAPTURE_SNAPSHOT_LENGTH"]?.toIntOrNull()
        ?: config.property("capture.snapshotLength").getString().toInt()
    val captureReadTimeout = env["CAPTURE_READ_TIMEOUT"]?.toIntOrNull()
        ?: config.property("capture.readTimeout").getString().toInt()
    val captureChannelBuffer = env["CAPTURE_CHANNEL_BUFFER_SIZE"]?.toIntOrNull()
        ?: config.property("capture.channelBufferSize").getString().toInt()

    val alertEnabled = env["ALERT_ENABLED"]?.toBoolean()
        ?: config.property("alert.enabled").getString().toBoolean()

    return AppConfig(
        database = DatabaseConfig(
            url = databaseUrl,
            driver = databaseDriver,
            user = databaseUser,
            password = databasePassword,
            maxPoolSize = databasePoolSize
        ),
        capture = CaptureConfig(
            interfaceName = captureInterface,
            promiscuous = capturePromiscuous,
            snapshotLength = captureSnapshot,
            readTimeoutMs = captureReadTimeout,
            channelBufferSize = captureChannelBuffer
        ),
        alertEnabled = alertEnabled
    )
}
