import {
  workspaceDeleteJson,
  workspaceGetJson,
  workspacePostJson,
  workspacePutJson,
} from '@/lib/request'
import type {
  BatchRabbit,
  Cage,
  DashboardSummary,
  HouseInvitationRequest,
  HouseMember,
  HousePermission,
  OutboundSelectedItem,
  OutboundSubmitResult,
  OutboundTask,
  ProductionBatch,
  Rabbit,
  RabbitHouse,
  WorkspaceSession,
} from '@/types/api'

export function requestId() {
  return crypto.randomUUID()
}

export function loginWorkspace(data: { userName: string; password: string }) {
  return workspacePostJson<WorkspaceSession>('/api/auth/login', data)
}

export function listWorkspaceHouses() {
  return workspaceGetJson<RabbitHouse[]>('/api/houses')
}

export function getHousePermission(houseId: number) {
  return workspaceGetJson<HousePermission>('/api/houses/permission', { houseId })
}

export function createWorkspaceHouse(data: {
  name: string
  layoutRows: number
  layoutCols: number
  layoutLayers: number
  remark?: string
}) {
  return workspacePostJson<RabbitHouse>('/api/houses', { ...data, requestId: requestId() })
}

export function updateWorkspaceHouse(
  houseId: number,
  data: { name: string; remark?: string },
) {
  return workspacePutJson<RabbitHouse>(`/api/houses/${houseId}`, data, { houseId })
}

export function deleteWorkspaceHouse(houseId: number) {
  return workspaceDeleteJson<void>(`/api/houses/${houseId}`, { houseId })
}

export function getDashboard(houseId?: number | null, year = new Date().getFullYear()) {
  return workspaceGetJson<DashboardSummary>('/api/reports/dashboard', {
    params: { houseId: houseId ?? undefined, year },
  })
}

export function listCages(houseId: number) {
  return workspaceGetJson<Cage[]>('/api/cages', { houseId })
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
  return workspacePostJson<Cage>('/api/cages', data, { houseId })
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
  return workspacePutJson<Cage>(`/api/cages/${cageId}`, data, { houseId })
}

export function deleteCage(houseId: number, cageId: number) {
  return workspaceDeleteJson<void>(`/api/cages/${cageId}`, { houseId })
}

export function listRabbits(houseId: number) {
  return workspaceGetJson<Rabbit[]>('/api/rabbits', { houseId })
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
  return workspacePostJson<Rabbit>(
    '/api/rabbits',
    { ...data, requestId: requestId() },
    { houseId },
  )
}

export function updateRabbit(houseId: number, rabbitId: number, data: RabbitWriteInput) {
  const { type: _type, gender: _gender, ...update } = data
  return workspacePutJson<Rabbit>(
    `/api/rabbits/${rabbitId}`,
    { ...update, requestId: requestId() },
    { houseId },
  )
}

export function listBatches(houseId: number) {
  return workspaceGetJson<ProductionBatch[]>('/api/batches', { houseId })
}

export function createBatch(
  houseId: number,
  data: { batchCode: string; femaleRabbitIds: number[]; remark?: string },
) {
  return workspacePostJson<ProductionBatch>(
    '/api/batches',
    { ...data, requestId: requestId() },
    { houseId },
  )
}

export function listBatchRabbits(houseId: number, batchId: number) {
  return workspaceGetJson<BatchRabbit[]>(`/api/batches/${batchId}/batch-rabbits`, {
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
  return workspacePostJson<void>(
    `/api/batches/${batchId}/${action}`,
    { ...data, requestId: requestId() },
    { houseId },
  )
}

export function createOutboundTask(houseId: number) {
  return workspacePostJson<OutboundTask>(
    '/api/outbound/tasks',
    { entryType: 'HOUSE', resumeExisting: false },
    { houseId },
  )
}

export function precheckOutboundTask(houseId: number, taskId: string) {
  return workspacePostJson<OutboundTask>(
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
  return workspacePutJson<OutboundTask>(`/api/outbound/tasks/${taskId}`, data, { houseId })
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
  return workspacePostJson<OutboundSubmitResult>(
    `/api/outbound/tasks/${taskId}/submit`,
    data,
    { houseId },
  )
}

export function cancelOutboundTask(houseId: number, taskId: string) {
  return workspacePostJson<void>(`/api/outbound/tasks/${taskId}/cancel`, {}, { houseId })
}

export function listHouseMembers(houseId: number) {
  return workspaceGetJson<HouseMember[]>('/api/house-members', { houseId })
}

export function createHouseInvitation(
  houseId: number,
  data: HouseInvitationRequest,
) {
  return workspacePostJson<void>('/api/house-invitations', data, { houseId })
}

export function updateHouseMember(
  houseId: number,
  userId: number,
  role: HouseMember['role'],
) {
  return workspacePutJson<void>(
    `/api/house-members/${userId}`,
    { role, requestId: requestId() },
    { houseId },
  )
}

export function removeHouseMember(houseId: number, userId: number) {
  return workspaceDeleteJson<void>(`/api/house-members/${userId}`, {
    houseId,
    params: { requestId: requestId() },
  })
}
