<template>
  <div class="capture">
    <el-card class="control-panel">
      <h2>网络包捕获</h2>
      <el-form :inline="true">
        <el-form-item label="网卡">
          <el-select v-model="selectedInterface" placeholder="选择网卡">
            <el-option label="eth0" value="eth0" />
            <el-option label="wlan0" value="wlan0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="startCapture" :disabled="isCapturing">
            开始捕获
          </el-button>
          <el-button type="danger" @click="stopCapture" :disabled="!isCapturing">
            停止捕获
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="data-panel">
      <h3>实时数据流</h3>
      <el-table :data="recentPackets" height="400" stripe>
        <el-table-column prop="timestamp" label="时间" width="180" />
        <el-table-column prop="protocol" label="协议" width="100" />
        <el-table-column prop="srcIp" label="源IP" width="150" />
        <el-table-column prop="srcPort" label="源端口" width="100" />
        <el-table-column prop="dstIp" label="目标IP" width="150" />
        <el-table-column prop="dstPort" label="目标端口" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { WebSocketClient, type AuditEvent } from '@/services/websocket'

const selectedInterface = ref('eth0')
const isCapturing = ref(false)
const recentPackets = ref<AuditEvent[]>([])

let wsClient: WebSocketClient | null = null

const startCapture = () => {
  isCapturing.value = true
  recentPackets.value = []

  wsClient = new WebSocketClient(
    '/ws/capture',
    (event: AuditEvent) => {
      recentPackets.value.unshift(event)
      if (recentPackets.value.length > 100) {
        recentPackets.value.pop()
      }
    },
    (error) => {
      console.error('WebSocket error:', error)
    }
  )
  wsClient.connect()
}

const stopCapture = () => {
  isCapturing.value = false
  if (wsClient) {
    wsClient.disconnect()
    wsClient = null
  }
}

onUnmounted(() => {
  if (wsClient) {
    wsClient.disconnect()
  }
})
</script>

<style scoped>
.capture {
  padding: 20px;
}

.control-panel {
  margin-bottom: 20px;
}

.data-panel {
  margin-top: 20px;
}
</style>
