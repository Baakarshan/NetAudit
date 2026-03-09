<template>
  <div class="alert-view">
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :md="8">
        <el-card class="stat-card critical">
          <div class="stat-title">CRITICAL</div>
          <div class="stat-value">{{ counts.CRITICAL || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card class="stat-card warn">
          <div class="stat-title">WARN</div>
          <div class="stat-value">{{ counts.WARN || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card class="stat-card info">
          <div class="stat-title">INFO</div>
          <div class="stat-value">{{ counts.INFO || 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <AlertHistoryTable :alerts="store.recentAlerts" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuditStore } from '@/stores/auditStore'
import AlertHistoryTable from '@/components/alert/AlertHistoryTable.vue'

const store = useAuditStore()
const counts = computed(() => store.alertCounts)
</script>

<style scoped>
.alert-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-row {
  margin-bottom: 6px;
}

.stat-card {
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.stat-card.critical {
  background: linear-gradient(135deg, #fee2e2, #fecaca);
}

.stat-card.warn {
  background: linear-gradient(135deg, #ffedd5, #fed7aa);
}

.stat-card.info {
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
}

.stat-title {
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
}
</style>
