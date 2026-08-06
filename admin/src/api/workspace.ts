import {
  merchantDeleteJson,
  merchantGetJson,
  merchantPostJson,
  merchantPutJson,
} from '@/lib/request'
import type {
  BatchRabbit,
  Cage,
  DashboardSummary,
  HouseMember,
  HousePermission,
  MerchantMember,
  MerchantMembership,
  MerchantRole,
  MerchantSession,
  MembershipStatus,
  OutboundSelectedItem,
  OutboundSubmitResult,
  OutboundTask,
  ProductionBatch,
  Rabbit,
  RabbitHouse,
} from '@/types/api'

export function requestId() {
  return crypto.randomUUID()
}

export function loginMerchant(data: { userName: string; password: string }) {
  return merchantPostJson<MerchantSession>('/api/auth/login', data)
}

export function listMerchantMemberships() {
  return merchantGetJson<MerchantMembership[]>('/api/merchant-memberships')
}

export function listWorkspaceHouses() {
  return merchantGetJson<RabbitHouse[]>('/api/houses')
}

export function getHousePermission(houseId: number) {
  return merchantGetJson<HousePermission>('/api/houses/permission', { houseId })
}

export function createWorkspaceHouse(data: {
  merchantId: number
  name: string
  layoutRows: number
  layoutCols: number
  layoutLayers: number
  remark?: string
}) {
  return merchantPostJson<RabbitHouse>('/api/houses', { ...data, requestId: requestId() })
}

export function updateWorkspaceHouse(
  houseId: number,
  data: { name: string; remark?: string },
) {
  return merchantPutJson<RabbitHouse>(`/api/houses/${houseId}`, data, { houseId })
}

export function deleteWorkspaceHouse(houseId: number) {
  return merchantDeleteJson<void>(`/api/houses/${houseId}`, { houseId })
}

export function getDashboard(houseId?: number | null, year = new Date().getFullYear()) {
  return merchantGetJson<DashboardSummary>('/api/reports/dashboard', {
    params: { houseId: houseId ?? undefined, year },
  })
}

export function listCages(houseId: number) {
  return merchantGetJson<Cage[]>('/api/cages', { houseId })
}

export function createCage(
  houseId: number,
  data: {
    cageNumber: string
    rowCode?: string
    layerIndex?: number
    positionIndex?: number
    remark?: string
    isEnabled: boolean
  },
) {
  return merchantPostJson<Cage>('/api/cages', data, { houseId })
}

export function updateCage(
  houseId: number,
  cageId: number,
  data: {
    cageNumber: string
    rowCode?: string
    layerIndex?: number
    positionIndex?: number
    remark?: string
    isEnabled: boolean
  },
) {
  return merchantPutJson<Cage>(`/api/cages/${cageId}`, data, { houseId })
}

export function deleteCage(houseId: number, cageId: number) {
  return merchantDeleteJson<void>(`/api/cages/${cageId}`, { houseId })
}

export function listRabbits(houseId: number) {
  return merchantGetJson<Rabbit[]>('/api/rabbits', { houseId })
}

export interface RabbitWriteInput {
  cageId: number
  motherId?: number
  type?: string
  gender?: string
  breed?: string
  arrivalMethod?: string
  arrivalDate?: string
  weight?: number
}

export function createRabbit(houseId: number, data: RabbitWriteInput) {
  return merchantPostJson<Rabbit>(
    '/api/rabbits',
    { ...data, requestId: requestId() },
    { houseId },
  )
}

export function updateRabbit(houseId: number, rabbitId: number, data: RabbitWriteInput) {
  const { type: _type, gender: _gender, ...update } = data
  return merchantPutJson<Rabbit>(
    `/api/rabbits/${rabbitId}`,
    { ...update, requestId: requestId() },
    { houseId },
  )
}

export function listBatches(houseId: number) {
  return merchantGetJson<ProductionBatch[]>('/api/batches', { houseId })
}

export function createBatch(
  houseId: number,
  data: { batchCode: string; femaleRabbitIds: number[]; remark?: string },
) {
  return merchantPostJson<ProductionBatch>(
    '/api/batches',
    { ...data, requestId: requestId() },
    { houseId },
  )
}

