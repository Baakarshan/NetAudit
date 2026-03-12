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
     *
     * @return 清理协程的 Job 句柄
     */
    fun startCleanupJob(): Job

    /**
     * 处理 TCP 包（需要流重组）。
     *
     * @param metadata 解码后的包元数据
     */
    suspend fun handleTcpPacket(metadata: PacketMetadata)

    /**
     * 处理 UDP 包（无需重组）。
     *
     * @param metadata 解码后的包元数据
     */
    suspend fun handleUdpPacket(metadata: PacketMetadata)

    /**
     * 当前活跃流数量（用于监控与压测验证）。
     */
    fun activeStreamCount(): Int
}
