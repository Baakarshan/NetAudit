package com.netaudit.capture

import com.netaudit.config.CaptureConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.pcap4j.core.PcapHandle
import org.pcap4j.core.PcapNativeException
import org.pcap4j.core.PcapNetworkInterface
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode
import org.pcap4j.core.Pcaps
import org.pcap4j.packet.Packet

private val logger = KotlinLogging.logger {}

/**
 * 包捕获引擎。
 *
 * 核心职责：
 * - 在线抓包或离线回放，并将 `Packet` 推送到 `rawPacketChannel`。
 * - 在高吞吐下保持稳定（使用 trySend 避免阻塞抓包线程）。
 *
 * 线程模型：
 * - 抓包循环运行在独立协程中。
 * - `stop()` 可安全中断循环并释放底层资源。
 */
class PacketCaptureEngine(
    private val config: CaptureConfig,
    private val scope: CoroutineScope,
    private val sourceFactory: PacketSourceFactory = PcapPacketSourceFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CaptureEngine {
    override val rawPacketChannel = Channel<Packet>(capacity = config.channelBufferSize)

    private var source: PacketSource? = null
    @Volatile private var running = false
    @Volatile private var offlineMode = false
    private var capturedCount = 0L
    private var droppedCount = 0L
    // 仅用于测试注入异常/覆盖边界，不参与业务逻辑
    internal var afterSendHook: ((Packet) -> Unit)? = null

    /**
     * 启动在线抓包。
     *
     * 注意：重复调用会被忽略（已有运行实例）。
     */
    override fun startLive() {
        if (running) {
            logger.warn { "Capture engine already running" }
            return
        }
        offlineMode = false

        try {
            source = sourceFactory.openLive(config)

            logger.info {
                "Capture started on interface=${config.interfaceName}, " +
                "promiscuous=${config.promiscuous}, " +
                "bufferSize=${config.channelBufferSize}"
            }

            running = true
            scope.launch(ioDispatcher) {
                captureLoop()
            }
        } catch (e: PcapNativeException) {
            logger.error(e) { "Failed to open capture device: ${e.message}" }
            throw e
        }
    }

    /**
     * 从 pcap 文件读取（测试用）。
     *
     * 读完文件后会触发 `stop()`，并关闭通道。
     */
    override fun startOffline(pcapFilePath: String) {
        if (running) {
            logger.warn { "Capture engine already running" }
            return
        }

        offlineMode = true
        try {
            source = sourceFactory.openOffline(pcapFilePath)
            logger.info { "Offline capture started from file: $pcapFilePath" }

            running = true
            scope.launch(ioDispatcher) {
                captureLoop()
                logger.info { "Offline capture completed." }
            }
        } catch (e: PcapNativeException) {
            logger.error(e) { "Failed to open pcap file: ${e.message}" }
            throw e
        }
    }

    /**
     * 停止抓包并释放资源。
     *
     * 若已停止，则无副作用。
     */
    override fun stop() {
        if (!running) {
            return
        }

        running = false
        source?.close()
        rawPacketChannel.close()

        logger.info {
            "Capture stopped. Total captured: $capturedCount, dropped: $droppedCount"
        }
    }

    private fun captureLoop() {
        val currentSource = source ?: return

        try {
            while (running) {
                val packet = try {
                    currentSource.nextPacket()
                } catch (e: Exception) {
                    logger.error(e) { "Error reading packet: ${e.message}" }
                    break
                }

                // getNextPacket() 返回 null 时表示超时，继续循环
                if (packet == null) {
                    if (offlineMode) {
                        logger.info { "Offline capture reached end of file." }
                        stop()
                        return
                    }
                    continue
                }

                capturedCount++

                // 使用 trySend 避免阻塞抓包线程
                val result = rawPacketChannel.trySend(packet)
                // 记录背压丢包数量，便于监控与告警
                if (result.isFailure) {
                    droppedCount++
                }

                // 每 1000 个包 log 一次
                if (capturedCount % 1000 == 0L) {
                    logger.debug {
                        "Captured $capturedCount packets so far (dropped: $droppedCount)"
                    }
                }

                afterSendHook?.invoke(packet)
            }
        } catch (e: Exception) {
            logger.error(e) { "Capture loop error: ${e.message}" }
        } finally {
            if (running) {
                // 如果是异常退出，确保清理
                stop()
            }
        }
    }
}

/**
 * 抓包数据源抽象。
 *
 * 便于在线/离线统一处理与测试替换。
 */
interface PacketSource {
    /** 返回下一包，若无数据则返回 null。 */
    fun nextPacket(): Packet?

    /** 释放底层资源。 */
    fun close()
}

/**
 * 数据源工厂。
 *
 * 统一封装 Pcap4J 的创建流程与异常边界。
 */
interface PacketSourceFactory {
    /** 创建在线抓包源。 */
    @Throws(PcapNativeException::class)
    fun openLive(config: CaptureConfig): PacketSource

    /** 创建离线回放源。 */
    @Throws(PcapNativeException::class)
    fun openOffline(pcapFilePath: String): PacketSource
}

/**
 * Pcap4J 实现的抓包数据源。
 */
internal class PcapPacketSource(private val handle: PcapHandle) : PacketSource {
    override fun nextPacket(): Packet? = handle.nextPacket

    override fun close() {
        handle.close()
    }
}

/**
 * 基于 Pcap4J 的数据源工厂。
 */
internal object PcapPacketSourceFactory : PacketSourceFactory {
    override fun openLive(config: CaptureConfig): PacketSource {
        val nif = resolveInterface(config.interfaceName)
        val handle = nif.openLive(
            config.snapshotLength,
            if (config.promiscuous) PromiscuousMode.PROMISCUOUS
            else PromiscuousMode.NONPROMISCUOUS,
            config.readTimeoutMs
        )
        return PcapPacketSource(handle)
    }

    /**
     * 根据接口名解析网卡。
     *
     * 支持：
     * - 完整设备名（\\Device\\NPF_{GUID}）
     * - 描述字符串（包含匹配）
     * - 名称部分匹配
     */
    private fun resolveInterface(interfaceName: String): PcapNetworkInterface {
        val normalized = normalizeInterfaceName(interfaceName)
        val direct = Pcaps.getDevByName(normalized)
        if (direct != null) {
            return direct
        }

        val trimmed = normalized.trim()
        val all = Pcaps.findAllDevs() ?: emptyList()
        if (all.isEmpty()) {
            throw PcapNativeException("No network interfaces found by Pcap4J")
        }

        val byDescription = all.firstOrNull {
            it.description?.contains(trimmed, ignoreCase = true) == true
        }
        val byNameEquals = all.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        val byNameContains = all.firstOrNull { it.name.contains(trimmed, ignoreCase = true) }

        val resolved = byDescription ?: byNameEquals ?: byNameContains
        if (resolved != null) {
            logger.info {
                "Resolved capture interface '$interfaceName' to '${resolved.name}' " +
                "(${resolved.description ?: "no-desc"})"
            }
            return resolved
        }

        val available = all.joinToString(separator = "; ") {
            "${it.name} (${it.description ?: "no-desc"})"
        }
        throw PcapNativeException(
            "Network interface not found: $normalized. Available: $available"
        )
    }

    /**
     * 规范化 Windows 设备名，避免转义与花括号重复导致匹配失败。
     */
    private fun normalizeInterfaceName(value: String): String {
        return value.trim()
            .replace("\\\\Device\\\\NPF_", "\\Device\\NPF_")
            .replace("\\\\", "\\")
            .replace("{{", "{")
            .replace("}}", "}")
    }

    override fun openOffline(pcapFilePath: String): PacketSource {
        return PcapPacketSource(Pcaps.openOffline(pcapFilePath))
    }
}
