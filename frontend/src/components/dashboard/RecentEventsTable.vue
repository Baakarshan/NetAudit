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

const isDockerIp = (ip?: string) => (ip ? /^172\.28\./.test(ip) : false)
const isScriptEvent = (event: AuditEvent) =>
  isDockerIp(event.srcIp) || isDockerIp(event.dstIp)

const sortByTimeDesc = (a: AuditEvent, b: AuditEvent) =>
  new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()

const isLocalHost = (host: string) =>
  host === 'localhost' ||
  host === '127.0.0.1' ||
  host === '::1' ||
  host.startsWith('127.') ||
  host.startsWith('172.28.')

const isInternalApiEvent = (event: AuditEvent) => {
  if (event.protocol !== 'HTTP') return false
  const rawUrl = (event as any)?.url
  if (!rawUrl) return false
  try {
    const parsed = new URL(String(rawUrl))
    if (!isLocalHost(parsed.hostname)) return false
    if (!parsed.pathname.startsWith('/api/')) return false
    if (parsed.port && parsed.port !== '8080' && parsed.port !== '8081') {
      return false
    }
    return true
  } catch {
    const text = String(rawUrl).toLowerCase()
    if (!text.includes('/api/')) return false
    return (
      text.includes('127.0.0.1') ||
      text.includes('localhost') ||
      text.includes('[::1]') ||
      text.includes('172.28.')
    )
  }
}

const rows = computed(() => {
  const events = (props.events ?? []).filter(event => !isInternalApiEvent(event))
  if (events.length <= 20) return events
  const scriptEvents = events.filter(isScriptEvent).sort(sortByTimeDesc)
  const realEvents = events.filter(event => !isScriptEvent(event)).sort(sortByTimeDesc)
  if (scriptEvents.length === 0 || realEvents.length === 0) {
    return events.slice(0, 20)
  }

  const mixed: AuditEvent[] = []
  let i = 0
  let j = 0
  while (mixed.length < 20 && (i < scriptEvents.length || j < realEvents.length)) {
    if (i < scriptEvents.length) {
      mixed.push(scriptEvents[i++])
    }
    if (mixed.length >= 20) break
    if (j < realEvents.length) {
      mixed.push(realEvents[j++])
    }
  }
  return mixed
})

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
    case 'TLS': {
      const parts: string[] = []
      if (e.serverName) parts.push(e.serverName)
      if (Array.isArray(e.alpn) && e.alpn.length > 0) {
        parts.push(`ALPN:${e.alpn.join('/')}`)
      }
      if (e.clientVersion) parts.push(e.clientVersion)
      return parts.join(' ') || 'TLS 握手'
    }
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
