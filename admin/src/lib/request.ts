import { createAlova } from 'alova'
import adapterFetch from 'alova/fetch'
import { toast } from 'sonner'
import {
  clearMerchantSession,
  clearSession,
  getMerchantToken,
  getToken,
} from '@/lib/auth'
import type { ApiResponse } from '@/types/api'

type JsonBody = object | string

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

function getRequestErrorMessage(error: unknown) {
  if (error instanceof TypeError && error.message === 'Failed to fetch') {
    return '无法连接 API 服务，请检查网络或跨域配置'
  }
  return error instanceof Error ? error.message : '网络请求失败'
}

async function parseApiResponse(response: Response) {
  const text = await response.text()
  if (!text.trim()) {
    throw new Error(`服务返回空响应：HTTP ${response.status}`)
  }

  try {
    return JSON.parse(text) as ApiResponse<unknown>
  } catch {
    const proxyHint =
      response.status === 404
        ? '，请检查 VITE_API_BASE_URL 或服务器 /api 反向代理配置'
        : ''
    throw new Error(`服务返回非 JSON 响应：HTTP ${response.status}${proxyHint}`)
  }
}

function createRequestClient(token: () => string, clearAuth: () => void) {
  return createAlova({
    baseURL: API_BASE_URL,
    requestAdapter: adapterFetch(),
    beforeRequest(method) {
      const accessToken = token()
      if (accessToken) {
        method.config.headers = {
          ...method.config.headers,
          Authorization: `Bearer ${accessToken}`,
        }
      }
    },
    responded: {
      async onSuccess(response) {
        const payload = await parseApiResponse(response)
        if (!payload || typeof payload.code !== 'number') {
          throw new Error('响应格式不合法')
        }
        if (payload.code !== 0) {
          const message = payload.message || '请求失败'
          if (payload.code === 401) {
            const hadSession = Boolean(token())
            clearAuth()
            if (hadSession) {
              toast.error(message)
            }
            throw new Error(message)
          }
          toast.error(message)
          throw new Error(message)
        }
        return payload.data
      },
      onError(error) {
        const message = getRequestErrorMessage(error)
        toast.error(message)
        throw new Error(message)
      },
    },
  })
}

const alova = createRequestClient(getToken, clearSession)
const merchantAlova = createRequestClient(getMerchantToken, clearMerchantSession)

export function getJson<T>(url: string, params?: Record<string, unknown>) {
  return alova.Get<T>(url, {
    params,
    cacheFor: 0,
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

interface MerchantRequestOptions {
  houseId?: number | null
  params?: Record<string, unknown>
}

function merchantConfig(options?: MerchantRequestOptions) {
  return {
    params: options?.params,
    headers: options?.houseId
      ? { 'X-House-Id': String(options.houseId) }
      : undefined,
  }
}

export function merchantGetJson<T>(url: string, options?: MerchantRequestOptions) {
  return merchantAlova.Get<T>(url, {
    ...merchantConfig(options),
    cacheFor: 0,
  })
}

export function merchantPostJson<T>(
  url: string,
  data?: JsonBody,
  options?: MerchantRequestOptions,
) {
  return merchantAlova.Post<T>(url, data, merchantConfig(options))
}

export function merchantPutJson<T>(
  url: string,
  data?: JsonBody,
  options?: MerchantRequestOptions,
) {
  return merchantAlova.Put<T>(url, data, merchantConfig(options))
}

export function merchantDeleteJson<T>(url: string, options?: MerchantRequestOptions) {
  return merchantAlova.Delete<T>(url, undefined, merchantConfig(options))
}
