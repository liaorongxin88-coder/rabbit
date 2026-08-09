import { Badge } from '@/components/ui/badge'
import { houseRoleLabels } from '@/lib/permission-labels'
import type { HousePermission, HouseRole } from '@/types/api'

export function HousePermissionBadge({ permission }: { permission: HousePermission | null }) {
  return (
    <Badge variant={permission?.isAdmin ? 'default' : 'secondary'}>
      {permission ? houseRoleLabels[permission.role] : '权限加载中'}
    </Badge>
  )
}

export function HouseRoleBadge({ role }: { role: HouseRole }) {
  return <Badge variant={role === 'OWNER' ? 'default' : 'secondary'}>{houseRoleLabels[role]}</Badge>
}
