import type { HouseInvitationRequest } from '@/types/api'

export function getOrCreateInvitationRequest(
  current: HouseInvitationRequest | null,
  payload: Omit<HouseInvitationRequest, 'requestId'>,
  createRequestId: () => string,
): HouseInvitationRequest {
  // 指纹按 identifier 比：同一个人换个大小写写法重试，不该变成两条邀请。
  if (current?.identifier === payload.identifier && current.role === payload.role) {
    return current
  }

  return { ...payload, requestId: createRequestId() }
}
