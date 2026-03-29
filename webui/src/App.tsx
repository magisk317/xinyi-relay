import { useEffect } from 'react'
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
import { useI18n } from './i18n'
import { RelaySpinner } from './template'

function ProtectedLayout() {
  const { t } = useI18n()
  const { loading, authenticated } = useAuth()
  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[linear-gradient(180deg,#fbfef2_0%,#eef7d7_100%)]">
        <div className="flex items-center gap-3 rounded-[24px] border border-[#d6e7a2] bg-white/90 px-5 py-4 text-sm text-[#5f6d45] shadow-[0_20px_50px_-36px_rgba(98,122,28,0.24)]">
          <RelaySpinner />
          {t('app.embeddedConnecting')}
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
