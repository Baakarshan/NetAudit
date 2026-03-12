package com.netaudit.model

import kotlinx.serialization.Serializable

/**
 * 应用层协议类型枚举。
 *
 * 用于审计事件分类与前端统计展示。
 */
@Serializable
enum class ProtocolType {
    HTTP, FTP, TELNET, DNS, SMTP, POP3, TLS
}

/**
 * 告警级别枚举。
 *
 * 用于告警规则匹配与 UI 呈现。
 */
@Serializable
enum class AlertLevel {
    INFO, WARN, CRITICAL
}

/**
 * 传输层协议枚举。
 */
@Serializable
enum class TransportProtocol {
    TCP, UDP;

    companion object {
        /**
         * 从名称解析协议类型。
         *
         * @param value 枚举名称
         */
        fun fromName(value: String): TransportProtocol = valueOf(value)
    }
}

/**
 * 流量方向枚举。
 *
 * 用于区分“客户端→服务端”与“服务端→客户端”的上下文。
 */
@Serializable
enum class Direction {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT
}
