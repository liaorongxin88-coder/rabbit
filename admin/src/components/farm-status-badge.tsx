import { Badge } from '@/components/ui/badge'
import type { FarmStatus } from '@/types/api'

export function FarmStatusBadge({ status }: { status: FarmStatus }) {
  if (status === 'ENABLED') {
    return <Badge>启用</Badge>
  }
  if (status === 'ORPHANED') {
    return <Badge variant="destructive">缺少所有者</Badge>
  }
  return <Badge variant="secondary">已暂停</Badge>
}
