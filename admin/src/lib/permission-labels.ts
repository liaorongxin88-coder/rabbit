import type { HouseRole, MerchantRole } from '@/types/api'

export const houseRoleLabels: Record<HouseRole, string> = {
  OWNER: '兔场所有者',
  MERCHANT_OWNER: '商户所有者',
  MANAGER: '兔场管理员',
  STAFF: '生产人员',
  VIEWER: '查看者',
}

export const merchantRoleLabels: Record<MerchantRole, string> = {
  OWNER: '商户所有者',
  ADMIN: '商户管理员',
  MEMBER: '商户成员',
}

export function houseRoleLabel(role: HouseRole) {
  return houseRoleLabels[role]
}

export function merchantRoleLabel(role: MerchantRole) {
  return merchantRoleLabels[role]
}
