import { downloadAdminFile, getJson, postForm, postJson, putJson } from '@/lib/request'
import type {
  AppRelease,
  AppReleaseChannel,
  AppReleaseStatus,
  PageResult,
} from '@/types/api'

export interface AppReleaseListParams {
  pageNum: number
  pageSize: number
  channel?: AppReleaseChannel | 'ALL'
  status?: AppReleaseStatus | 'ALL'
}

export interface CreateAppReleasePayload {
  channel: AppReleaseChannel
  versionName: string
  versionCode: number
  releaseNotes?: string
  forceUpdate: boolean
  requestId: string
  file: File
}

export function listAppReleases(params: AppReleaseListParams) {
  return getJson<PageResult<AppRelease>>('/api/admin/app-releases', {
    pageNum: params.pageNum,
    pageSize: params.pageSize,
    channel: params.channel === 'ALL' ? undefined : params.channel,
    status: params.status === 'ALL' ? undefined : params.status,
  }).send()
}

export function createAppRelease(payload: CreateAppReleasePayload) {
  const body = new FormData()
  body.append('channel', payload.channel)
  body.append('versionName', payload.versionName)
  body.append('versionCode', String(payload.versionCode))
  body.append('requestId', payload.requestId)
  body.append('forceUpdate', String(payload.forceUpdate))
  if (payload.releaseNotes?.trim()) {
    body.append('releaseNotes', payload.releaseNotes.trim())
  }
  body.append('file', payload.file)
  return postForm<AppRelease>('/api/admin/app-releases', body).send()
}

export function updateAppRelease(
  id: string,
  payload: { releaseNotes?: string; forceUpdate?: boolean },
) {
  return putJson<AppRelease>(`/api/admin/app-releases/${id}`, payload).send()
}

export function publishAppRelease(id: string) {
  return postJson<AppRelease>(`/api/admin/app-releases/${id}/publish`).send()
}

export function revokeAppRelease(id: string) {
  return postJson<AppRelease>(`/api/admin/app-releases/${id}/revoke`).send()
}

export function downloadAppReleaseApk(release: AppRelease) {
  return downloadAdminFile(`/api/admin/app-releases/${release.id}/apk`, release.fileName)
}
