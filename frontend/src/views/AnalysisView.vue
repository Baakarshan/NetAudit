<template>
  <div class="analysis">
    <el-card class="stats-panel">
      <h2>数据分析</h2>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-statistic title="总包数" :value="stats.totalPackets" />
        </el-col>
        <el-col :span="12">
          <el-button type="primary" @click="loadStats">刷新统计</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="chart-panel">
      <h3>协议分布</h3>
      <div ref="chartRef" style="width: 100%; height: 400px"></div>
    </el-card>

    <el-card class="session-panel">
      <h3>会话列表</h3>
      <el-table :data="sessions" stripe>
        <el-table-column prop="protocol" label="协议" width="100" />
        <el-table-column prop="srcIp" label="源IP" width="150" />
        <el-table-column prop="dstIp" label="目标IP" width="150" />
        <el-table-column prop="timestamp" label="时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type SystemStats } from '@/services/api'
import * as echarts from 'echarts'

const stats = ref<SystemStats>({ totalPackets: 0, protocolStats: {} })
const sessions = ref<any[]>([])
const chartRef = ref<HTMLElement>()

const loadStats = async () => {
  try {
    stats.value = await api.getStats()
    updateChart()
  } catch (error) {
    console.error('Failed to load stats:', error)
  }
}

const updateChart = () => {
  if (!chartRef.value) return

  const chart = echarts.init(chartRef.value)
  const data = Object.entries(stats.value.protocolStats).map(([name, value]) => ({
    name,
    value
  }))

  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [
      {
        type: 'pie',
        radius: '50%',
        data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  })
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.analysis {
  padding: 20px;
}

.stats-panel,
.chart-panel,
.session-panel {
  margin-bottom: 20px;
}
</style>
