package com.netaudit.model

import kotlinx.serialization.Serializable

@Serializable
/**
 * 应用层协议类型枚举。
 */
enum class ProtocolType {
    HTTP, FTP, TELNET, DNS, SMTP, POP3, TLS
}

@Serializable
/**
 * 告警级别枚举。
 */
enum class AlertLevel {
    INFO, WARN, CRITICAL
}

@Serializable
/**
 * 传输层协议枚举。
 */
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

@Serializable
/**
 * 流量方向枚举。
 */
enum class Direction {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT
}
