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
import com.netaudit.parser.tls.TlsParser
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
    runServer(args)
}

internal fun runServer(
    args: Array<String>,
    runner: (Array<String>) -> Unit = io.ktor.server.netty.EngineMain::main
) {
    if (System.getProperty("netaudit.disableMain") == "true") {
        logger.info { "Main disabled by system property" }
        return
    }
    runner(args)
}

fun Application.module(
    config: com.netaudit.config.AppConfig = loadConfig(environment.config),
    registry: ParserRegistry = ParserRegistry(),
    eventBus: AuditEventBus = AuditEventBus(),
    databaseInit: (com.netaudit.config.DatabaseConfig) -> Unit = DatabaseFactory::init,
    auditRepoProvider: () -> com.netaudit.storage.AuditRepository = { ExposedAuditRepository() },
    alertRepoProvider: () -> com.netaudit.storage.AlertRepository = { ExposedAlertRepository() },
    batchWriterStarter: (com.netaudit.storage.AuditRepository, AuditEventBus, Application) -> Unit =
        { repo, bus, app ->
            BatchWriter(repo, bus, app).start()
        },
    pipelineStarter: (com.netaudit.config.CaptureConfig, ParserRegistry, AuditEventBus, Application) -> Unit =
        { capture, reg, bus, app ->
            AuditPipeline(capture, reg, bus, app).start()
        },
    alertEngineStarter: (AuditEventBus, com.netaudit.storage.AlertRepository, Application) -> Unit =
        { bus, repo, app ->
            AlertEngine(bus, repo, app).start()
        },
    startBackground: Boolean = true
) {
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
    registry.register(HttpParser())
    registry.register(FtpParser())
    registry.register(TelnetParser())
    registry.register(DnsParser())
    registry.register(SmtpParser())
    registry.register(Pop3Parser())
    registry.register(TlsParser())

    // 初始化数据库
    databaseInit(config.database)

    // 初始化 Repository
    val auditRepo = auditRepoProvider()
    val alertRepo = alertRepoProvider()

    // 告警引擎（Spec 9）
    if (config.alertEnabled && startBackground) {
        alertEngineStarter(eventBus, alertRepo, this)
    } else if (!config.alertEnabled) {
        logger.info { "AlertEngine disabled by config" }
    } else {
        logger.info { "AlertEngine skipped (startBackground=false)" }
    }

    // 配置路由
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "NetAudit"))
        }
        auditRoutes(auditRepo)
        alertRoutes(alertRepo)
        statsRoutes(auditRepo, alertRepo)
        sseRoutes(eventBus.auditEvents, eventBus.alertEvents)
        captureWebSocket(eventBus)
    }

    if (startBackground) {
        // 批量写入器（Spec 3）
        batchWriterStarter(auditRepo, eventBus, this)

        // 启动捕获管道（Spec 2）
        pipelineStarter(config.capture, registry, eventBus, this)
    } else {
        logger.info { "Background services skipped (startBackground=false)" }
    }
}
