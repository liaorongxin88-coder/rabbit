import { getJson, postJson, putJson } from '@/lib/request'
import type {
  Merchant,
  MerchantOverview,
  MerchantStatus,
  MerchantAccountSummary,
  MerchantHousePolicy,
  MerchantRole,
  MembershipStatus,
  PageResult,
} from '@/types/api'

export interface MerchantListParams {
  page: number
  pageSize: number
  keyword?: string
  status?: MerchantStatus | 'ALL'
}

export interface MerchantBasicPayload {
  name: string
  contactName?: string
  contactPhone?: string
  remark?: string
}

export interface CreateMerchantPayload extends MerchantBasicPayload {
  userName: string
  password: string
  confirmPassword: string
}

export interface CreateMerchantAccountPayload {
  userName: string
  password: string
  confirmPassword: string
  role: Exclude<MerchantRole, 'OWNER'>
}

export interface MerchantHousePolicyPayload {
  houseCreationEnabled: boolean
  houseMemberManagementEnabled: boolean
  maxHouseCount: number
  maxMembersPerHouse: number
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

export function createMerchant(payload: CreateMerchantPayload) {
  return postJson<Merchant>('/api/admin/merchants', payload).send()
}

export function updateMerchant(merchantId: number, payload: MerchantBasicPayload) {
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

export function listMerchantAccounts(merchantId: number) {
  return getJson<MerchantAccountSummary[]>(
    `/api/admin/merchants/${merchantId}/accounts`,
  ).send()
}

export function createMerchantAccount(
  merchantId: number,
  payload: CreateMerchantAccountPayload,
) {
  return postJson<void>(
    `/api/admin/merchants/${merchantId}/accounts`,
    payload,
  ).send()
}

export function getMerchantOverview(merchantId: number) {
  return getJson<MerchantOverview>(
    `/api/admin/merchants/${merchantId}/overview`,
  ).send()
}

export function getMerchantHousePolicy(merchantId: number) {
  return getJson<MerchantHousePolicy>(
    `/api/admin/merchants/${merchantId}/house-policy`,
  ).send()
}

export function updateMerchantHousePolicy(
  merchantId: number,
  payload: MerchantHousePolicyPayload,
) {
  return putJson<MerchantHousePolicy>(
    `/api/admin/merchants/${merchantId}/house-policy`,
    payload,
  ).send()
}

export function updateMerchantMembership(
  merchantId: number,
  userId: number,
  payload: { role: MerchantRole; status: MembershipStatus },
) {
  return putJson<void>(
    `/api/admin/merchants/${merchantId}/accounts/${userId}/membership`,
    payload,
  ).send()
}
