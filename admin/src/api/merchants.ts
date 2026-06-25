import { deleteJson, getJson, postJson, putJson } from '@/lib/request'
import type {
  Merchant,
  MerchantOverview,
  MerchantStatus,
  MerchantUser,
  PageResult,
} from '@/types/api'

export interface MerchantListParams {
  page: number
  pageSize: number
  keyword?: string
  status?: MerchantStatus | 'ALL'
}

export interface MerchantPayload {
  name: string
  contactName?: string
  contactPhone?: string
  remark?: string
}

export function listMerchants(params: MerchantListParams) {
  return getJson<PageResult<Merchant>>('/api/admin/merchants', {
    page: params.page,
    pageSize: params.pageSize,
    keyword: params.keyword || undefined,
    status: params.status === 'ALL' ? undefined : params.status,
  }).send()
}

export function getMerchant(merchantId: number) {
  return getJson<Merchant>(`/api/admin/merchants/${merchantId}`).send()
}

export function createMerchant(payload: MerchantPayload) {
  return postJson<Merchant>('/api/admin/merchants', payload).send()
}

export function updateMerchant(merchantId: number, payload: MerchantPayload) {
  return putJson<Merchant>(`/api/admin/merchants/${merchantId}`, payload).send()
}

export function updateMerchantStatus(
  merchantId: number,
  status: MerchantStatus,
) {
  return putJson<Merchant>(`/api/admin/merchants/${merchantId}/status`, {
    status,
  }).send()
}

export function listMerchantUsers(merchantId: number) {
  return getJson<MerchantUser[]>(
    `/api/admin/merchants/${merchantId}/users`,
  ).send()
}

export function addMerchantUser(merchantId: number, userId: number) {
  return postJson<void>(`/api/admin/merchants/${merchantId}/users`, {
    userId,
  }).send()
}

export function removeMerchantUser(merchantId: number, userId: number) {
  return deleteJson<void>(
    `/api/admin/merchants/${merchantId}/users/${userId}`,
  ).send()
}

export function getMerchantOverview(merchantId: number) {
  return getJson<MerchantOverview>(
    `/api/admin/merchants/${merchantId}/overview`,
  ).send()
}
