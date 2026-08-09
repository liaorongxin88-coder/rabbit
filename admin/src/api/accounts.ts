import { deleteJson, getJson, postJson, putJson } from '@/lib/request'
import type { AdminAccount, AdminRole, PageResult } from '@/types/api'

export interface AdminAccountListParams {
  page: number
  pageSize: number
  keyword?: string
}

export interface AdminAccountPayload {
  userName: string
  password?: string
  role: AdminRole
  enabled: boolean
}

export function listAdminAccounts(params: AdminAccountListParams) {
  return getJson<PageResult<AdminAccount>>('/api/admin/accounts', {
    page: params.page,
    pageSize: params.pageSize,
    keyword: params.keyword || undefined,
  }).send()
}

export function createAdminAccount(payload: AdminAccountPayload & { password: string }) {
  return postJson<AdminAccount>('/api/admin/accounts', payload).send()
}

export function updateAdminAccount(accountId: number, payload: AdminAccountPayload) {
  return putJson<AdminAccount>(`/api/admin/accounts/${accountId}`, payload).send()
}

export function deleteAdminAccount(accountId: number) {
  return deleteJson<void>(`/api/admin/accounts/${accountId}`).send()
}
