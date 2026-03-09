import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AlertRecord, AuditEvent, DashboardStats } from '@/types/models'
import { alertApi, auditApi, statsApi } from '@/api/client'

export const useAuditStore = defineStore('audit', () => {
  const recentEvents = ref<AuditEvent[]>([])
  const recentAlerts = ref<AlertRecord[]>([])
  const stats = ref<DashboardStats | null>(null)

  const timelineData = ref<{ time: string; count: number }[]>([])
  let currentSecondCount = 0
  let currentSecond = 0

  const totalEvents = computed(() => stats.value?.totalEvents ?? 0)
  const protocolCounts = computed(() => stats.value?.protocolCounts ?? {})
  const alertCounts = computed(() => stats.value?.alertCounts ?? {})
  const criticalAlertCount = computed(
    () => recentAlerts.value.filter(alert => alert.level === 'CRITICAL').length
  )

  function addAuditEvent(event: AuditEvent) {
    recentEvents.value.unshift(event)
    if (recentEvents.value.length > 200) {
      recentEvents.value.pop()
    }

    if (stats.value) {
      stats.value.totalEvents += 1
      const p = event.protocol
      stats.value.protocolCounts[p] = (stats.value.protocolCounts[p] ?? 0) + 1
    }

    updateTimeline()
  }

  function addAlert(alert: AlertRecord) {
    recentAlerts.value.unshift(alert)
    if (recentAlerts.value.length > 100) {
      recentAlerts.value.pop()
    }

    if (stats.value) {
      stats.value.alertCounts[alert.level] = (stats.value.alertCounts[alert.level] ?? 0) + 1
    }
  }

  async function loadInitialData() {
    try {
      const [dashRes, eventsRes, alertsRes] = await Promise.all([
        statsApi.getDashboard(),
        auditApi.getRecent(50),
        alertApi.getRecent(20)
      ])
      stats.value = dashRes.data
      recentEvents.value = eventsRes.data
      recentAlerts.value = alertsRes.data
    } catch (err) {
      console.error('Failed to load initial data', err)
    }
  }

  function updateTimeline() {
    const now = Math.floor(Date.now() / 1000)
    if (now === currentSecond) {
      currentSecondCount += 1
    } else {
      if (currentSecond) {
        timelineData.value.push({
          time: new Date(currentSecond * 1000).toLocaleTimeString(),
          count: currentSecondCount
        })
        if (timelineData.value.length > 60) {
          timelineData.value.shift()
        }
      }
      currentSecond = now
      currentSecondCount = 1
    }
  }

  return {
    recentEvents,
    recentAlerts,
    stats,
    timelineData,
    totalEvents,
    protocolCounts,
    alertCounts,
    criticalAlertCount,
    addAuditEvent,
    addAlert,
    loadInitialData
  }
})
