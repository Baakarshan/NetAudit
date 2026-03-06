package com.netaudit.parser

import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext

/**
 * 协议解析器统一接口 — 策略模式。
 *
 * 每个实现类负责一种应用层协议的解析。
 * 输入: StreamContext (含 payload + 元数据 + 会话状态)
 * 输出: AuditEvent? (解析出审计事件则返回，否则 null)
 */
interface ProtocolParser {
    /** 协议类型标识 */
    val protocolType: ProtocolType

    /** 该协议使用的知名端口集合（用于路由分发） */
    val ports: Set<Int>

    /**
     * 解析一次应用层交互。
     * 对于无状态协议（HTTP/DNS），每次调用可直接返回事件。
     * 对于有状态协议（FTP/TELNET/SMTP/POP3），可能需要多次调用积累状态后才返回事件。
     *
     * @return 解析出的审计事件，如果当前 payload 不足以产生完整事件则返回 null
     */
    fun parse(context: StreamContext): AuditEvent?
}
