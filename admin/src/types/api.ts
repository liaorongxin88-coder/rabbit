export type MerchantStatus = 'ENABLED' | 'DISABLED'

export type AdminRole = 'SUPER_ADMIN' | 'OPERATOR'

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
}

export interface Merchant {
  id: number
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

export interface MerchantUser {
  userId: number
  userName: string
  openid?: string | null
  createTime?: string | null
  bindTime?: string | null
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
