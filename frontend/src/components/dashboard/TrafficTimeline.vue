<template>
  <el-card class="chart-card">
    <div class="card-title">实时流量时间线</div>
    <div ref="chartRef" class="chart"></div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref, watchEffect } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{ data: { time: string; count: number }[] }>()
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  chart.setOption({
    grid: { left: 24, right: 24, top: 20, bottom: 24 },
    xAxis: {
      type: 'category',
      data: props.data.map(item => item.time),
      boundaryGap: false
    },
    yAxis: { type: 'value' },
    tooltip: { trigger: 'axis' },
    series: [
      {
        type: 'line',
        data: props.data.map(item => item.count),
        smooth: true,
        areaStyle: { color: 'rgba(14, 165, 233, 0.15)' },
        lineStyle: { color: '#0ea5e9', width: 2 }
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
