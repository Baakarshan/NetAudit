package com.netaudit

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.http.*
import io.github.oshai.kotlinlogging.KotlinLogging
import com.netaudit.config.loadConfig
import com.netaudit.parser.ParserRegistry
import com.netaudit.parser.http.HttpParser
import com.netaudit.parser.ftp.FtpParser
import com.netaudit.parser.telnet.TelnetParser
import com.netaudit.parser.dns.DnsParser
import com.netaudit.parser.email.SmtpParser
import com.netaudit.parser.email.Pop3Parser
import com.netaudit.model.AppJson
import com.netaudit.storage.DatabaseFactory
import com.netaudit.storage.BatchWriter
import com.netaudit.storage.impl.ExposedAlertRepository
import com.netaudit.storage.impl.ExposedAuditRepository
import com.netaudit.api.statsRoutes
import com.netaudit.api.auditRoutes
import com.netaudit.api.alertRoutes
import com.netaudit.api.sseRoutes
import com.netaudit.api.captureWebSocket
import com.netaudit.api.configurePlugins
import com.netaudit.alert.AlertEngine
import com.netaudit.event.AuditEventBus
import com.netaudit.pipeline.AuditPipeline
import java.time.Duration

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val config = loadConfig(environment.config)
    logger.info { "NetAudit starting with config: $config" }

    // 配置插件
    configurePlugins()

    // 配置 WebSocket
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // 初始化 ParserRegistry（Parser 注册在各 Spec 实现后补充）
    val registry = ParserRegistry()
    registry.register(HttpParser())
    registry.register(FtpParser())
    registry.register(TelnetParser())
    registry.register(DnsParser())
    registry.register(SmtpParser())
    registry.register(Pop3Parser())

    // 初始化事件总线
    val eventBus = AuditEventBus()

    // 初始化数据库
    DatabaseFactory.init(config.database)

    // 初始化 Repository
    val auditRepo = ExposedAuditRepository(AppJson)
    val alertRepo = ExposedAlertRepository()

    // 告警引擎（Spec 9）
    if (config.alertEnabled) {
        val alertEngine = AlertEngine(eventBus, alertRepo, this)
        alertEngine.start()
    } else {
        logger.info { "AlertEngine disabled by config" }
    }

    // 配置路由
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "NetAudit"))
        }
        auditRoutes(auditRepo)
        alertRoutes(alertRepo)
        statsRoutes(auditRepo, alertRepo)
        sseRoutes(eventBus)
        captureWebSocket(eventBus)
    }

    // 批量写入器（Spec 3）
    val batchWriter = BatchWriter(auditRepo, eventBus, this)
    batchWriter.start()

    // 启动捕获管道（Spec 2）
    val pipeline = AuditPipeline(config.capture, registry, eventBus, this)
    pipeline.start()
}
