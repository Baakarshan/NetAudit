<template>
  <el-row :gutter="16" class="metrics-row">
    <el-col :xs="24" :sm="12" :md="8">
      <el-card class="metric-card">
        <div class="metric-title">实时 QPS</div>
        <div class="metric-value">{{ qps }}</div>
        <div class="metric-desc">最近 1 秒事件数</div>
      </el-card>
    </el-col>
    <el-col :xs="24" :sm="12" :md="8">
      <el-card class="metric-card">
        <div class="metric-title">近 1 分钟</div>
        <div class="metric-value">{{ lastMinute }}</div>
        <div class="metric-desc">滚动窗口内事件数</div>
      </el-card>
    </el-col>
    <el-col :xs="24" :sm="12" :md="8">
      <el-card class="metric-card">
        <div class="metric-title">活跃协议</div>
        <div class="metric-value">{{ activeProtocols }}</div>
        <div class="metric-desc">当前有数据的协议数</div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
defineProps<{
  qps: number
  lastMinute: number
  activeProtocols: number
}>()
</script>

<style scoped>
.metrics-row {
  margin-bottom: 10px;
}

.metric-card {
  border-radius: var(--radius);
  background: linear-gradient(135deg, #ffffff, #f4f7fb);
  box-shadow: var(--shadow);
}

.metric-title {
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 6px;
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 4px;
}

.metric-desc {
  font-size: 12px;
  color: var(--text-3);
}
</style>
