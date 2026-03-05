package com.netaudit.parser

import com.netaudit.model.ProtocolType

/**
 * 协议解析器注册中心。
 *
 * 负责：
 * 1. 管理所有协议解析器实例
 * 2. 根据端口号路由到对应解析器
 * 3. 根据协议类型获取解析器
 */
class ParserRegistry {
    private val parsersByType = mutableMapOf<ProtocolType, ProtocolParser>()
    private val parsersByPort = mutableMapOf<Int, ProtocolParser>()

    /**
     * 注册一个协议解析器
     */
    fun register(parser: ProtocolParser) {
        parsersByType[parser.protocolType] = parser
        parser.ports.forEach { port ->
            parsersByPort[port] = parser
        }
    }

    /**
     * 根据端口号获取解析器（用于自动识别协议）
     */
    fun getByPort(port: Int): ProtocolParser? = parsersByPort[port]

    /**
     * 根据协议类型获取解析器
     */
    fun getByType(type: ProtocolType): ProtocolParser? = parsersByType[type]

    /**
     * 获取所有已注册的解析器
     */
    fun getAllParsers(): List<ProtocolParser> = parsersByType.values.toList()
}
