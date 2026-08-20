import { lazy, Suspense, useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import {
  clearSession,
  clearWorkspaceSession,
  getSession,
  getWorkspaceSession,
  subscribeAdminSession,
  subscribeWorkspaceSession,
} from '@/lib/auth'
import type { AdminSession, WorkspaceSession } from '@/types/api'
import { hasPermission } from '@/lib/permissions'
import { AppShell } from '@/components/app-shell'
import { WorkspaceShell } from '@/components/workspace-shell'
import { WorkspaceProvider } from '@/components/workspace-context'
import { Spinner } from '@/components/ui/spinner'
import { AccountsPage } from '@/pages/accounts-page'
import { DashboardPage } from '@/pages/dashboard-page'
import { FarmDetailPage } from '@/pages/farm-detail-page'
import { FarmsPage } from '@/pages/farms-page'
import { LoginPage } from '@/pages/login-page'
import { UsersPage } from '@/pages/users-page'

const WorkspaceDashboardPage = lazy(() =>
  import('@/pages/workspace-dashboard-page').then((module) => ({
    default: module.WorkspaceDashboardPage,
  })),
)
const WorkspaceAccountPage = lazy(() =>
  import('@/pages/workspace-account-page').then((module) => ({
    default: module.WorkspaceAccountPage,
  })),
)
const WorkspaceFarmsPage = lazy(() =>
  import('@/pages/workspace-farms-page').then((module) => ({
    default: module.WorkspaceFarmsPage,
  })),
)
const WorkspaceLivestockPage = lazy(() =>
  import('@/pages/workspace-livestock-page').then((module) => ({
    default: module.WorkspaceLivestockPage,
  })),
)
const WorkspaceRabbitDetailPage = lazy(() =>
  import('@/pages/workspace-rabbit-detail-page').then((module) => ({
    default: module.WorkspaceRabbitDetailPage,
  })),
)
const WorkspaceLoginPage = lazy(() =>
  import('@/pages/workspace-login-page').then((module) => ({
    default: module.WorkspaceLoginPage,
  })),
)
const WorkspaceMembersPage = lazy(() =>
  import('@/pages/workspace-members-page').then((module) => ({
    default: module.WorkspaceMembersPage,
  })),
)
const WorkspaceProductionPage = lazy(() =>
  import('@/pages/workspace-production-page').then((module) => ({
    default: module.WorkspaceProductionPage,
  })),
)

function RequireAdminAuth({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  const session = useAdminSession()

  if (!session) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return children
}

function RequireWorkspaceAuth({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  const session = useWorkspaceSession()

  if (!session) {
    return <Navigate to="/workspace/login" state={{ from: location }} replace />
  }

  return children
}

function ShellRoutes() {
  const navigate = useNavigate()
  const session = useAdminSession()

  if (!session) {
    return <Navigate to="/login" replace />
  }

  function handleLogout() {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <AppShell session={session} onLogout={handleLogout}>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage session={session} />} />
        <Route path="/farms" element={<FarmsPage />} />
        <Route path="/farms/:farmId" element={<FarmDetailPage />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="/merchants" element={<Navigate to="/farms" replace />} />
        <Route path="/merchants/:legacyId" element={<Navigate to="/farms" replace />} />
        <Route
          path="/accounts"
          element={
            hasPermission(session, 'platform:accounts:list') ? (
              <AccountsPage currentAdminId={session.adminId} />
            ) : (
              <Navigate to="/dashboard" replace />
            )
          }
        />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AppShell>
  )
}

function WorkspaceShellRoutes() {
  const navigate = useNavigate()
  const session = useWorkspaceSession()

  if (!session) {
    return <Navigate to="/workspace/login" replace />
  }

  function handleLogout() {
    clearWorkspaceSession()
    navigate('/workspace/login', { replace: true })
  }

  return (
    <WorkspaceProvider session={session}>
      <WorkspaceShell onLogout={handleLogout}>
        <Routes>
          <Route index element={<Navigate to="/workspace/dashboard" replace />} />
          <Route path="dashboard" element={<WorkspaceDashboardPage />} />
          <Route path="farms" element={<WorkspaceFarmsPage />} />
          <Route path="livestock" element={<WorkspaceLivestockPage />} />
          <Route path="livestock/rabbits/:rabbitId" element={<WorkspaceRabbitDetailPage />} />
          <Route path="production" element={<WorkspaceProductionPage />} />
          <Route path="members" element={<WorkspaceMembersPage />} />
          <Route path="account" element={<WorkspaceAccountPage />} />
          <Route path="*" element={<Navigate to="/workspace/dashboard" replace />} />
        </Routes>
      </WorkspaceShell>
    </WorkspaceProvider>
  )
}

function LegacyWorkspaceRedirect() {
  const location = useLocation()
  const destination = location.pathname.replace(/^\/merchant/, '/workspace')
  return <Navigate to={`${destination}${location.search}${location.hash}`} replace />
}

function useAdminSession(): AdminSession | null {
  const [session, setSessionState] = useState<AdminSession | null>(() => getSession())

  useEffect(
    () => subscribeAdminSession(() => setSessionState(getSession())),
    [],
  )

  return session
}

function useWorkspaceSession(): WorkspaceSession | null {
  const [session, setSessionState] = useState<WorkspaceSession | null>(() =>
    getWorkspaceSession(),
  )

  useEffect(
    () => subscribeWorkspaceSession(() => setSessionState(getWorkspaceSession())),
    [],
  )

  return session
}

function App() {
  return (
    <>
      <Suspense
        fallback={
          <div className="flex min-h-screen items-center justify-center bg-background">
            <Spinner aria-label="页面加载中" />
          </div>
        }
      >
        <Routes>
          <Route path="/workspace/login" element={<WorkspaceLoginPage />} />
          <Route
            path="/workspace/*"
            element={
              <RequireWorkspaceAuth>
                <WorkspaceShellRoutes />
              </RequireWorkspaceAuth>
            }
          />
          <Route path="/merchant/login" element={<Navigate to="/workspace/login" replace />} />
          <Route path="/merchant/*" element={<LegacyWorkspaceRedirect />} />
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/*"
            element={
              <RequireAdminAuth>
                <ShellRoutes />
              </RequireAdminAuth>
            }
          />
        </Routes>
      </Suspense>
      <Toaster richColors position="top-right" />
    </>
  )
}

export default App
