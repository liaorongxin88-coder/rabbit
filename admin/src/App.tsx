import { lazy, Suspense, useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import {
  clearMerchantSession,
  clearSession,
  getMerchantSession,
  getSession,
  subscribeAdminSession,
  subscribeMerchantSession,
} from '@/lib/auth'
import type { AdminSession, MerchantSession } from '@/types/api'
import { hasPermission } from '@/lib/permissions'
import { AppShell } from '@/components/app-shell'
import { MerchantShell } from '@/components/merchant-shell'
import { MerchantWorkspaceProvider } from '@/components/merchant-workspace-context'
import { Spinner } from '@/components/ui/spinner'
import { AccountsPage } from '@/pages/accounts-page'
import { DashboardPage } from '@/pages/dashboard-page'
import { LoginPage } from '@/pages/login-page'
import { MerchantDetailPage } from '@/pages/merchant-detail-page'
import { MerchantsPage } from '@/pages/merchants-page'

const MerchantDashboardPage = lazy(() =>
  import('@/pages/merchant-dashboard-page').then((module) => ({
    default: module.MerchantDashboardPage,
  })),
)
const MerchantHousesPage = lazy(() =>
  import('@/pages/merchant-houses-page').then((module) => ({
    default: module.MerchantHousesPage,
  })),
)
const MerchantLivestockPage = lazy(() =>
  import('@/pages/merchant-livestock-page').then((module) => ({
    default: module.MerchantLivestockPage,
  })),
)
const MerchantLoginPage = lazy(() =>
  import('@/pages/merchant-login-page').then((module) => ({
    default: module.MerchantLoginPage,
  })),
)
const MerchantMembersPage = lazy(() =>
  import('@/pages/merchant-members-page').then((module) => ({
    default: module.MerchantMembersPage,
  })),
)
const MerchantProductionPage = lazy(() =>
  import('@/pages/merchant-production-page').then((module) => ({
    default: module.MerchantProductionPage,
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

function RequireMerchantAuth({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  const session = useMerchantSession()

  if (!session) {
    return <Navigate to="/merchant/login" state={{ from: location }} replace />
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
        <Route path="/merchants" element={<MerchantsPage />} />
        <Route path="/merchants/:merchantId" element={<MerchantDetailPage />} />
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

function MerchantShellRoutes() {
  const navigate = useNavigate()
  const session = useMerchantSession()

  if (!session) {
    return <Navigate to="/merchant/login" replace />
  }

  function handleLogout() {
    clearMerchantSession()
    navigate('/merchant/login', { replace: true })
  }

  return (
    <MerchantWorkspaceProvider session={session}>
      <MerchantShell onLogout={handleLogout}>
        <Routes>
          <Route index element={<Navigate to="/merchant/dashboard" replace />} />
          <Route path="dashboard" element={<MerchantDashboardPage />} />
          <Route path="houses" element={<MerchantHousesPage />} />
          <Route path="livestock" element={<MerchantLivestockPage />} />
          <Route path="production" element={<MerchantProductionPage />} />
          <Route path="members" element={<MerchantMembersPage />} />
          <Route path="*" element={<Navigate to="/merchant/dashboard" replace />} />
        </Routes>
      </MerchantShell>
    </MerchantWorkspaceProvider>
  )
}

function useAdminSession(): AdminSession | null {
  const [session, setSessionState] = useState<AdminSession | null>(() => getSession())

  useEffect(
    () => subscribeAdminSession(() => setSessionState(getSession())),
    [],
  )

  return session
}

function useMerchantSession(): MerchantSession | null {
  const [session, setSessionState] = useState<MerchantSession | null>(() =>
    getMerchantSession(),
  )

  useEffect(
    () => subscribeMerchantSession(() => setSessionState(getMerchantSession())),
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
          <Route path="/merchant/login" element={<MerchantLoginPage />} />
          <Route
            path="/merchant/*"
            element={
              <RequireMerchantAuth>
                <MerchantShellRoutes />
              </RequireMerchantAuth>
            }
          />
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
