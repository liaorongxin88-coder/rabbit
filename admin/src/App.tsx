import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import { clearSession, getSession } from '@/lib/auth'
import type { AdminSession } from '@/types/api'
import { AppShell } from '@/components/app-shell'
import { DashboardPage } from '@/pages/dashboard-page'
import { LoginPage } from '@/pages/login-page'
import { MerchantDetailPage } from '@/pages/merchant-detail-page'
import { MerchantsPage } from '@/pages/merchants-page'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  const session = getSession()

  if (!session) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return children
}

function ShellRoutes() {
  const navigate = useNavigate()
  const session = getSession() as AdminSession

  function handleLogout() {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <AppShell session={session} onLogout={handleLogout}>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/merchants" element={<MerchantsPage />} />
        <Route path="/merchants/:merchantId" element={<MerchantDetailPage />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AppShell>
  )
}

function App() {
  return (
    <>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/*"
          element={
            <RequireAuth>
              <ShellRoutes />
            </RequireAuth>
          }
        />
      </Routes>
      <Toaster richColors position="top-right" />
    </>
  )
}

export default App
