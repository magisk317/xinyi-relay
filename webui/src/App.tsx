import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import { AppLayout } from './layout'
import { AdvancedPage } from './pages/AdvancedPage'
import { AppsPage } from './pages/AppsPage'
import { LoginPage } from './pages/LoginPage'
import { OverviewPage } from './pages/OverviewPage'
import { RecordsPage } from './pages/RecordsPage'
import { SendersPage } from './pages/SendersPage'
import { SettingsPage } from './pages/SettingsPage'

function ProtectedLayout() {
  const { loading, authenticated } = useAuth()
  if (loading) {
    return <div className="p-6 text-sm text-slate-500">加载中...</div>
  }
  if (!authenticated) {
    return <Navigate to="/login" replace />
  }
  return <AppLayout />
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedLayout />}>
        <Route path="/overview" element={<OverviewPage />} />
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
