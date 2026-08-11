import { getJson, postJson, putJson } from '@/lib/request'
import type {
  AdminFarm,
  AdminFarmMember,
  CreateAdminFarmRequest,
  FarmOverview,
  FarmStatus,
  HouseRole,
  PageResult,
  UpdateAdminFarmRequest,
} from '@/types/api'

export interface FarmListParams {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: FarmStatus | 'ALL'
}

export function listFarms(params: FarmListParams) {
  return getJson<PageResult<AdminFarm>>('/api/admin/farms', {
    pageNum: params.pageNum,
    pageSize: params.pageSize,
    keyword: params.keyword || undefined,
    status: params.status === 'ALL' ? undefined : params.status,
  }).send()
}

export function createFarm(payload: CreateAdminFarmRequest) {
  return postJson<AdminFarm>('/api/admin/farms', payload).send()
}

export function getFarmOverview(farmId: number) {
  return getJson<FarmOverview>(`/api/admin/farms/${farmId}/overview`).send()
}

export function listFarmMembers(farmId: number) {
  return getJson<AdminFarmMember[]>(`/api/admin/farms/${farmId}/members`).send()
}

export function addFarmMember(
  farmId: number,
  payload: { userId: number; role: HouseRole },
) {
  return postJson<void>(`/api/admin/farms/${farmId}/members`, payload).send()
}

export function updateFarm(farmId: number, payload: UpdateAdminFarmRequest) {
  return putJson<AdminFarm>(`/api/admin/farms/${farmId}`, payload).send()
}

export function updateFarmStatus(farmId: number, status: FarmStatus) {
  return putJson<AdminFarm>(`/api/admin/farms/${farmId}/status`, { status }).send()
}
