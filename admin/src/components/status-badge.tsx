import { Badge } from '@/components/ui/badge'
import type { MerchantStatus } from '@/types/api'

export function StatusBadge({ status }: { status: MerchantStatus }) {
  if (status === 'ENABLED') {
    return <Badge>启用</Badge>
  }
  return <Badge variant="secondary">停用</Badge>
}
