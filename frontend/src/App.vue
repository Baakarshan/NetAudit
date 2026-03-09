<template>
  <AppLayout />
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { ElNotification } from 'element-plus'
import AppLayout from '@/components/layout/AppLayout.vue'
import { useAuditStore } from '@/stores/auditStore'
import { useSse } from '@/composables/useSse'

const store = useAuditStore()

store.loadInitialData()
useSse(store.addAuditEvent, store.addAlert)

const playAlertSound = () => {
  const audio = new Audio('/alert.mp3')
  audio.volume = 0.5
  audio.play().catch(() => {
    // 浏览器可能阻止自动播放，忽略错误
  })
}

watch(
  () => store.recentAlerts[0],
  (newAlert) => {
    if (!newAlert) return
    const type = newAlert.level === 'CRITICAL' ? 'error' : newAlert.level === 'WARN' ? 'warning' : 'info'
    ElNotification({
      title: newAlert.ruleName,
      message: newAlert.message,
      type,
      position: 'top-right',
      duration: 6000
    })
    if (newAlert.level === 'CRITICAL') {
      playAlertSound()
    }
  }
)
</script>
