import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react'
import {
  getHousePermission,
  listMerchantMemberships,
  listWorkspaceHouses,
} from '@/api/workspace'
import {
  getMerchantSelection,
  setMerchantSelection,
} from '@/lib/auth'
import {
  hasPermission,
  MerchantWorkspaceContext,
  type MerchantWorkspaceValue,
} from '@/lib/merchant-workspace'
import type {
  HousePermission,
  MerchantMembership,
  MerchantSession,
  RabbitHouse,
} from '@/types/api'

export function MerchantWorkspaceProvider({
  session,
  children,
}: {
  session: MerchantSession
  children: React.ReactNode
}) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [memberships, setMemberships] = useState<MerchantMembership[]>([])
  const [houses, setHouses] = useState<RabbitHouse[]>([])
  const [merchantId, setMerchantId] = useState<number | null>(null)
  const [houseId, setHouseId] = useState<number | null>(null)
  const [permission, setPermission] = useState<HousePermission | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [nextMemberships, nextHouses] = await Promise.all([
        listMerchantMemberships(),
        listWorkspaceHouses(),
      ])
      const activeMemberships = nextMemberships.filter(
        (item) =>
          item.membershipStatus === 'ENABLED' && item.merchantStatus === 'ENABLED',
      )
      const saved = getMerchantSelection(session.userId)
      const nextMerchantId = activeMemberships.some(
        (item) => item.merchantId === saved?.merchantId,
      )
        ? saved?.merchantId ?? null
        : activeMemberships[0]?.merchantId ?? null
      const merchantHouses = nextHouses.filter(
        (house) => house.merchantId === nextMerchantId,
      )
      const nextHouseId = merchantHouses.some((house) => house.id === saved?.houseId)
        ? saved?.houseId ?? null
        : merchantHouses[0]?.id ?? null

      setMemberships(nextMemberships)
      setHouses(nextHouses)
      setMerchantId(nextMerchantId)
      setHouseId(nextHouseId)
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '工作台加载失败')
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
    setMerchantSelection({ userId: session.userId, merchantId, houseId })
  }, [houseId, merchantId, session.userId])

  const selectedMerchant = useMemo(
    () => memberships.find((item) => item.merchantId === merchantId) ?? null,
    [memberships, merchantId],
  )
  const merchantHouses = useMemo(
    () => houses.filter((house) => house.merchantId === merchantId),
    [houses, merchantId],
  )
  const selectedHouse = useMemo(
    () => merchantHouses.find((house) => house.id === houseId) ?? null,
    [houseId, merchantHouses],
  )

  const selectMerchant = useCallback((nextMerchantId: number) => {
    setMerchantId(nextMerchantId)
    const nextHouse = houses.find((house) => house.merchantId === nextMerchantId)
    setHouseId(nextHouse?.id ?? null)
  }, [houses])

  const selectHouse = useCallback((nextHouseId: number) => {
    if (merchantHouses.some((house) => house.id === nextHouseId)) {
      setHouseId(nextHouseId)
    }
  }, [merchantHouses])

  const value = useMemo<MerchantWorkspaceValue>(
    () => ({
      session,
      loading,
      error,
      memberships,
      houses,
      selectedMerchant,
      selectedHouse,
      merchantHouses,
      permission,
      canCreateHouse: hasPermission(selectedMerchant, 'merchant:houses:add'),
      selectMerchant,
      selectHouse,
      refresh,
    }),
    [
      error,
      houses,
      loading,
      memberships,
      merchantHouses,
      permission,
      refresh,
      selectedHouse,
      selectedMerchant,
      selectHouse,
      selectMerchant,
      session,
    ],
  )

  return (
    <MerchantWorkspaceContext.Provider value={value}>
      {children}
    </MerchantWorkspaceContext.Provider>
  )
}
