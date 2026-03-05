import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 响应拦截器
apiClient.interceptors.response.use(
  response => response,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export interface SystemStats {
  totalPackets: number
  protocolStats: Record<string, number>
}

export const api = {
  // 健康检查
  async healthCheck(): Promise<{ status: string }> {
    const response = await apiClient.get('/health')
    return response.data
  },

  // 获取系统统计
  async getStats(): Promise<SystemStats> {
    const response = await apiClient.get('/api/stats')
    return response.data
  }
}
