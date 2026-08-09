import type { HouseRole } from '@/types/api'

export const houseRoleLabels: Record<HouseRole, string> = {
  OWNER: '兔场所有者',
  MANAGER: '兔场管理员',
  STAFF: '生产人员',
  VIEWER: '查看者',
}

export function houseRoleLabel(role: HouseRole) {
  return houseRoleLabels[role]
}
