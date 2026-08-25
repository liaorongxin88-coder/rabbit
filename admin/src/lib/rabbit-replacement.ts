import type { RabbitReplacementRequest } from '@/types/api'

export type RabbitReplacementDraft = Omit<RabbitReplacementRequest, 'requestId'>

export function getOrCreateRabbitReplacementRequest(
  current: RabbitReplacementRequest | null,
  draft: RabbitReplacementDraft,
  createRequestId: () => string,
): RabbitReplacementRequest {
  if (
    current
    && current.forceExitBatch === draft.forceExitBatch
    && current.targetCageId === draft.targetCageId
    && current.rabbitIds.length === draft.rabbitIds.length
    && current.rabbitIds.every((rabbitId, index) => rabbitId === draft.rabbitIds[index])
  ) {
    return current
  }

  return {
    rabbitIds: [...draft.rabbitIds],
    forceExitBatch: draft.forceExitBatch,
    targetCageId: draft.targetCageId,
    requestId: createRequestId(),
  }
}

export function rabbitReplacementPath() {
  return '/api/rabbits/replacement'
}
