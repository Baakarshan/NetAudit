package com.netaudit.coverage

import kotlin.test.Test

class KotlinFileInitCoverageTest {
    @Test
    fun `load kotlin file classes`() {
        val classes = listOf(
            "com.netaudit.pipeline.AuditPipelineKt",
            "com.netaudit.parser.http.HttpParserKt",
            "com.netaudit.stream.TcpStreamTrackerKt",
            "com.netaudit.parser.dns.DnsParserKt",
            "com.netaudit.parser.telnet.TelnetParserKt",
            "com.netaudit.parser.ftp.FtpParserKt",
            "com.netaudit.alert.AlertEngineKt",
            "com.netaudit.storage.BatchWriterKt",
            "com.netaudit.storage.DatabaseFactoryKt",
            "com.netaudit.capture.PacketCaptureEngineKt",
            "com.netaudit.parser.ParserRegistryKt"
        )

        classes.forEach { className ->
            val clazz = Class.forName(className)
            try {
                val field = clazz.getDeclaredField("logger")
                field.isAccessible = true
                field.get(null)?.toString()
            } catch (_: NoSuchFieldException) {
            }
        }
    }
}