export function listBatchRabbits(houseId: number, batchId: number) {
  return merchantGetJson<BatchRabbit[]>(`/api/batches/${batchId}/batch-rabbits`, {
    houseId,
  })
}

export type BatchAction =
  | 'aphrodisiac/start'
  | 'aphrodisiac/finish'
  | 'mating'
  | 'pregnancy-check'
  | 'prepartum/finish'
  | 'parturition'
  | 'weaning'
  | 'sale'
  | 'complete'

export function submitBatchAction(
  houseId: number,
  batchId: number,
  action: BatchAction,
  data: Record<string, unknown>,
) {
  return merchantPostJson<void>(
    `/api/batches/${batchId}/${action}`,
    { ...data, requestId: requestId() },
    { houseId },
  )
}

export function createOutboundTask(houseId: number) {
  return merchantPostJson<OutboundTask>(
    '/api/outbound/tasks',
    { entryType: 'HOUSE', resumeExisting: false },
    { houseId },
  )
}

export function precheckOutboundTask(houseId: number, taskId: string) {
  return merchantPostJson<OutboundTask>(
    `/api/outbound/tasks/${taskId}/precheck`,
    {},
    { houseId },
  )
}

export function saveOutboundTask(
  houseId: number,
  taskId: string,
  data: {
    revision: number
    status: 'SELECTING' | 'WAITING_CONFIRMATION'
    items: OutboundSelectedItem[]
    saleTime: number
    totalWeight: number
    unitPrice?: number
    customer?: string
    remark?: string
  },
) {
  return merchantPutJson<OutboundTask>(`/api/outbound/tasks/${taskId}`, data, { houseId })
}

export function submitOutboundTask(
  houseId: number,
  taskId: string,
  data: {
    rabbitIds: number[]
    stateVersions: Record<string, number>
    earlySaleReasons: Record<string, string>
    saleTime: number
    totalWeight: number
    unitPrice?: number
    customer?: string
    remark?: string
    requestId: string
  },
) {
  return merchantPostJson<OutboundSubmitResult>(
    `/api/outbound/tasks/${taskId}/submit`,
    data,
    { houseId },
  )
}

export function cancelOutboundTask(houseId: number, taskId: string) {
  return merchantPostJson<void>(`/api/outbound/tasks/${taskId}/cancel`, {}, { houseId })
}

export function listMerchantMembers(merchantId: number) {
  return merchantGetJson<MerchantMember[]>(`/api/merchant-memberships/${merchantId}/members`)
}

export function addMerchantMember(
  merchantId: number,
  data: { userName: string; role: Exclude<MerchantRole, 'OWNER'> },
) {
  return merchantPostJson<void>(`/api/merchant-memberships/${merchantId}/members`, data)
}

export function updateMerchantMember(
  merchantId: number,
  userId: number,
  data: { role?: MerchantRole; status?: MembershipStatus },
) {
  return merchantPutJson<void>(
    `/api/merchant-memberships/${merchantId}/members/${userId}`,
    data,
  )
}

export function removeMerchantMember(merchantId: number, userId: number) {
  return merchantDeleteJson<void>(
    `/api/merchant-memberships/${merchantId}/members/${userId}`,
  )
}

export function listHouseMembers(houseId: number) {
  return merchantGetJson<HouseMember[]>('/api/house-members', { houseId })
}

export function addHouseMember(
  houseId: number,
  data: { userName: string; role: Exclude<HouseMember['role'], 'OWNER' | 'MERCHANT_OWNER'> },
) {
  return merchantPostJson<void>(
    '/api/house-members',
    { ...data, requestId: requestId() },
    { houseId },
  )
}

export function updateHouseMember(
  houseId: number,
  userId: number,
  role: Exclude<HouseMember['role'], 'MERCHANT_OWNER'>,
) {
  return merchantPutJson<void>(
    `/api/house-members/${userId}`,
    { role, requestId: requestId() },
    { houseId },
  )
}

export function removeHouseMember(houseId: number, userId: number) {
  return merchantDeleteJson<void>(`/api/house-members/${userId}`, {
    houseId,
    params: { requestId: requestId() },
  })
}
