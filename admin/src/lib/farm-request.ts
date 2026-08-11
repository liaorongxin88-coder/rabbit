import type { CreateAdminFarmRequest } from '@/types/api'

type FarmCreatePayload = Omit<CreateAdminFarmRequest, 'requestId'>

export function getOrCreateFarmRequest(
  current: CreateAdminFarmRequest | null,
  payload: FarmCreatePayload,
  createRequestId: () => string,
): CreateAdminFarmRequest {
  if (
    current?.name === payload.name
    && current.layoutRows === payload.layoutRows
    && current.layoutCols === payload.layoutCols
    && current.layoutLayers === payload.layoutLayers
    && current.remark === payload.remark
    && current.ownerUserId === payload.ownerUserId
    && current.ownerPhone === payload.ownerPhone
  ) {
    return current
  }

  return { ...payload, requestId: createRequestId() }
}
