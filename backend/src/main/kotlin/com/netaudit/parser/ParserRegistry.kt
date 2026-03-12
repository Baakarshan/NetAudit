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

    /**
     * 注册一个 Parser，自动将其所有端口映射到该 Parser。
     *
     * @param parser 协议解析器
     */
    fun register(parser: ProtocolParser) {
        allParsersList.add(parser)
        parser.ports.forEach { port ->
            portToParser[port] = parser
            logger.info { "Registered ${parser.protocolType} parser on port $port" }
        }
    }

    /**
     * 根据端口号查找对应的 Parser。
     *
     * @param port 端口号
     * @return 解析器；不存在则返回 null
     */
    fun findByPort(port: Int): ProtocolParser? = portToParser[port]

    /**
     * 根据 src 或 dst 端口查找 Parser（双向匹配）。
     *
     * @param srcPort 源端口
     * @param dstPort 目标端口
     * @return 解析器；不存在则返回 null
     */
    fun findByEitherPort(srcPort: Int, dstPort: Int): ProtocolParser? {
        return portToParser[dstPort] ?: portToParser[srcPort]
    }

    /**
     * 获取所有已注册的 Parser。
     *
     * @return 解析器列表
     */
    fun allParsers(): List<ProtocolParser> = allParsersList.toList()

    /**
     * 获取所有已注册的端口。
     *
     * @return 端口集合
     */
    fun allPorts(): Set<Int> = portToParser.keys.toSet()
}
