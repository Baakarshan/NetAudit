<template>
  <el-card class="table-card">
    <div class="card-title">最近事件</div>
    <el-table :data="rows" height="360" stripe>
      <el-table-column prop="timestamp" label="时间" width="180" />
      <el-table-column label="协议" width="110">
        <template #default="scope">
          <el-tag :style="{ background: protocolColor(scope.row.protocol), color: '#fff', border: 'none' }">
            {{ scope.row.protocol }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="srcIp" label="源IP" width="150" />
      <el-table-column prop="dstIp" label="目标IP" width="150" />
      <el-table-column label="摘要">
        <template #default="scope">
          {{ summary(scope.row) }}
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AuditEvent } from '@/types/models'
import { PROTOCOL_COLORS } from '@/utils/protocolColors'

const props = defineProps<{ events: AuditEvent[] }>()

const rows = computed(() => props.events.slice(0, 20))

const protocolColor = (protocol: string) => PROTOCOL_COLORS[protocol] || '#64748b'

const summary = (event: AuditEvent) => {
  switch (event.protocol) {
    case 'HTTP':
      return `${event.method} ${event.url}`
    case 'FTP':
      return `${event.command} ${event.argument || ''}`.trim()
    case 'TELNET':
      return event.commandLine
    case 'DNS':
      return event.queryDomain
    case 'SMTP':
      return event.subject || '邮件发送'
    case 'POP3':
      return event.command
    default:
      return event.protocol
  }
}
</script>

<style scoped>
.table-card {
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.card-title {
  font-weight: 600;
  margin-bottom: 12px;
}
</style>
