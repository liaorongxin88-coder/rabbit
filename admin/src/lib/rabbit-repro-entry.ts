import type { RabbitReproEntryInput } from '@/api/workspace'
import { normalizeBatchStatus } from './batch-workflow.ts'
import { farmBusinessDateToIso } from './date.ts'
import type { ProductionBatch } from '@/types/api'

export function inProgressProductionBatches(batches: readonly ProductionBatch[]) {
  return batches.filter((batch) => normalizeBatchStatus(batch.status) === '进行中')
}

export function keepValidProductionBatchId(
  batchId: string,
  batches: readonly ProductionBatch[],
) {
  return batches.some((batch) => String(batch.id) === batchId) ? batchId : ''
}

export function buildRabbitReproEntryInput({
  reproStage,
  batchId,
  stageEnteredAt,
  matingDate,
  birthDate,
  liveKits,
}: {
  reproStage: string
  batchId: number
  stageEnteredAt: string
  matingDate: string
  birthDate: string
  liveKits?: number
}): RabbitReproEntryInput {
  return {
    reproStage,
    batchId,
    stageEnteredAt: farmBusinessDateToIso(stageEnteredAt),
    matingDate: farmBusinessDateToIso(matingDate),
    birthDate: farmBusinessDateToIso(birthDate),
    liveKits,
  }
}
