package com.netaudit.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val AppJson = Json {
    classDiscriminator = "protocol"
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
    serializersModule = SerializersModule {
        polymorphic(AuditEvent::class) {
            subclass(AuditEvent.HttpEvent::class)
            subclass(AuditEvent.FtpEvent::class)
            subclass(AuditEvent.TelnetEvent::class)
            subclass(AuditEvent.DnsEvent::class)
            subclass(AuditEvent.SmtpEvent::class)
            subclass(AuditEvent.Pop3Event::class)
        }
    }
}
