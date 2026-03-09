package com.netaudit.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolType {
    HTTP, FTP, TELNET, DNS, SMTP, POP3
}

@Serializable
enum class AlertLevel {
    INFO, WARN, CRITICAL
}

@Serializable
enum class TransportProtocol {
    TCP, UDP;

    companion object {
        fun fromName(value: String): TransportProtocol = valueOf(value)
    }
}

@Serializable
enum class Direction {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT
}
