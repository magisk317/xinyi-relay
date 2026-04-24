import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { listen } from '@tauri-apps/api/event'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { useDesktopI18n } from './i18n'
import { DesktopShell } from './ui'
import { useDesktop } from './state/DesktopContext'
import { LoginPage } from './pages/LoginPage'
import { OverviewPage } from './pages/OverviewPage'
import { DevicesPage } from './pages/DevicesPage'
import { RecordsPage } from './pages/RecordsPage'
import { ConfigPage } from './pages/ConfigPage'
import { SendersPage } from './pages/SendersPage'
import { AppsPage } from './pages/AppsPage'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { AdvancedPage } from './pages/AdvancedPage'

type DesktopWindowBridge = Window & {
  __desktopNavigate?: (path: string, stamp?: number) => void
  __desktopAction?: (kind: string) => void
}

function ProtectedRoutes() {
  const { bootstrap, loading } = useDesktop()
  const { t } = useDesktopI18n()

  if (loading) {
    return <div className="loading-screen">{t('common.preparing')}</div>
  }

  if (!bootstrap?.session.authenticated) {
    return <Navigate to="/login" replace />
  }

  return <DesktopShell />
}

export default function App() {
  const navigate = useNavigate()
  const location = useLocation()
  const { refreshBootstrap } = useDesktop()
  const { appName, t } = useDesktopI18n()

  useEffect(() => {
    const routeTitle = resolveRouteTitle(location.pathname, t)
    const nextTitle = `${appName} · ${routeTitle}`
    document.title = nextTitle
    void getCurrentWindow().setTitle(nextTitle).catch(() => {})
  }, [appName, location.pathname, t])

  useEffect(() => {
    const desktopWindow = window as DesktopWindowBridge
    desktopWindow.__desktopNavigate = (path: string) => {
      navigate(path)
    }
    desktopWindow.__desktopAction = (kind: string) => {
      if (kind === 'restart-monitor') {
        void refreshBootstrap()
      }
    }

    const unlistenPromises = [
      listen<{ path: string }>('desktop://navigate', ({ payload }) => {
        if (!payload.path) return
        navigate(payload.path)
      }),
      listen<{ kind: string }>('desktop://action', ({ payload }) => {
        if (payload.kind === 'restart-monitor') {
          void refreshBootstrap()
        }
      })
    ]

    return () => {
      delete desktopWindow.__desktopNavigate
      delete desktopWindow.__desktopAction
      void Promise.all(unlistenPromises).then((items) => {
        items.forEach((dispose) => dispose())
      })
    }
  }, [navigate, refreshBootstrap])

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoutes />}>
        <Route path="/overview" element={<OverviewPage />} />
        <Route path="/devices" element={<DevicesPage />} />
        <Route path="/records" element={<RecordsPage />} />
        <Route path="/config" element={<ConfigPage />} />
        <Route path="/senders" element={<SendersPage />} />
        <Route path="/apps" element={<AppsPage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="/advanced" element={<AdvancedPage />} />
        <Route path="/" element={<Navigate to="/overview" replace />} />
      </Route>
      <Route path="*" element={<Navigate to="/overview" replace />} />
    </Routes>
  )
}

function resolveRouteTitle(pathname: string, t: ReturnType<typeof useDesktopI18n>['t']) {
  switch (pathname) {
    case '/login':
      return t('app.route.login')
    case '/devices':
      return t('app.route.devices')
    case '/records':
      return t('app.route.records')
    case '/config':
      return t('app.route.config')
    case '/senders':
      return t('app.route.senders')
    case '/apps':
      return t('app.route.apps')
    case '/analytics':
      return t('app.route.analytics')
    case '/advanced':
      return t('app.route.advanced')
    case '/overview':
    case '/':
    default:
      return t('app.route.overview')
  }
}
