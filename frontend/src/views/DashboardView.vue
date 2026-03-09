<template>
  <div class="dashboard">
    <StatsCardRow
      :total="store.totalEvents"
      :http-count="store.protocolCounts['HTTP'] || 0"
      :alert-total="alertTotal"
      :critical-count="store.criticalAlertCount"
    />

    <el-row :gutter="16" class="charts-row">
      <el-col :xs="24" :md="12">
        <ProtocolPieChart :counts="store.protocolCounts" />
      </el-col>
      <el-col :xs="24" :md="12">
        <TrafficTimeline :data="store.timelineData" />
      </el-col>
    </el-row>

    <RecentEventsTable :events="store.recentEvents" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuditStore } from '@/stores/auditStore'
import StatsCardRow from '@/components/dashboard/StatsCardRow.vue'
import ProtocolPieChart from '@/components/dashboard/ProtocolPieChart.vue'
import TrafficTimeline from '@/components/dashboard/TrafficTimeline.vue'
import RecentEventsTable from '@/components/dashboard/RecentEventsTable.vue'

const store = useAuditStore()
const alertTotal = computed(() =>
  Object.values(store.alertCounts).reduce((sum, value) => sum + value, 0)
)
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.charts-row {
  margin-bottom: 6px;
}
</style>
