import axios from 'axios'
import type { AlertRecord, AuditEvent, DashboardStats } from '@/types/models'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8080',
  timeout: 10000
})

export const auditApi = {
  getLogs: (params: {
    page?: number
    size?: number
    protocol?: string
    srcIp?: string
    start?: string
    end?: string
  }) => api.get<AuditEvent[]>('/api/audit/logs', { params }),

  getRecent: (limit = 20) =>
    api.get<AuditEvent[]>('/api/audit/recent', { params: { limit } })
}

export const alertApi = {
  getRecent: (limit = 20) =>
    api.get<AlertRecord[]>('/api/alerts/recent', { params: { limit } }),

  getStats: () => api.get<Record<string, number>>('/api/alerts/stats')
}

export const statsApi = {
  getDashboard: () => api.get<DashboardStats>('/api/stats/dashboard'),
  getProtocols: () => api.get<Record<string, number>>('/api/stats/protocols')
}
