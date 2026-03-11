package com.netaudit.stream

import com.netaudit.model.PacketMetadata
import kotlinx.coroutines.Job

/**
 * 流追踪抽象。
 *
 * 统一 TCP/UDP 的流处理入口，便于测试替换与扩展。
 */
interface StreamTracker {
    /**
     * 启动超时清理任务，返回可取消的 Job。
     */
    fun startCleanupJob(): Job

    /** 处理 TCP 包（需要流重组）。 */
    suspend fun handleTcpPacket(metadata: PacketMetadata)

    /** 处理 UDP 包（无需重组）。 */
    suspend fun handleUdpPacket(metadata: PacketMetadata)

    /** 当前活跃流数量，用于监控。 */
    fun activeStreamCount(): Int
}
