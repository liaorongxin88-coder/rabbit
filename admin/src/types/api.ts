export type MerchantStatus = 'ENABLED' | 'DISABLED'
export type MerchantRole = 'OWNER' | 'ADMIN' | 'MEMBER'
export type MembershipStatus = 'ENABLED' | 'DISABLED'

export type AdminRole = 'SUPER_ADMIN' | 'ADMIN'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface AdminSession {
  token: string
  adminId: number
  userName: string
  role: AdminRole
  permissions?: string[]
}

export interface MerchantSession {
  token: string
  userId: number
  userName: string
  phoneBound?: boolean
  maskedPhone?: string | null
  permissions?: string[]
}

export interface MerchantMembership {
  merchantId: number
  merchantName: string
  merchantStatus: MerchantStatus
  role: MerchantRole
  membershipStatus: MembershipStatus
  permissions?: string[]
}

export interface MerchantMember {
  userId: number
  userName: string
  phoneMasked?: string | null
  role: MerchantRole
  status: MembershipStatus
  joinTime?: string | null
}

export type HouseRole = 'OWNER' | 'MANAGER' | 'STAFF' | 'VIEWER' | 'MERCHANT_OWNER'

export interface HousePermission {
  perms: 'view' | 'edit' | 'control'
  role: HouseRole
  isAdmin: boolean
  permissions?: string[]
}

export interface HouseMember {
  userId: number
  userName: string
  role: HouseRole
  perms: 'view' | 'edit' | 'control'
  isAdmin: boolean
  joinTime?: string | null
}

export interface AdminAccount {
  id: number
  userName: string
  role: AdminRole
  enabled: boolean
  lastLoginTime?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface MerchantAccount {
  userId: number
  merchantId: number
  merchantName: string
  userName: string
  openid?: string | null
  role?: MerchantRole | null
  membershipStatus?: MembershipStatus | null
  createTime?: string | null
  updateTime?: string | null
}

export interface Merchant {
  id: number
  ownerUserId?: number | null
  name: string
  contactName?: string | null
  contactPhone?: string | null
  status: MerchantStatus
  remark?: string | null
  createBy?: string | null
  createTime?: string | null
  updateBy?: string | null
  updateTime?: string | null
}

export interface MerchantAccountSummary {
  userId: number
  userName: string
  openid?: string | null
  phoneMasked?: string | null
  role: MerchantRole
  membershipStatus: MembershipStatus
  createTime?: string | null
}

export interface MerchantHousePolicy {
  merchantId: number
  houseCreationEnabled: boolean
  houseMemberManagementEnabled: boolean
  maxHouseCount: number
  maxMembersPerHouse: number
  updateTime?: string | null
}

export interface RabbitHouse {
  id: number
  merchantId?: number | null
  name: string
  layoutRows: number
  layoutCols: number
  layoutLayers: number
  remark?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface Cage {
  id: number
  houseId: number
  cageNumber: string
  rowCode?: string | null
  layerIndex?: number | null
  positionIndex?: number | null
  status?: string | null
  rabbitCount: number
  isFed: boolean
  isEnabled: boolean
  remark?: string | null
}

export interface Rabbit {
  id: number
  houseId: number
  cageId: number
  motherId?: number | null
  type: string
  gender: string
  breed?: string | null
  arrivalMethod?: string | null
  arrivalDate?: string | null
  weight?: number | null
  stateVersion?: number | null
  isActive: boolean
  isQuarantined: boolean
  quarantineReason?: string | null
  departureDate?: string | null
  departureReason?: string | null
  createTime?: string | null
}

export interface DashboardSummary {
  selectedHouseId?: number | null
  houseCount: number
  year: number
  totalRabbits: number
  seedRabbits: number
  maleRabbits: number
  femaleRabbits: number
  bredRabbits: number
  readyForBreeding: number
  litters: number
  nursingKits: number
  commodityRabbits: number
  replacementRabbits: number
  liveRate: number
  monthlyBirths: number[]
  monthlyWeaned: number[]
}

export interface ProductionBatch {
  id: number
  houseId: number
  batchCode: string
  status: string
  startDate?: string | null
  endDate?: string | null
  remark?: string | null
  createTime?: string | null
}

export interface BatchRabbit {
  id: number
  batchId: number
  rabbitId: number
  maleRabbitId?: number | null
  batchRole?: string | null
  currentStatus?: string | null
  nextEventDate?: string | null
  nextEventType?: string | null
  isActive: boolean
  rabbitType?: string | null
  rabbitGender?: string | null
  cageId?: number | null
}

export type OutboundEligibility = 'NORMAL' | 'EARLY_SALE' | 'NEEDS_ACTION' | 'BLOCKED'

export interface OutboundSummary {
  normal: number
  earlySale: number
  needsAction: number
  blocked: number
}

export interface OutboundRabbit {
  rabbitId: number
  cageId?: number | null
  cageNumber: string
  rowCode: string
  layerIndex?: number | null
  positionIndex?: number | null
  rabbitType: string
  gender: string
  weight?: number | null
  stage: string
  batchId?: number | null
  stateVersion: number
  eligibility: OutboundEligibility
  reasonCode: string
  message: string
  recommendedAction: string
  defaultSelected: boolean
}

export interface OutboundSelectedItem {
  rabbitId: number
  stateVersion: number
  selectionType: 'NORMAL' | 'EARLY_SALE'
  earlySaleReason?: string | null
}

export interface OutboundTask {
  taskId: string
  houseId: number
  entryType: 'RABBIT' | 'CAGE' | 'ROW' | 'HOUSE'
  status: 'SELECTING' | 'WAITING_CONFIRMATION' | 'COMPLETED' | 'CANCELLED'
  revision: number
  saleTime?: string | null
  totalWeight?: number | null
  unitPrice?: number | null
  customer?: string | null
  remark?: string | null
  saleOrderId?: number | null
  resumed: boolean
  summary: OutboundSummary
  rabbits: OutboundRabbit[]
  selectedItems: OutboundSelectedItem[]
}

export interface OutboundConflict {
  rabbitId: number
  errorCode: string
  currentState: string
  message: string
  recommendedAction: string
}

export interface OutboundSubmitResult {
  status: 'COMPLETED' | 'CONFLICT' | 'FAILED' | 'PROCESSING'
  requestId: string
  taskId: string
  saleOrderId?: number | null
  saleOrderNumber?: string | null
  saleTime?: string | null
  rabbitCount: number
  cageCount: number
  rowCount: number
  totalWeight?: number | null
  totalAmount?: number | null
  errorCode?: string | null
  message?: string | null
  conflicts: OutboundConflict[]
}

export interface AuditLog {
  id: number
  traceId?: string | null
  userId?: number | null
  houseId?: number | null
  method?: string | null
  path?: string | null
  status?: number | null
  apiCode?: number | null
  apiMessage?: string | null
  costMs?: number | null
  createTime?: string | null
}

export interface MerchantOverview {
  houseCount: number
  userCount: number
  cageCount: number
  rabbitCount: number
  houses: RabbitHouse[]
  recentAuditLogs: AuditLog[]
}
