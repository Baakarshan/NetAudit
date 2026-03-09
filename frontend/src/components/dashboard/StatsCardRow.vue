<template>
  <el-row :gutter="16" class="stats-row">
    <el-col :xs="24" :sm="12" :md="6">
      <el-card class="stat-card">
        <div class="stat-title">总事件数</div>
        <div class="stat-value">{{ total }}</div>
      </el-card>
    </el-col>
    <el-col :xs="24" :sm="12" :md="6">
      <el-card class="stat-card">
        <div class="stat-title">HTTP 事件</div>
        <div class="stat-value">{{ httpCount }}</div>
      </el-card>
    </el-col>
    <el-col :xs="24" :sm="12" :md="6">
      <el-card class="stat-card">
        <div class="stat-title">告警总数</div>
        <div class="stat-value">{{ alertTotal }}</div>
      </el-card>
    </el-col>
    <el-col :xs="24" :sm="12" :md="6">
      <el-card class="stat-card danger">
        <div class="stat-title">CRITICAL</div>
        <div class="stat-value">{{ criticalCount }}</div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
defineProps<{
  total: number
  httpCount: number
  alertTotal: number
  criticalCount: number
}>()
</script>

<style scoped>
.stats-row {
  margin-bottom: 18px;
}

.stat-card {
  border-radius: var(--radius);
  background: linear-gradient(135deg, #ffffff, #f6f8fb);
  box-shadow: var(--shadow);
}

.stat-card.danger {
  background: linear-gradient(135deg, #fff1f2, #ffe4e6);
}

.stat-title {
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  transition: all 0.3s ease;
}
</style>
