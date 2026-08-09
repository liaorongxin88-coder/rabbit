import { createContext, useContext } from 'react'
import type { HousePermission, RabbitHouse, WorkspaceSession } from '@/types/api'
export { hasPermission } from '@/lib/permissions'

export interface WorkspaceValue {
  session: WorkspaceSession
  loading: boolean
  error: string | null
  houses: RabbitHouse[]
  selectedHouse: RabbitHouse | null
  permission: HousePermission | null
  canCreateHouse: boolean
  selectHouse: (houseId: number) => void
  refresh: () => Promise<void>
}

export const WorkspaceContext = createContext<WorkspaceValue | null>(null)

export function useWorkspace() {
  const value = useContext(WorkspaceContext)
  if (!value) {
    throw new Error('useWorkspace must be used inside WorkspaceProvider')
  }
  return value
}
