import { useCallback, useEffect, useMemo, useState } from 'react'
import { getHousePermission, listWorkspaceHouses } from '@/api/workspace'
import { getWorkspaceSelection, setWorkspaceSelection } from '@/lib/auth'
import {
  WorkspaceContext,
  type WorkspaceValue,
} from '@/lib/workspace'
import type { HousePermission, RabbitHouse, WorkspaceSession } from '@/types/api'

export function WorkspaceProvider({
  session,
  children,
}: {
  session: WorkspaceSession
  children: React.ReactNode
}) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [houses, setHouses] = useState<RabbitHouse[]>([])
  const [houseId, setHouseId] = useState<number | null>(null)
  const [permission, setPermission] = useState<HousePermission | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const nextHouses = await listWorkspaceHouses()
      const saved = getWorkspaceSelection(session.userId)
      setHouses(nextHouses)
      setHouseId((currentHouseId) => {
        const preferredHouseId = currentHouseId ?? saved?.houseId ?? null
        return nextHouses.some((house) => house.id === preferredHouseId)
          ? preferredHouseId
          : nextHouses[0]?.id ?? null
      })
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '工作台加载失败')
      setHouses([])
      setHouseId(null)
    } finally {
      setLoading(false)
    }
  }, [session.userId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!houseId) {
      setPermission(null)
      return
    }
    let active = true
    setPermission(null)
    void getHousePermission(houseId)
      .then((nextPermission) => {
        if (active) {
          setPermission(nextPermission)
        }
      })
      .catch(() => {
        if (active) {
          setPermission(null)
        }
      })
    return () => {
      active = false
    }
  }, [houseId])

  useEffect(() => {
    setWorkspaceSelection({ userId: session.userId, houseId })
  }, [houseId, session.userId])

  const selectedHouse = useMemo(
    () => houses.find((house) => house.id === houseId) ?? null,
    [houseId, houses],
  )

  const selectHouse = useCallback((nextHouseId: number) => {
    if (houses.some((house) => house.id === nextHouseId)) {
      setHouseId(nextHouseId)
    }
  }, [houses])

  const value = useMemo<WorkspaceValue>(
    () => ({
      session,
      loading,
      error,
      houses,
      selectedHouse,
      permission,
      canCreateHouse: session.canCreateHouse !== false,
      selectHouse,
      refresh,
    }),
    [
      error,
      houses,
      loading,
      permission,
      refresh,
      selectedHouse,
      selectHouse,
      session,
    ],
  )

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>
}
