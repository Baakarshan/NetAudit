<template>
  <div class="audit-view">
    <AuditFilterBar v-model="filters" @search="loadLogs" />

    <el-card class="table-card">
      <el-table :data="logs" stripe @row-click="onRowClick">
        <el-table-column prop="timestamp" label="时间" width="180" />
        <el-table-column prop="protocol" label="协议" width="120" />
        <el-table-column prop="srcIp" label="源IP" width="160" />
        <el-table-column prop="dstIp" label="目标IP" width="160" />
        <el-table-column label="摘要">
          <template #default="scope">
            {{ summary(scope.row) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next"
          :page-size="pageSize"
          :total="total"
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <AuditDetailDrawer v-model="drawerVisible" :event="selectedEvent" />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { AuditEvent } from '@/types/models'
import { auditApi } from '@/api/client'
import AuditFilterBar from '@/components/audit/AuditFilterBar.vue'
import AuditDetailDrawer from '@/components/audit/AuditDetailDrawer.vue'

const logs = ref<AuditEvent[]>([])
const pageSize = 20
const total = ref(0)
const currentPage = ref(1)

const drawerVisible = ref(false)
const selectedEvent = ref<AuditEvent | null>(null)

const filters = reactive({
  protocol: '',
  srcIp: '',
  range: null as [Date, Date] | null
})

const loadLogs = async () => {
  const params: Record<string, string | number> = {
    page: currentPage.value - 1,
    size: pageSize
  }
  if (filters.protocol) params.protocol = filters.protocol
  if (filters.srcIp) params.srcIp = filters.srcIp
  if (filters.range) {
    params.start = filters.range[0].toISOString()
    params.end = filters.range[1].toISOString()
  }

  const response = await auditApi.getLogs(params)
  logs.value = response.data
  total.value = response.data.length + (currentPage.value - 1) * pageSize
}

const onPageChange = (page: number) => {
  currentPage.value = page
  loadLogs()
}

const onRowClick = (row: AuditEvent) => {
  selectedEvent.value = row
  drawerVisible.value = true
}

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

loadLogs()
</script>

<style scoped>
.audit-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-card {
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
