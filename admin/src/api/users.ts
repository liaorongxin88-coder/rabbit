import { getJson, putJson } from '@/lib/request'
import type { BusinessUser, PageResult, UserStatus } from '@/types/api'

export interface BusinessUserListParams {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: UserStatus | 'ALL'
}

export function listBusinessUsers(params: BusinessUserListParams) {
  return getJson<PageResult<BusinessUser>>('/api/admin/users', {
    pageNum: params.pageNum,
    pageSize: params.pageSize,
    keyword: params.keyword || undefined,
    status: params.status === 'ALL' ? undefined : params.status,
  }).send()
}

export function updateBusinessUserStatus(userId: number, status: UserStatus) {
  return putJson<BusinessUser>(`/api/admin/users/${userId}/status`, { status }).send()
}
