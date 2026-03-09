<template>
  <el-card class="table-card">
    <div class="card-title">告警历史</div>
    <el-table :data="alerts" stripe>
      <el-table-column prop="timestamp" label="时间" width="180" />
      <el-table-column label="级别" width="120">
        <template #default="scope">
          <el-tag :style="{ background: alertColor(scope.row.level), color: '#fff', border: 'none' }">
            {{ scope.row.level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ruleName" label="规则" width="200" />
      <el-table-column prop="message" label="消息" />
      <el-table-column prop="protocol" label="协议" width="120" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import type { AlertRecord } from '@/types/models'
import { ALERT_COLORS } from '@/utils/protocolColors'

defineProps<{ alerts: AlertRecord[] }>()

const alertColor = (level: string) => ALERT_COLORS[level] || '#64748b'
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
