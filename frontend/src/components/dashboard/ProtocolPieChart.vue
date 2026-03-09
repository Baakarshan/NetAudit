<template>
  <el-card class="chart-card">
    <div class="card-title">协议分布</div>
    <div ref="chartRef" class="chart"></div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref, watchEffect } from 'vue'
import * as echarts from 'echarts'
import { PROTOCOL_COLORS } from '@/utils/protocolColors'

const props = defineProps<{ counts: Record<string, number> }>()
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const data = Object.entries(props.counts).map(([name, value]) => ({
    name,
    value,
    itemStyle: { color: PROTOCOL_COLORS[name] || '#64748b' }
  }))

  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { top: 'bottom' },
    series: [
      {
        type: 'pie',
        radius: ['35%', '60%'],
        data,
        label: { formatter: '{b}' }
      }
    ]
  })
}

onMounted(render)
watchEffect(render)
</script>

<style scoped>
.chart-card {
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.card-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.chart {
  width: 100%;
  height: 300px;
}
</style>
