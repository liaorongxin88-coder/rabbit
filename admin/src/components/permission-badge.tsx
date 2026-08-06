import { Badge } from '@/components/ui/badge'
import { houseRoleLabels, merchantRoleLabels } from '@/lib/permission-labels'
import type { HousePermission, MerchantRole } from '@/types/api'

export function HousePermissionBadge({ permission }: { permission: HousePermission | null }) {
  return (
    <Badge variant={permission?.isAdmin ? 'default' : 'secondary'}>
      {permission ? houseRoleLabels[permission.role] : '权限加载中'}
    </Badge>
  )
}

export function MerchantRoleBadge({ role }: { role: MerchantRole }) {
  return <Badge variant={role === 'OWNER' ? 'default' : 'secondary'}>{merchantRoleLabels[role]}</Badge>
}
