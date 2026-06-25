import { postJson } from '@/lib/request'
import type { AdminSession } from '@/types/api'

export interface LoginPayload {
  userName: string
  password: string
}

export function loginAdmin(payload: LoginPayload) {
  return postJson<AdminSession>('/api/admin/auth/login', payload).send()
}
