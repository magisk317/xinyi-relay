import { useEffect } from 'react'
import { Spinner } from 'flowbite-react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import { AppLayout } from './layout'
import { AdvancedPage } from './pages/AdvancedPage'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { AppsPage } from './pages/AppsPage'
import { LoginPage } from './pages/LoginPage'
import { OverviewPage } from './pages/OverviewPage'
import { RecordsPage } from './pages/RecordsPage'
import { SendersPage } from './pages/SendersPage'
import { SettingsPage } from './pages/SettingsPage'
import { trackPageView } from './analytics'

function ProtectedLayout() {
  const { loading, authenticated } = useAuth()
  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[linear-gradient(180deg,#f8fbff_0%,#eef5ff_100%)]">
        <div className="flex items-center gap-3 rounded-2xl border border-slate-200/70 bg-white px-5 py-4 text-sm text-slate-600 shadow-sm">
          <Spinner color="info" />
          正在连接内嵌 WebUI...
        </div>
      </div>
    )
  }
  if (!authenticated) {
    return <Navigate to="/login" replace />
  }
  return <AppLayout />
}

function AppRoutes() {
  const location = useLocation()
  useEffect(() => {
    trackPageView(location.pathname)
  }, [location.pathname])

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedLayout />}>
        <Route path="/overview" element={<OverviewPage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="/apps" element={<AppsPage />} />
        <Route path="/records" element={<RecordsPage />} />
        <Route path="/senders" element={<SendersPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/advanced" element={<AdvancedPage />} />
        <Route path="/" element={<Navigate to="/overview" replace />} />
      </Route>
      <Route path="*" element={<Navigate to="/overview" replace />} />
    </Routes>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  )
}
