import type { AdminSession, WorkspaceSession } from '@/types/api'

const STORAGE_KEY = 'rabbit_admin_session_v1'
const WORKSPACE_STORAGE_KEY = 'rabbit_workspace_session_v2'
const WORKSPACE_SELECTION_KEY = 'rabbit_workspace_selection_v2'
const LEGACY_WORKSPACE_STORAGE_KEY = 'rabbit_merchant_session_v1'
const LEGACY_WORKSPACE_SELECTION_KEY = 'rabbit_merchant_selection_v1'
const ADMIN_SESSION_CHANGE_EVENT = 'rabbit-admin-session-change'
const WORKSPACE_SESSION_CHANGE_EVENT = 'rabbit-workspace-session-change'

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

export function getWorkspaceSession(): WorkspaceSession | null {
  window.localStorage.removeItem(LEGACY_WORKSPACE_STORAGE_KEY)
  window.localStorage.removeItem(LEGACY_WORKSPACE_SELECTION_KEY)
  const raw = window.localStorage.getItem(WORKSPACE_STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as WorkspaceSession
  } catch {
    window.localStorage.removeItem(WORKSPACE_STORAGE_KEY)
    return null
  }
}

export function setWorkspaceSession(session: WorkspaceSession) {
  window.localStorage.setItem(WORKSPACE_STORAGE_KEY, JSON.stringify(session))
  emitSessionChange(WORKSPACE_SESSION_CHANGE_EVENT)
}

export function clearWorkspaceSession() {
  const hadSession = window.localStorage.getItem(WORKSPACE_STORAGE_KEY) !== null
  window.localStorage.removeItem(WORKSPACE_STORAGE_KEY)
  window.localStorage.removeItem(WORKSPACE_SELECTION_KEY)
  window.localStorage.removeItem(LEGACY_WORKSPACE_STORAGE_KEY)
  window.localStorage.removeItem(LEGACY_WORKSPACE_SELECTION_KEY)
  if (hadSession) {
    emitSessionChange(WORKSPACE_SESSION_CHANGE_EVENT)
  }
}

export function getWorkspaceToken() {
  return getWorkspaceSession()?.token ?? ''
}

export function subscribeWorkspaceSession(listener: () => void) {
  return subscribeSessionChange(
    WORKSPACE_STORAGE_KEY,
    WORKSPACE_SESSION_CHANGE_EVENT,
    listener,
  )
}

export interface WorkspaceSelection {
  userId: number
  houseId: number | null
}

export function getWorkspaceSelection(userId: number): WorkspaceSelection | null {
  const raw = window.localStorage.getItem(WORKSPACE_SELECTION_KEY)
  if (!raw) {
    return null
  }
  try {
    const selection = JSON.parse(raw) as WorkspaceSelection
    return selection.userId === userId ? selection : null
  } catch {
    window.localStorage.removeItem(WORKSPACE_SELECTION_KEY)
    return null
  }
}

export function setWorkspaceSelection(selection: WorkspaceSelection) {
  window.localStorage.setItem(WORKSPACE_SELECTION_KEY, JSON.stringify(selection))
}
