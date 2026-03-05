package com.netaudit.capture

import com.netaudit.config.CaptureConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.pcap4j.core.PcapHandle
import org.pcap4j.core.PcapNativeException
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode
import org.pcap4j.core.Pcaps
import org.pcap4j.packet.Packet

private val logger = KotlinLogging.logger {}

/**
 * 包捕获引擎。
 * Template Method 模式：统一 open → loop → close 生命周期。
 *
 * 职责:
 * - 打开指定网卡的 Pcap4J PcapHandle，设置混杂模式
 * - 在专用协程中循环调用 handle.getNextPacket()
 * - 将原始 Packet 对象发送到 Channel<Packet>（背压缓冲）
 * - 支持 graceful shutdown（close() 方法 break 循环 + close handle）
 * - 支持离线模式：从 .pcap 文件读取（用于测试）
 */
class PacketCaptureEngine(
    private val config: CaptureConfig,
    private val scope: CoroutineScope
) {
    val rawPacketChannel = Channel<Packet>(capacity = config.channelBufferSize)

    private var handle: PcapHandle? = null
    @Volatile private var running = false
    private var capturedCount = 0L
    private var droppedCount = 0L

    /**
     * 启动在线抓包。在 scope 中启动协程。
     * 协程内循环: handle.getNextPacket() → channel.send()
     */
    fun startLive() {
        if (running) {
            logger.warn { "Capture engine already running" }
            return
        }

        try {
            val nif = Pcaps.getDevByName(config.interfaceName)
            handle = nif.openLive(
                config.snapshotLength,
                if (config.promiscuous) PromiscuousMode.PROMISCUOUS
                else PromiscuousMode.NONPROMISCUOUS,
                config.readTimeoutMs
            )

            logger.info {
                "Capture started on interface=${config.interfaceName}, " +
                "promiscuous=${config.promiscuous}, " +
                "bufferSize=${config.channelBufferSize}"
            }

            running = true
            scope.launch(Dispatchers.IO) {
                captureLoop()
            }
        } catch (e: PcapNativeException) {
            logger.error(e) { "Failed to open capture device: ${e.message}" }
            throw e
        }
    }

    /**
     * 从 pcap 文件读取（测试用）。
     * 同样走 channel.send()，但读完文件后 close channel。
     */
    fun startOffline(pcapFilePath: String) {
        if (running) {
            logger.warn { "Capture engine already running" }
            return
        }

        try {
            handle = Pcaps.openOffline(pcapFilePath)
            logger.info { "Offline capture started from file: $pcapFilePath" }

            running = true
            scope.launch(Dispatchers.IO) {
                captureLoop()
                // 离线模式读完后关闭 channel
                rawPacketChannel.close()
                logger.info { "Offline capture completed. Channel closed." }
            }
        } catch (e: PcapNativeException) {
            logger.error(e) { "Failed to open pcap file: ${e.message}" }
            throw e
        }
    }

    /**
     * 停止抓包，关闭 handle，关闭 channel。
     */
    fun stop() {
        if (!running) {
            return
        }

        running = false
        handle?.close()
        rawPacketChannel.close()

        logger.info {
            "Capture stopped. Total captured: $capturedCount, dropped: $droppedCount"
        }
    }

    private fun captureLoop() {
        val currentHandle = handle ?: return

        try {
            while (running) {
                val packet = try {
                    currentHandle.nextPacket
                } catch (e: Exception) {
                    logger.error(e) { "Error reading packet: ${e.message}" }
                    break
                }

                // getNextPacket() 返回 null 时表示超时，继续循环
                if (packet == null) {
                    continue
                }

                capturedCount++

                // 使用 trySend 避免阻塞抓包线程
                val result = rawPacketChannel.trySend(packet)
                if (result.isFailure) {
                    droppedCount++
                }

                // 每 1000 个包 log 一次
                if (capturedCount % 1000 == 0L) {
                    logger.debug {
                        "Captured $capturedCount packets so far (dropped: $droppedCount)"
                    }
                }
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
