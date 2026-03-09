package com.netaudit.stream

import com.netaudit.model.PacketMetadata
import kotlinx.coroutines.Job

interface StreamTracker {
    fun startCleanupJob(): Job
    suspend fun handleTcpPacket(metadata: PacketMetadata)
    suspend fun handleUdpPacket(metadata: PacketMetadata)
    fun activeStreamCount(): Int
}
