import { createAlova } from 'alova'
import adapterFetch from 'alova/fetch'
import { toast } from 'sonner'
import { clearSession, getToken } from '@/lib/auth'
import type { ApiResponse } from '@/types/api'

type JsonBody = object | string

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

const alova = createAlova({
  baseURL: API_BASE_URL,
  requestAdapter: adapterFetch(),
  beforeRequest(method) {
    const token = getToken()
    if (token) {
      method.config.headers = {
        ...method.config.headers,
        Authorization: `Bearer ${token}`,
      }
    }
  },
  responded: {
    async onSuccess(response) {
      const payload = (await response.json()) as ApiResponse<unknown>
      if (!payload || typeof payload.code !== 'number') {
        throw new Error('响应格式不合法')
      }
      if (payload.code !== 0) {
        if (payload.code === 401) {
          clearSession()
        }
        throw new Error(payload.message || '请求失败')
      }
      return payload.data
    },
    onError(error) {
      const message = error instanceof Error ? error.message : '网络请求失败'
      toast.error(message)
      throw error
    },
  },
})

export function getJson<T>(url: string, params?: Record<string, unknown>) {
  return alova.Get<T>(url, {
    params,
  })
}

export function postJson<T>(url: string, data?: JsonBody) {
  return alova.Post<T>(url, data)
}

export function putJson<T>(url: string, data?: JsonBody) {
  return alova.Put<T>(url, data)
}

export function deleteJson<T>(url: string) {
  return alova.Delete<T>(url)
}
