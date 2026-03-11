import { ref, onMounted, onUnmounted } from 'vue'
import type { AlertRecord, AuditEvent } from '@/types/models'
import { getApiBaseUrls } from '@/api/client'

export function useSse(
  onAudit: (event: AuditEvent) => void,
  onAlert: (alert: AlertRecord) => void
) {
  const connected = ref(false)
  const sources: EventSource[] = []
  const openSources = new Set<EventSource>()

  const connect = () => {
    const baseUrls = getApiBaseUrls()
    baseUrls.forEach((baseUrl) => {
      const source = new EventSource(`${baseUrl}/api/sse/events`)
      sources.push(source)

      source.onopen = () => {
        openSources.add(source)
        connected.value = openSources.size > 0
      }

      source.addEventListener('audit', (e: MessageEvent) => {
        try {
          const event: AuditEvent = JSON.parse(e.data)
          onAudit(event)
        } catch (err) {
          console.error('Failed to parse audit event', err)
        }
      })

      source.addEventListener('alert', (e: MessageEvent) => {
        try {
          const alert: AlertRecord = JSON.parse(e.data)
          onAlert(alert)
        } catch (err) {
          console.error('Failed to parse alert event', err)
        }
      })

      source.onerror = () => {
        if (source.readyState === EventSource.CLOSED) {
          openSources.delete(source)
          connected.value = openSources.size > 0
        }
      }
    })
  }

  const disconnect = () => {
    sources.forEach(source => source.close())
    sources.length = 0
    openSources.clear()
    connected.value = false
  }

  onMounted(connect)
  onUnmounted(disconnect)

  return { connected, disconnect }
}
