import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import axios from 'axios'
import type { AlertRecord, AuditEvent, DashboardStats } from '@/types/models'
import { getApiBaseUrls } from '@/api/client'

export const useAuditStore = defineStore('audit', () => {
  const recentEvents = ref<AuditEvent[]>([])
  const recentAlerts = ref<AlertRecord[]>([])
  const stats = ref<DashboardStats | null>(null)

  const timelineData = ref<{ time: string; count: number }[]>([])
  const currentSecondCount = ref(0)
  const currentSecond = ref(0)
  const recentEventSeconds = ref<number[]>([])

  const pushTimelinePoint = (second: number, count: number) => {
    timelineData.value.push({
      time: new Date(second * 1000).toLocaleTimeString(),
      count
    })
    if (timelineData.value.length > 60) {
      timelineData.value.shift()
    }
  }

  const seedTimelineFromEvents = (events: AuditEvent[]) => {
    const buckets = new Map<number, number>()
    for (const event of events) {
      const ts = Math.floor(new Date(event.timestamp).getTime() / 1000)
      buckets.set(ts, (buckets.get(ts) ?? 0) + 1)
    }
    const sorted = Array.from(buckets.entries()).sort((a, b) => a[0] - b[0])
    const tail = sorted.slice(-60)
    timelineData.value = tail.map(([second, count]) => ({
      time: new Date(second * 1000).toLocaleTimeString(),
      count
    }))
    if (tail.length > 0) {
      const [lastSecond, lastCount] = tail[tail.length - 1]
      currentSecond.value = lastSecond
      currentSecondCount.value = lastCount
    }
  }

  const totalEvents = computed(() => stats.value?.totalEvents ?? 0)
  const protocolCounts = computed(() => stats.value?.protocolCounts ?? {})
  const alertCounts = computed(() => stats.value?.alertCounts ?? {})
  const criticalAlertCount = computed(
    () => recentAlerts.value.filter(alert => alert.level === 'CRITICAL').length
  )
  const qps = computed(() => currentSecondCount.value)
  const lastMinuteCount = computed(() => recentEventSeconds.value.length)
  const activeProtocolCount = computed(() =>
    Object.entries(protocolCounts.value).filter(([, count]) => count > 0).length
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

    recordRealtime()
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
      const baseUrls = getApiBaseUrls()
      const dashboards = await Promise.all(
        baseUrls.map(baseUrl =>
          axios
            .get<DashboardStats>(`${baseUrl}/api/stats/dashboard`, { timeout: 10000 })
            .catch(() => null)
        )
      )
      const eventsList = await Promise.all(
        baseUrls.map(baseUrl =>
          axios
            .get<AuditEvent[]>(`${baseUrl}/api/audit/recent`, {
              params: { limit: 50 },
              timeout: 10000
            })
            .catch(() => null)
        )
      )
      const alertsList = await Promise.all(
        baseUrls.map(baseUrl =>
          axios
            .get<AlertRecord[]>(`${baseUrl}/api/alerts/recent`, {
              params: { limit: 20 },
              timeout: 10000
            })
            .catch(() => null)
        )
      )

      const mergedStats: DashboardStats = {
        totalEvents: 0,
        protocolCounts: {},
        alertCounts: {}
      }

      dashboards.forEach(response => {
        if (!response) return
        const data = response.data
        mergedStats.totalEvents += data.totalEvents
        Object.entries(data.protocolCounts).forEach(([protocol, count]) => {
          mergedStats.protocolCounts[protocol] = (mergedStats.protocolCounts[protocol] ?? 0) + count
        })
        Object.entries(data.alertCounts).forEach(([level, count]) => {
          mergedStats.alertCounts[level] = (mergedStats.alertCounts[level] ?? 0) + count
        })
      })

      const mergedEvents = eventsList
        .filter(Boolean)
        .flatMap(response => response?.data ?? [])
        .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())

      const mergedAlerts = alertsList
        .filter(Boolean)
        .flatMap(response => response?.data ?? [])
        .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())

      stats.value = mergedStats
      recentEvents.value = mergedEvents
      recentAlerts.value = mergedAlerts
      seedTimelineFromEvents(recentEvents.value)
      seedRealtimeWindow(recentEvents.value)
    } catch (err) {
      console.error('Failed to load initial data', err)
    }
  }

  function updateTimeline() {
    const now = Math.floor(Date.now() / 1000)
    if (now === currentSecond.value) {
      currentSecondCount.value += 1
      const last = timelineData.value[timelineData.value.length - 1]
      if (last) {
        last.count = currentSecondCount.value
      } else {
        pushTimelinePoint(now, currentSecondCount.value)
      }
      return
    }

    currentSecond.value = now
    currentSecondCount.value = 1
    pushTimelinePoint(now, currentSecondCount.value)
  }

  function recordRealtime() {
    const now = Math.floor(Date.now() / 1000)
    recentEventSeconds.value.push(now)
    const threshold = now - 59
    recentEventSeconds.value = recentEventSeconds.value.filter(ts => ts >= threshold)
  }

  function seedRealtimeWindow(events: AuditEvent[]) {
    const now = Math.floor(Date.now() / 1000)
    const threshold = now - 59
    recentEventSeconds.value = events
      .map(event => Math.floor(new Date(event.timestamp).getTime() / 1000))
      .filter(ts => ts >= threshold && ts <= now)
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
    qps,
    lastMinuteCount,
    activeProtocolCount,
    addAuditEvent,
    addAlert,
    loadInitialData
  }
})
