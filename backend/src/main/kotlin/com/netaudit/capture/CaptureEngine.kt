package com.netaudit.capture

import kotlinx.coroutines.channels.ReceiveChannel
import org.pcap4j.packet.Packet

/**
 * 抓包引擎抽象。
 *
 * 生命周期约定：
 * 1. 调用 `startLive()` 或 `startOffline()` 启动捕获。
 * 2. 持续向 `rawPacketChannel` 推送原始包。
 * 3. 调用 `stop()` 结束并释放底层资源（同时关闭通道）。
 *
 * 线程安全：
 * - 建议单实例单线程（或单协程）驱动，避免并发启动/停止。
 *
 * 异常处理：
 * - 具体实现可能抛出底层抓包库异常（如设备不可用/权限不足）。
 */
interface CaptureEngine {
    /**
     * 原始包输出通道。
     *
     * 约定：
     * - 由实现创建并负责关闭；
     * - 消费者只读不写；
     * - `stop()` 后通道应关闭，避免资源泄漏。
     */
    val rawPacketChannel: ReceiveChannel<Packet>

    /**
     * 启动在线抓包。
     *
     * 实现应保证幂等：重复调用不会导致重复启动。
     */
    fun startLive()

    /**
     * 启动离线回放（pcap 文件）。
     *
     * @param pcapFilePath pcap 文件路径
     */
    fun startOffline(pcapFilePath: String)

    /**
     * 停止抓包并释放底层资源。
     *
     * 应保证幂等：重复调用不会引发异常。
     */
    fun stop()
}
