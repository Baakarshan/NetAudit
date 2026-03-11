package com.netaudit.capture

import kotlinx.coroutines.channels.ReceiveChannel
import org.pcap4j.packet.Packet

/**
 * 抓包引擎抽象。
 *
 * 约束：实现必须保证 `rawPacketChannel` 的生命周期可控，停止后应关闭通道。
 */
interface CaptureEngine {
    /**
     * 原始包输出通道。
     *
     * 约定：由实现创建并负责关闭，消费者只读不写。
     */
    val rawPacketChannel: ReceiveChannel<Packet>

    /** 启动在线抓包。 */
    fun startLive()

    /** 启动离线回放（pcap 文件）。 */
    fun startOffline(pcapFilePath: String)

    /** 停止抓包并释放底层资源。 */
    fun stop()
}
