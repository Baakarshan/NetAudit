import axios from 'axios'
import type { AlertRecord, AuditEvent, DashboardStats } from '@/types/models'

export const getApiBaseUrls = () => {
  const raw = import.meta.env.VITE_API_BASES || import.meta.env.VITE_API_BASE || 'http://localhost:8080'
  const list = raw
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
    .map(item => item.replace(/\/$/, ''))
  return Array.from(new Set(list))
}

const api = axios.create({
  baseURL: getApiBaseUrls()[0],
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
