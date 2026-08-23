import type { RabbitDepartureRequest } from '@/types/api'

export type BatchStatusInput = string | null | undefined

export const BATCH_MOTHER_PAGE_SIZE = 50

const batchStatusAliases: Record<string, string> = {
  ACTIVE: '进行中',
  COMPLETED: '已完成',
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

type BatchActionValue =
  | string
  | number
  | boolean
  | null
  | BatchActionValue[]
  | { [key: string]: BatchActionValue }

type BatchActionPayload = Record<string, BatchActionValue>

export interface PendingBatchActionRequest {
  batchId: number
  action: string
  payload: BatchActionPayload
  requestId: string
}

function normalizeBatchActionValue(
  key: string,
  value: unknown,
): BatchActionValue | undefined {
  if (value === null || ['string', 'number', 'boolean'].includes(typeof value)) {
    return value as string | number | boolean | null
  }
  if (Array.isArray(value)) {
    const normalized = value
      .map((item) => normalizeBatchActionValue('', item))
      .filter((item): item is BatchActionValue => item !== undefined)
    if (key.endsWith('Ids') && normalized.every((item) => typeof item === 'number')) {
      return [...new Set(normalized)].sort((left, right) => left - right)
    }
    return normalized
  }
  if (value && typeof value === 'object') {
    return normalizeBatchActionPayload(value as Record<string, unknown>)
  }
  return undefined
}

export function normalizeBatchActionPayload(
  payload: Record<string, unknown>,
): BatchActionPayload {
  return Object.keys(payload).sort().reduce<BatchActionPayload>((normalized, key) => {
    const value = normalizeBatchActionValue(key, payload[key])
    if (value !== undefined) normalized[key] = value
    return normalized
  }, {})
}

export function getOrCreateBatchActionRequest(
  current: PendingBatchActionRequest | null,
  draft: { batchId: number; action: string; payload: Record<string, unknown> },
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

export function pendingWeaningRecordsPath(batchId: number) {
  return `/api/batches/${batchId}/weaning-records`
}

export function weaningSeparationPath(batchId: number, weaningRecordId: number) {
  return `/api/batches/${batchId}/weaning-records/${weaningRecordId}/separation`
}
