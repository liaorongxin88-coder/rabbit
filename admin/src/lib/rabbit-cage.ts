import { COMMODITY_CAPACITY } from './cage-map.ts'
import type { Cage } from '@/types/api'

const cageStatusByRabbitType: Record<string, string> = {
  '0': '1',
  '1': '2',
  '2': '3',
}

export function preferredRabbitTypeForCage(cage: Cage | null | undefined): string {
  if (cage?.status === '2') return '1'
  if (cage?.status === '3') return '2'
  return '0'
}

export function rabbitCageValidationMessage(
  cage: Cage | null | undefined,
  rabbitType: string,
  houseId: number,
): string | null {
  if (!cage) return '请选择有效笼位'
  if (cage.houseId !== houseId) return '笼位不属于当前兔场'
  if (!cage.isEnabled) return '笼位已停用'

  if (cage.status !== '0' && cage.status !== cageStatusByRabbitType[rabbitType]) {
    return '笼位用途与兔只类型不匹配'
  }
  if ((cage.status === '1' || cage.status === '2') && cage.rabbitCount >= 1) {
    return '笼位已满，最多容纳 1 只'
  }
  if (cage.status === '3' && cage.rabbitCount >= COMMODITY_CAPACITY) {
    return `笼位已满，最多容纳 ${COMMODITY_CAPACITY} 只`
  }

  return null
}

export function isReplacementTargetCage(cage: Cage, houseId: number): boolean {
  return (
    cage.houseId === houseId
    && cage.isEnabled
    && cage.rabbitCount === 0
    && (cage.status === '0' || cage.status === '2')
  )
}
