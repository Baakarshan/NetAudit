package com.netaudit.integration

import com.netaudit.config.CaptureConfig
import com.netaudit.event.AuditEventBus
import com.netaudit.model.AuditEvent
import com.netaudit.parser.ParserRegistry
import com.netaudit.parser.dns.DnsParser
import com.netaudit.parser.email.Pop3Parser
import com.netaudit.parser.email.SmtpParser
import com.netaudit.parser.ftp.FtpParser
import com.netaudit.parser.http.HttpParser
import com.netaudit.parser.telnet.TelnetParser
import com.netaudit.pipeline.AuditPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

@Tag("integration")
class PcapReplayTest {
    @Test
    fun `pcap replay produces events`() = runBlocking {
        val pcapFile = File("test-data/sample.pcap")
        assumeTrue(pcapFile.exists() && pcapFile.length() > 24, "sample.pcap missing or empty")

        val config = CaptureConfig(
            interfaceName = "eth0",
            snapshotLength = 65536,
            promiscuous = true,
            readTimeoutMs = 100,
            channelBufferSize = 4096
        )

        val eventBus = AuditEventBus()
        val registry = ParserRegistry().apply {
            register(HttpParser())
            register(FtpParser())
            register(TelnetParser())
            register(DnsParser())
            register(SmtpParser())
            register(Pop3Parser())
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pipeline = AuditPipeline(config, registry, eventBus, scope)

        val events = mutableListOf<AuditEvent>()
        val collectJob = scope.launch {
            eventBus.auditEvents.collect { events.add(it) }
        }

        pipeline.startOffline(pcapFile.absolutePath)
        withTimeoutOrNull(5000) { delay(5000) }
        pipeline.stop()
        collectJob.cancelAndJoin()
        scope.cancel()

        assertTrue(events.isNotEmpty(), "No audit events emitted from pcap replay")

        val httpEvents = events.filterIsInstance<AuditEvent.HttpEvent>()
        if (httpEvents.isNotEmpty()) {
            assertTrue(httpEvents.all { it.method.isNotBlank() && it.url.isNotBlank() && it.host.isNotBlank() })
        }

        val dnsEvents = events.filterIsInstance<AuditEvent.DnsEvent>()
        if (dnsEvents.isNotEmpty()) {
            assertTrue(dnsEvents.all { it.queryDomain.isNotBlank() })
        }
    }
}
