import { ref, onMounted, onUnmounted } from 'vue'
import type { AlertRecord, AuditEvent } from '@/types/models'

export function useSse(
  onAudit: (event: AuditEvent) => void,
  onAlert: (alert: AlertRecord) => void
) {
  const connected = ref(false)
  let eventSource: EventSource | null = null

  const connect = () => {
    const baseUrl = (import.meta.env.VITE_API_BASE || 'http://localhost:8080').replace(/\/$/, '')
    eventSource = new EventSource(`${baseUrl}/api/sse/events`)

    eventSource.onopen = () => {
      connected.value = true
    }

    eventSource.addEventListener('audit', (e: MessageEvent) => {
      try {
        const event: AuditEvent = JSON.parse(e.data)
        onAudit(event)
      } catch (err) {
        console.error('Failed to parse audit event', err)
      }
    })

    eventSource.addEventListener('alert', (e: MessageEvent) => {
      try {
        const alert: AlertRecord = JSON.parse(e.data)
        onAlert(alert)
      } catch (err) {
        console.error('Failed to parse alert event', err)
      }
    })

    eventSource.onerror = () => {
      connected.value = false
    }
  }

  const disconnect = () => {
    eventSource?.close()
    connected.value = false
  }

  onMounted(connect)
  onUnmounted(disconnect)

  return { connected, disconnect }
}
