package com.netaudit.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * 应用内统一使用的 JSON 序列化配置。
 *
 * - 使用 `protocol` 作为多态判别字段
 * - 忽略未知字段，便于版本兼容
 * - 保持默认值输出，前端可直接消费
 */
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
            subclass(AuditEvent.TlsEvent::class)
        }
    }
}
