import type { AdminSession, MerchantSession } from '@/types/api'

const STORAGE_KEY = 'rabbit_admin_session_v1'
const MERCHANT_STORAGE_KEY = 'rabbit_merchant_session_v1'
const MERCHANT_SELECTION_KEY = 'rabbit_merchant_selection_v1'
const ADMIN_SESSION_CHANGE_EVENT = 'rabbit-admin-session-change'
const MERCHANT_SESSION_CHANGE_EVENT = 'rabbit-merchant-session-change'

function emitSessionChange(eventName: string) {
  window.dispatchEvent(new Event(eventName))
}

function subscribeSessionChange(
  storageKey: string,
  eventName: string,
  listener: () => void,
) {
  const handleStorage = (event: StorageEvent) => {
    if (event.key === storageKey) {
      listener()
    }
  }

  window.addEventListener(eventName, listener)
  window.addEventListener('storage', handleStorage)

  return () => {
    window.removeEventListener(eventName, listener)
    window.removeEventListener('storage', handleStorage)
  }
}

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
  emitSessionChange(ADMIN_SESSION_CHANGE_EVENT)
}

export function clearSession() {
  const hadSession = window.localStorage.getItem(STORAGE_KEY) !== null
  window.localStorage.removeItem(STORAGE_KEY)
  if (hadSession) {
    emitSessionChange(ADMIN_SESSION_CHANGE_EVENT)
  }
}

export function getToken() {
  return getSession()?.token ?? ''
}

export function subscribeAdminSession(listener: () => void) {
  return subscribeSessionChange(STORAGE_KEY, ADMIN_SESSION_CHANGE_EVENT, listener)
}

export function getMerchantSession(): MerchantSession | null {
  const raw = window.localStorage.getItem(MERCHANT_STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as MerchantSession
  } catch {
    window.localStorage.removeItem(MERCHANT_STORAGE_KEY)
    return null
  }
}

export function setMerchantSession(session: MerchantSession) {
  window.localStorage.setItem(MERCHANT_STORAGE_KEY, JSON.stringify(session))
  emitSessionChange(MERCHANT_SESSION_CHANGE_EVENT)
}

export function clearMerchantSession() {
  const hadSession = window.localStorage.getItem(MERCHANT_STORAGE_KEY) !== null
  window.localStorage.removeItem(MERCHANT_STORAGE_KEY)
  window.localStorage.removeItem(MERCHANT_SELECTION_KEY)
  if (hadSession) {
    emitSessionChange(MERCHANT_SESSION_CHANGE_EVENT)
  }
}

export function getMerchantToken() {
  return getMerchantSession()?.token ?? ''
}

export function subscribeMerchantSession(listener: () => void) {
  return subscribeSessionChange(
    MERCHANT_STORAGE_KEY,
    MERCHANT_SESSION_CHANGE_EVENT,
    listener,
  )
}

export interface MerchantSelection {
  userId: number
  merchantId: number | null
  houseId: number | null
}

export function getMerchantSelection(userId: number): MerchantSelection | null {
  const raw = window.localStorage.getItem(MERCHANT_SELECTION_KEY)
  if (!raw) {
    return null
  }
  try {
    const selection = JSON.parse(raw) as MerchantSelection
    return selection.userId === userId ? selection : null
  } catch {
    window.localStorage.removeItem(MERCHANT_SELECTION_KEY)
    return null
  }
}

export function setMerchantSelection(selection: MerchantSelection) {
  window.localStorage.setItem(MERCHANT_SELECTION_KEY, JSON.stringify(selection))
}
