package com.netaudit.pipeline

import com.netaudit.capture.CaptureEngine
import com.netaudit.capture.PacketCaptureEngine
import com.netaudit.config.CaptureConfig
import com.netaudit.decode.PacketDecoder
import com.netaudit.decode.PacketDecoderLike
import com.netaudit.event.AuditEventBus
import com.netaudit.model.TransportProtocol
import com.netaudit.parser.ParserRegistry
import com.netaudit.stream.StreamTracker
import com.netaudit.stream.TcpStreamTracker
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * 审计管道编排器。
 * 组装: CaptureEngine → PacketDecoder → TcpStreamTracker → Parser → EventBus
 *
 * 启动后在 scope 中运行两个协程:
 * 1. 捕获协程 (CaptureEngine 内部)
 * 2. 解码+分发协程 (本类管理)
 *
 * @param config 抓包配置
 * @param registry 协议解析器注册表
 * @param eventBus 事件总线
 * @param scope 协程作用域
 * @param captureEngine 抓包引擎（可替换）
 * @param decoder 解码器（可替换）
 * @param streamTracker 流追踪器（可替换）
 * @param dispatcher 解码分发协程调度器
 */
class AuditPipeline(
    private val config: CaptureConfig,
    private val registry: ParserRegistry,
    private val eventBus: AuditEventBus,
    private val scope: CoroutineScope,
    private val captureEngine: CaptureEngine = PacketCaptureEngine(config, scope),
    private val decoder: PacketDecoderLike = PacketDecoder(),
    private val streamTracker: StreamTracker = TcpStreamTracker(registry, eventBus, scope),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * 启动管道（在线模式）。
     */
    fun start() {
        logger.info { "Starting audit pipeline on interface: ${config.interfaceName}" }

        // 启动 TCP 流清理定时任务
        streamTracker.startCleanupJob()

        // 启动抓包
        captureEngine.startLive()

        // 启动解码+分发协程
        scope.launch(dispatcher) {
            for (rawPacket in captureEngine.rawPacketChannel) {
                try {
                    val metadata = decoder.decode(rawPacket) ?: continue

                    when (metadata.ipProtocol) {
                        TransportProtocol.TCP -> streamTracker.handleTcpPacket(metadata)
                        TransportProtocol.UDP -> streamTracker.handleUdpPacket(metadata)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Pipeline error: ${e.message}" }
                }
            }
            logger.info { "Packet channel closed. Pipeline stopped." }
        }
    }

    /**
     * 启动管道（离线模式，用于测试）。
     *
     * @param pcapFilePath pcap 文件路径
     */
    fun startOffline(pcapFilePath: String) {
        logger.info { "Starting audit pipeline in offline mode: $pcapFilePath" }
        streamTracker.startCleanupJob()
        captureEngine.startOffline(pcapFilePath)

        scope.launch(dispatcher) {
            for (rawPacket in captureEngine.rawPacketChannel) {
                try {
                    val metadata = decoder.decode(rawPacket) ?: continue
                    when (metadata.ipProtocol) {
                        TransportProtocol.TCP -> streamTracker.handleTcpPacket(metadata)
                        TransportProtocol.UDP -> streamTracker.handleUdpPacket(metadata)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Pipeline error: ${e.message}" }
                }
            }
            logger.info { "Offline replay completed." }
        }
    }

    /**
     * 停止捕获与管道处理。
     */
    fun stop() {
        captureEngine.stop()
    }

    /**
     * 当前活跃 TCP 流数量。
     *
     * @return 活跃流数量
     */
    fun activeStreams(): Int = streamTracker.activeStreamCount()
}
