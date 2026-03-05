package com.netaudit.pipeline

import com.netaudit.config.CaptureConfig
import com.netaudit.event.AuditEventBus
import com.netaudit.parser.ParserRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AuditPipelineTest {
    @Test
    fun `test active stream count tracking`() = runTest {
        val config = CaptureConfig(
            interfaceName = "eth0",
            snapshotLength = 65536,
            promiscuous = true,
            readTimeoutMs = 100,
            channelBufferSize = 1024
        )

        val eventBus = AuditEventBus()
        val registry = ParserRegistry()
        val scope = CoroutineScope(SupervisorJob())
        val pipeline = AuditPipeline(config, registry, eventBus, scope)

        // 初始状态应该没有活跃流
        assertTrue(pipeline.activeStreams() == 0)

        scope.cancel()
    }

    // 注意：完整的集成测试需要：
    // - 在 test-data/ 目录准备 sample.pcap 文件（可在 Spec 11 提供）
    // - 注册实际的 ProtocolParser（在 Spec 4-8 实现后）
    // - 此处提供的是测试框架，实际断言需要在后续 Spec 完成后补充
}
