import type { AdminSession } from '@/types/api'

const STORAGE_KEY = 'rabbit_admin_session_v1'

export function getSession(): AdminSession | null {
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as AdminSession
  } catch {
    window.localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

export function setSession(session: AdminSession) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function clearSession() {
  window.localStorage.removeItem(STORAGE_KEY)
}

export function getToken() {
  return getSession()?.token ?? ''
}
