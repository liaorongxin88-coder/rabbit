import { createContext, useContext } from 'react'
import type {
  HousePermission,
  MerchantMembership,
  MerchantSession,
  RabbitHouse,
} from '@/types/api'
export { hasPermission } from '@/lib/permissions'

export interface MerchantWorkspaceValue {
  session: MerchantSession
  loading: boolean
  error: string | null
  memberships: MerchantMembership[]
  houses: RabbitHouse[]
  selectedMerchant: MerchantMembership | null
  selectedHouse: RabbitHouse | null
  merchantHouses: RabbitHouse[]
  permission: HousePermission | null
  canCreateHouse: boolean
  selectMerchant: (merchantId: number) => void
  selectHouse: (houseId: number) => void
  refresh: () => Promise<void>
}

export const MerchantWorkspaceContext = createContext<MerchantWorkspaceValue | null>(null)

export function useMerchantWorkspace() {
  const value = useContext(MerchantWorkspaceContext)
  if (!value) {
    throw new Error('useMerchantWorkspace must be used inside MerchantWorkspaceProvider')
  }
  return value
}
