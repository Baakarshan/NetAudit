package com.netaudit.config

import io.ktor.server.config.*

data class DatabaseConfig(
    val url: String,
    val driver: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int
)

data class CaptureConfig(
    val interfaceName: String,
    val promiscuous: Boolean,
    val snapshotLength: Int,
    val readTimeoutMs: Int,
    val channelBufferSize: Int
)

data class AppConfig(
    val database: DatabaseConfig,
    val capture: CaptureConfig,
    val alertEnabled: Boolean
)

fun loadConfig(config: ApplicationConfig): AppConfig {
    val env = System.getenv()

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
