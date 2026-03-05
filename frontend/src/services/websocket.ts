const WS_BASE_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080'

export interface AuditEvent {
  timestamp: string
  protocol: string
  srcIp: string
  dstIp: string
  srcPort: number
  dstPort: number
  payload?: string
}

export class WebSocketClient {
  private ws: WebSocket | null = null
  private reconnectTimer: number | null = null
  private reconnectDelay = 3000

  constructor(
    private endpoint: string,
    private onMessage: (event: AuditEvent) => void,
    private onError?: (error: Event) => void
  ) {}

  connect(): void {
    const url = `${WS_BASE_URL}${this.endpoint}`
    console.log('Connecting to WebSocket:', url)

    this.ws = new WebSocket(url)

    this.ws.onopen = () => {
      console.log('WebSocket connected')
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer)
        this.reconnectTimer = null
      }
    }

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        this.onMessage(data)
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error)
      }
    }

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error)
      if (this.onError) {
        this.onError(error)
      }
    }

    this.ws.onclose = () => {
      console.log('WebSocket closed, reconnecting...')
      this.reconnect()
    }
  }

  private reconnect(): void {
    if (this.reconnectTimer) return

    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, this.reconnectDelay)
  }

  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }

  send(data: any): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }
}
