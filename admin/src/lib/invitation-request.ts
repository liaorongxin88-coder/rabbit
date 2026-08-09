import type { HouseInvitationRequest } from '@/types/api'

export function getOrCreateInvitationRequest(
  current: HouseInvitationRequest | null,
  payload: Omit<HouseInvitationRequest, 'requestId'>,
  createRequestId: () => string,
): HouseInvitationRequest {
  if (current?.phone === payload.phone && current.role === payload.role) {
    return current
  }

  return { ...payload, requestId: createRequestId() }
}
