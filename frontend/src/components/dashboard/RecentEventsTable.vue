<template>
  <el-card class="table-card">
    <div class="card-title">最近事件</div>
    <el-table :data="rows" height="360" stripe>
      <el-table-column label="时间" width="200">
        <template #default="scope">
          {{ formatTimestamp(scope.row?.timestamp) }}
        </template>
      </el-table-column>
      <el-table-column label="协议" width="110">
        <template #default="scope">
          <el-tag :style="{ background: protocolColor(rowProtocol(scope.row)), color: '#fff', border: 'none' }">
            {{ rowProtocol(scope.row) }}
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

const rowProtocol = (row: unknown) => (row as any)?.protocol ?? 'UNKNOWN'

const protocolColor = (protocol: string) => PROTOCOL_COLORS[protocol] || '#64748b'

const formatTimestamp = (value: unknown) => {
  if (!value) return ''
  const date = new Date(value as any)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const summary = (event: unknown) => {
  const e = event as any
  switch (e?.protocol) {
    case 'HTTP':
      return `${e.method} ${e.url}`
    case 'FTP':
      return `${e.command} ${e.argument || ''}`.trim()
    case 'TELNET':
      return e.commandLine
    case 'DNS':
      return e.queryDomain
    case 'SMTP':
      return e.subject || '邮件发送'
    case 'POP3':
      return e.command
    default:
      return e?.protocol ?? ''
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
