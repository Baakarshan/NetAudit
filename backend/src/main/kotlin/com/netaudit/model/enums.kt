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
    TCP, UDP
}

@Serializable
enum class Direction {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT
}
