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

/**
 * 应用入口。
 *
 * 仅负责启动 Ktor 引擎，具体初始化逻辑在 [module] 中完成。
 */
fun main(args: Array<String>) {
    runServer(args)
}

/**
 * 运行服务器入口，支持通过系统属性关闭主入口，便于测试或嵌入式运行。
 *
 * @param args 启动参数（由 Ktor 引擎解析）
 * @param runner 实际的引擎启动函数，便于测试替换
 */
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

/**
 * Ktor 应用模块入口。
 *
 * 完成配置加载、解析器注册、数据库与仓储初始化、路由安装，
 * 以及后台任务启动（捕获、告警、批量写入）。
 *
 * @param config 运行配置，默认从环境配置读取
 * @param registry 协议解析器注册表
 * @param eventBus 审计与告警事件总线
 * @param databaseInit 数据库初始化函数
 * @param auditRepoProvider 审计仓储提供者
 * @param alertRepoProvider 告警仓储提供者
 * @param batchWriterStarter 批量写入启动器
 * @param pipelineStarter 捕获管道启动器
 * @param alertEngineStarter 告警引擎启动器
 * @param startBackground 是否启动后台任务（测试场景可关闭）
 */
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

    // 初始化解析器注册表
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

    // 告警引擎
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
        // 批量写入器
        batchWriterStarter(auditRepo, eventBus, this)

        // 启动捕获管道
        pipelineStarter(config.capture, registry, eventBus, this)
    } else {
        logger.info { "Background services skipped (startBackground=false)" }
    }
}
