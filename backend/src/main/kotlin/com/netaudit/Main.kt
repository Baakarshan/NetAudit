package com.netaudit

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.github.oshai.kotlinlogging.KotlinLogging
import com.netaudit.config.loadConfig
import com.netaudit.parser.ParserRegistry
import com.netaudit.model.AppJson
import com.netaudit.storage.DatabaseFactory
import com.netaudit.api.statsRoutes
import java.time.Duration

private val logger = KotlinLogging.logger {}

fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val config = loadConfig(environment.config)
    logger.info { "NetAudit starting with config: $config" }

    // 配置 JSON 序列化
    install(ContentNegotiation) {
        json(AppJson)
    }

    // 配置 CORS
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    // 配置 WebSocket
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // 配置异常处理
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception: ${cause.message}" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to "Internal Server Error",
                    "message" to (cause.message ?: "Unknown error")
                )
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            logger.warn { "Bad request: ${cause.message}" }
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Bad Request", "message" to cause.message)
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                mapOf("error" to "Not Found", "message" to "Resource not found")
            )
        }
    }

    // 配置路由
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "NetAudit"))
        }
        statsRoutes()
    }

    // 初始化 ParserRegistry（Parser 注册在各 Spec 实现后补充）
    val registry = ParserRegistry()

    // 初始化数据库
    DatabaseFactory.init(config.database)

    // TODO: Spec 1 → 配置 /api/stats 和 /ws/capture
    // TODO: Spec 2 → 启动捕获引擎
}
