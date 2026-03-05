package com.netaudit

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.github.oshai.kotlinlogging.KotlinLogging
import com.netaudit.config.loadConfig
import com.netaudit.parser.ParserRegistry

private val logger = KotlinLogging.logger {}

fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val config = loadConfig(environment.config)
    logger.info { "NetAudit starting with config: $config" }

    // 初始化 ParserRegistry（Parser 注册在各 Spec 实现后补充）
    val registry = ParserRegistry()

    // TODO: Spec 3 → 初始化数据库
    // TODO: Spec 2 → 启动捕获引擎
    // TODO: Spec 9 → 配置路由和 SSE
}
