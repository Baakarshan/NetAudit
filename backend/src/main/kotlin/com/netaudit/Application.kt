package com.netaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "启动 NetAudit 网络审计系统..." }

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureRouting()
    configureSerialization()
    configureCORS()
    configureWebSockets()
    configureDatabase()

    logger.info { "NetAudit 服务已启动" }
}
