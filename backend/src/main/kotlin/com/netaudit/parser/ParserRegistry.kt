package com.netaudit.parser

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 端口 → 解析器 的注册表。
 * 注册表模式：O(1) 路由分发。
 */
class ParserRegistry {
    private val portToParser = mutableMapOf<Int, ProtocolParser>()
    private val allParsersList = mutableListOf<ProtocolParser>()

    /** 注册一个 Parser，自动将其所有端口映射到该 Parser */
    fun register(parser: ProtocolParser) {
        allParsersList.add(parser)
        parser.ports.forEach { port ->
            portToParser[port] = parser
            logger.info { "Registered ${parser.protocolType} parser on port $port" }
        }
    }

    /** 根据端口号查找对应的 Parser */
    fun findByPort(port: Int): ProtocolParser? = portToParser[port]

    /** 根据 src 或 dst 端口查找 Parser（双向匹配） */
    fun findByEitherPort(srcPort: Int, dstPort: Int): ProtocolParser? {
        return portToParser[dstPort] ?: portToParser[srcPort]
    }

    /** 获取所有已注册的 Parser */
    fun allParsers(): List<ProtocolParser> = allParsersList.toList()

    /** 获取所有已注册的端口 */
    fun allPorts(): Set<Int> = portToParser.keys.toSet()
}
