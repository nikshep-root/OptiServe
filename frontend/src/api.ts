import axios from 'axios'
import type { AssignmentNextResponse, AuthTokenResponse, QueueEntry, Resource, ServiceRequest, ServiceType } from './types'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/',
  timeout: 12000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('optiserve-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export const authApi = {
  async login(email: string, password: string) {
    const { data } = await api.post<AuthTokenResponse>('/api/auth/login', { email, password })
    return data
  },
  async register(email: string, password: string) {
    const { data } = await api.post<AuthTokenResponse>('/api/auth/register', { email, password })
    return data
  },
}

export const operationsApi = {
  async getServiceRequests() {
    const { data } = await api.get<ServiceRequest[]>('/api/service-requests')
    return data
  },
  async getQueue() {
    const { data } = await api.get<QueueEntry[]>('/api/queue')
    return data
  },
  async getResources() {
    const { data } = await api.get<Resource[]>('/api/resources')
    return data
  },
  async getServiceTypes() {
    const { data } = await api.get<ServiceType[]>('/api/service-types')
    return data
  },
  async createServiceType(payload: { name: string; description: string; defaultServiceDurationSeconds: number; active: boolean }) {
    const { data } = await api.post<ServiceType>('/api/service-types', payload)
    return data
  },
  async deleteServiceType(id: string) {
    await api.delete(`/api/service-types/${id}`)
  },
  async createResource(payload: { name: string }) {
    const { data } = await api.post<Resource>('/api/resources', payload)
    return data
  },
  async deleteResource(id: string) {
    await api.delete(`/api/resources/${id}`)
  },
  async assignNext() {
    const { data } = await api.post<AssignmentNextResponse>('/api/assignments/next')
    return data
  },
}

export default api