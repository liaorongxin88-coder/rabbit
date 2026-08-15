import type {
  BatchRabbit,
  BulkMatingRequest,
  RabbitDepartureRequest,
} from '@/types/api'

export type BatchStatusInput = string | null | undefined

export const MAX_BULK_MATING_MOTHERS = 1000
export const BATCH_MOTHER_PAGE_SIZE = 50

const batchStatusAliases: Record<string, string> = {
  PLANNED: '计划中',
  ACTIVE: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

/** Normalize persisted batch statuses before comparing or rendering them. */
export function normalizeBatchStatus(status: BatchStatusInput) {
  const normalized = status?.trim() ?? ''
  return batchStatusAliases[normalized] ?? normalized
}

export function isCompletedBatchStatus(status: BatchStatusInput) {
  return normalizeBatchStatus(status) === '已完成'
}

export function batchStatusLabel(status: BatchStatusInput) {
  return normalizeBatchStatus(status) || '-'
}

export function normalizeParturitionCounts(
  failed: boolean,
  totalKits: number,
  liveKits: number,
) {
  return failed ? { totalKits: 0, liveKits: 0 } : { totalKits, liveKits }
}

export function normalizeParturitionPayload(
  failed: boolean,
  totalKits: number,
  liveKits: number,
) {
  return {
    ...normalizeParturitionCounts(failed, totalKits, liveKits),
    failed,
  }
}

export function isBulkMatingEligible(
  rabbit: Pick<BatchRabbit, 'isActive' | 'rabbitType' | 'rabbitGender' | 'currentStatus'>,
) {
  const status = rabbit.currentStatus?.trim()
  return rabbit.isActive
    && rabbit.rabbitType === '0'
    && rabbit.rabbitGender === '0'
    && (status === '待配种' || status === '哺乳中')
}

type BulkMatingPayload = Omit<BulkMatingRequest, 'requestId'>

function sameNumberList(left: number[], right: number[]) {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

export function normalizeBulkMatingPayload(payload: BulkMatingPayload): BulkMatingPayload {
  return {
    ...payload,
    femaleRabbitIds: [...new Set(payload.femaleRabbitIds)].sort((left, right) => left - right),
  }
}

export function getOrCreateBulkMatingRequest(
  current: BulkMatingRequest | null,
  payload: BulkMatingPayload,
  createRequestId: () => string,
): BulkMatingRequest {
  const normalized = normalizeBulkMatingPayload(payload)
  if (
    current
    && current.maleRabbitId === normalized.maleRabbitId
    && current.matingDate === normalized.matingDate
    && sameNumberList(current.femaleRabbitIds, normalized.femaleRabbitIds)
  ) {
    return current
  }
  return { ...normalized, requestId: createRequestId() }
}

export function bulkMatingPath(batchId: number) {
  return `/api/batches/${batchId}/mating/bulk`
}

type RabbitDeparturePayload = Omit<RabbitDepartureRequest, 'requestId'>

export function getOrCreateRabbitDepartureRequest(
  current: RabbitDepartureRequest | null,
  payload: RabbitDeparturePayload,
  createRequestId: () => string,
): RabbitDepartureRequest {
  if (
    current
    && current.rabbitId === payload.rabbitId
    && current.eventType === payload.eventType
    && current.actionDate === payload.actionDate
    && current.reason === payload.reason
    && current.remark === payload.remark
    && current.forceExitBatch === payload.forceExitBatch
  ) {
    return current
  }
  return { ...payload, requestId: createRequestId() }
}

export function rabbitEventPath() {
  return '/api/rabbits/events'
}

export interface PendingBatchActionRequest {
  batchId: number
  action: string
  payload: Record<string, unknown>
  requestId: string
}

function normalizeBatchActionValue(key: string, value: unknown): unknown {
  if (Array.isArray(value)) {
    const normalized = value.map((item) => normalizeBatchActionValue('', item))
    if (key.endsWith('Ids') && normalized.every((item) => typeof item === 'number')) {
      return [...new Set(normalized as number[])].sort((left, right) => left - right)
    }
    return normalized
  }
  if (value && typeof value === 'object') {
    return normalizeBatchActionPayload(value as Record<string, unknown>)
  }
  return value
}

export function normalizeBatchActionPayload(payload: Record<string, unknown>) {
  return Object.keys(payload).sort().reduce<Record<string, unknown>>((normalized, key) => {
    const value = payload[key]
    if (value !== undefined) normalized[key] = normalizeBatchActionValue(key, value)
    return normalized
  }, {})
}

export function getOrCreateBatchActionRequest(
  current: PendingBatchActionRequest | null,
  draft: Omit<PendingBatchActionRequest, 'requestId'>,
  createRequestId: () => string,
): PendingBatchActionRequest {
  const payload = normalizeBatchActionPayload(draft.payload)
  if (
    current
    && current.batchId === draft.batchId
    && current.action === draft.action
    && JSON.stringify(current.payload) === JSON.stringify(payload)
  ) {
    return current
  }
  return { ...draft, payload, requestId: createRequestId() }
}

export function batchActionPath(batchId: number, action: string) {
  return `/api/batches/${batchId}/${action}`
}
