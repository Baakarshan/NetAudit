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
    return AppConfig(
        database = DatabaseConfig(
            url = config.property("database.url").getString(),
            driver = config.property("database.driver").getString(),
            user = config.property("database.user").getString(),
            password = config.property("database.password").getString(),
            maxPoolSize = config.property("database.maxPoolSize").getString().toInt()
        ),
        capture = CaptureConfig(
            interfaceName = config.property("capture.interface").getString(),
            promiscuous = config.property("capture.promiscuous").getString().toBoolean(),
            snapshotLength = config.property("capture.snapshotLength").getString().toInt(),
            readTimeoutMs = config.property("capture.readTimeout").getString().toInt(),
            channelBufferSize = config.property("capture.channelBufferSize").getString().toInt()
        ),
        alertEnabled = config.property("alert.enabled").getString().toBoolean()
    )
}
