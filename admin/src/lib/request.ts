import { createAlova } from 'alova'
import adapterFetch from 'alova/fetch'
import { toast } from 'sonner'
import {
  clearSession,
  getToken,
  clearWorkspaceSession,
  getWorkspaceToken,
} from '@/lib/auth'
import {
  shouldClearRequestSession,
  type RequestSessionScope,
} from '@/lib/request-auth'
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

function createRequestClient(
  token: () => string,
  clearAuth: () => void,
  scope: RequestSessionScope,
) {
  return createAlova({
    baseURL: API_BASE_URL,
    requestAdapter: adapterFetch(),
    beforeRequest(method) {
      const headers: Record<string, string> = {
        ...(method.config.headers as Record<string, string> | undefined),
      }
      if (method.data instanceof FormData) {
        delete headers['Content-Type']
        delete headers['content-type']
      }
      const accessToken = token()
      if (accessToken) {
        headers.Authorization = `Bearer ${accessToken}`
      }
      method.config.headers = headers
    },
    responded: {
      async onSuccess(response) {
        const payload = await parseApiResponse(response)
        if (!payload || typeof payload.code !== 'number') {
          throw new Error('响应格式不合法')
        }
        if (payload.code !== 0) {
          const message = payload.message || '请求失败'
          if (shouldClearRequestSession(scope, payload.code, message)) {
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

const alova = createRequestClient(getToken, clearSession, 'admin')
const workspaceAlova = createRequestClient(
  getWorkspaceToken,
  clearWorkspaceSession,
  'workspace',
)

export function getJson<T>(url: string, params?: Record<string, unknown>) {
  return alova.Get<T>(url, {
    params,
    cacheFor: 0,
  })
}

export function postJson<T>(url: string, data?: JsonBody) {
  return alova.Post<T>(url, data)
}

export function postForm<T>(url: string, data: FormData) {
  return alova.Post<T>(url, data)
}

export function putJson<T>(url: string, data?: JsonBody) {
  return alova.Put<T>(url, data)
}

export function deleteJson<T>(url: string) {
  return alova.Delete<T>(url)
}

export async function downloadAdminFile(path: string, fileName: string) {
  const token = getToken()
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    const payload = (await response.json()) as ApiResponse<unknown>
    const message = payload.message || '下载失败'
    if (payload.code !== 0) {
      if (shouldClearRequestSession('admin', payload.code, message)) {
        const hadSession = Boolean(token)
        clearSession()
        if (hadSession) {
          toast.error(message)
        }
      } else {
        toast.error(message)
      }
      throw new Error(message)
    }
  }
  if (!response.ok) {
    const message = '下载安装包失败'
    toast.error(message)
    throw new Error(message)
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName
  document.body.append(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(objectUrl)
}

interface WorkspaceRequestOptions {
  houseId?: number | null
  params?: Record<string, unknown>
}

function workspaceConfig(options?: WorkspaceRequestOptions) {
  return {
    params: options?.params,
    headers: options?.houseId
      ? { 'X-House-Id': String(options.houseId) }
      : undefined,
  }
}

export function workspaceGetJson<T>(url: string, options?: WorkspaceRequestOptions) {
  return workspaceAlova.Get<T>(url, {
    ...workspaceConfig(options),
    cacheFor: 0,
  })
}

export function workspacePostJson<T>(
  url: string,
  data?: JsonBody,
  options?: WorkspaceRequestOptions,
) {
  return workspaceAlova.Post<T>(url, data, workspaceConfig(options))
}

export function workspacePutJson<T>(
  url: string,
  data?: JsonBody,
  options?: WorkspaceRequestOptions,
) {
  return workspaceAlova.Put<T>(url, data, workspaceConfig(options))
}

export function workspaceDeleteJson<T>(url: string, options?: WorkspaceRequestOptions) {
  return workspaceAlova.Delete<T>(url, undefined, workspaceConfig(options))
}
