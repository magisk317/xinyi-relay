import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren
} from 'react'
import { listen } from '@tauri-apps/api/event'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { desktopApi, type SaveProfileInput } from '../api/desktopApi'
import type {
  DesktopAuthCallbackPayload,
  DesktopAuthStart,
  DesktopBackendProbe,
  DesktopBootstrapState,
  DesktopConnectionSnapshot,
  DesktopDiagnosticsExport,
  DesktopNotificationPreferences,
  DesktopProfile,
  RealtimeEvent
} from '../../../shared/contracts/console'

type DesktopContextValue = {
  bootstrap: DesktopBootstrapState | null
  loading: boolean
  error: string
  authBusy: boolean
  activeProfile: DesktopProfile | null
  pendingAuthStart: DesktopAuthStart | null
  connection: DesktopConnectionSnapshot
  lastRealtimeEvent: RealtimeEvent | null
  lastDiagnosticsExport: DesktopDiagnosticsExport | null
  lastProbe: DesktopBackendProbe | null
  refreshBootstrap: () => Promise<void>
  saveProfile: (profile: SaveProfileInput) => Promise<void>
  deleteProfile: (profileId: string) => Promise<void>
  setActiveProfile: (profileId: string) => Promise<void>
  probeBackend: (profileId: string) => Promise<void>
  beginBrowserLogin: (profileId: string) => Promise<void>
  retryPendingBrowserOpen: () => Promise<void>
  completeBrowserLogin: (code: string, state: string) => Promise<void>
  logout: () => Promise<void>
  exportDiagnostics: () => Promise<void>
  updateNotifications: (preferences: DesktopNotificationPreferences) => Promise<void>
  sendTestNotification: () => Promise<void>
}

const DesktopContext = createContext<DesktopContextValue | null>(null)

const defaultConnection: DesktopConnectionSnapshot = {
  state: 'connecting',
  message: ''
}

export function DesktopProvider({ children }: PropsWithChildren) {
  const [bootstrap, setBootstrap] = useState<DesktopBootstrapState | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [authBusy, setAuthBusy] = useState(false)
  const [pendingAuthStart, setPendingAuthStart] = useState<DesktopAuthStart | null>(null)
  const [connection, setConnection] = useState<DesktopConnectionSnapshot>(defaultConnection)
  const [lastRealtimeEvent, setLastRealtimeEvent] = useState<RealtimeEvent | null>(null)
  const [lastDiagnosticsExport, setLastDiagnosticsExport] = useState<DesktopDiagnosticsExport | null>(null)
  const [lastProbe, setLastProbe] = useState<DesktopBackendProbe | null>(null)

  const syncBootstrap = useCallback((nextValue: DesktopBootstrapState) => {
    setBootstrap(nextValue)
    setConnection(nextValue.connection)
  }, [])

  const resolveErrorMessage = useCallback((nextError: unknown, fallback: string) => (
    nextError instanceof Error ? nextError.message : fallback
  ), [])

  const openExternalUrl = useCallback(async (url: string) => {
    await desktopApi.openExternalUrl(url)
  }, [])

  const refreshBootstrap = useCallback(async () => {
    try {
      setError('')
      const payload = await desktopApi.bootstrap()
      syncBootstrap(payload)
      if (payload.session.authenticated) {
        setPendingAuthStart(null)
      }
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Failed to bootstrap desktop runtime.')
    } finally {
      setLoading(false)
    }
  }, [syncBootstrap])

  const completeBrowserLogin = useCallback(async (code: string, state: string) => {
    try {
      console.info('[desktop-auth] exchanging desktop browser login', { state })
      setAuthBusy(true)
      await desktopApi.exchangeBrowserLogin(code, state)
      console.info('[desktop-auth] desktop browser login exchange succeeded', { state })
      setPendingAuthStart(null)
      await refreshBootstrap()
      void getCurrentWindow().setFocus().catch(() => {})
    } catch (nextError) {
      console.error('[desktop-auth] desktop browser login exchange failed', nextError)
      setError(resolveErrorMessage(nextError, 'Desktop login exchange failed.'))
      throw nextError
    } finally {
      setAuthBusy(false)
    }
  }, [refreshBootstrap, resolveErrorMessage])

  useEffect(() => {
    void refreshBootstrap()
  }, [refreshBootstrap])

  useEffect(() => {
    const unlistenPromises = [
      listen<DesktopAuthCallbackPayload>('desktop://auth-callback', async ({ payload }) => {
        if (payload.error) {
          setAuthBusy(false)
          setPendingAuthStart(null)
          setError(payload.error)
          return
        }
        if (!payload.code) {
          setAuthBusy(false)
          setError('Desktop login callback did not include an authorization code.')
          return
        }
        try {
          await completeBrowserLogin(payload.code, payload.state)
        } catch (nextError) {
          setError(nextError instanceof Error ? nextError.message : 'Desktop login exchange failed.')
        }
      }),
      listen<DesktopConnectionSnapshot>('desktop://connection', ({ payload }) => {
        setConnection(payload)
      }),
      listen<RealtimeEvent>('desktop://realtime', ({ payload }) => {
        setLastRealtimeEvent(payload)
      })
    ]

    return () => {
      void Promise.all(unlistenPromises).then((items) => {
        items.forEach((dispose) => dispose())
      })
    }
  }, [completeBrowserLogin, refreshBootstrap])

  const saveProfile = useCallback(async (profile: SaveProfileInput) => {
    try {
      setError('')
      setLastProbe(null)
      setPendingAuthStart(null)
      const payload = await desktopApi.saveProfile(profile)
      syncBootstrap(payload)
    } catch (nextError) {
      setError(resolveErrorMessage(nextError, 'Failed to save desktop backend profile.'))
      throw nextError
    }
  }, [resolveErrorMessage, syncBootstrap])

  const deleteProfile = useCallback(async (profileId: string) => {
    try {
      setError('')
      setLastProbe(null)
      setPendingAuthStart(null)
      const payload = await desktopApi.deleteProfile(profileId)
      syncBootstrap(payload)
    } catch (nextError) {
      setError(resolveErrorMessage(nextError, 'Failed to delete desktop backend profile.'))
      throw nextError
    }
  }, [resolveErrorMessage, syncBootstrap])

  const setActiveProfile = useCallback(async (profileId: string) => {
    try {
      setError('')
      setLastProbe(null)
      setPendingAuthStart(null)
      const payload = await desktopApi.setActiveProfile(profileId)
      syncBootstrap(payload)
    } catch (nextError) {
      setError(resolveErrorMessage(nextError, 'Failed to switch desktop backend profile.'))
      throw nextError
    }
  }, [resolveErrorMessage, syncBootstrap])

  const probeBackend = useCallback(async (profileId: string) => {
    try {
      setError('')
      const payload = await desktopApi.probeBackend(profileId)
      setLastProbe(payload)
    } catch (nextError) {
      const message = resolveErrorMessage(nextError, 'Failed to probe desktop backend.')
      setError(message)
      throw nextError
    }
  }, [resolveErrorMessage])

  const beginBrowserLogin = useCallback(async (profileId: string) => {
    setError('')
    setAuthBusy(true)
    try {
      const auth = await desktopApi.startBrowserLogin(profileId)
      console.info('[desktop-auth] desktop browser login started', { profileId, callbackUrl: auth.callbackUrl })
      setPendingAuthStart(auth)
      try {
        await openExternalUrl(auth.authUrl)
        setAuthBusy(false)
      } catch (nextError) {
        console.error('[desktop-auth] opening desktop browser login failed', nextError)
        setAuthBusy(false)
        setError(resolveErrorMessage(
          nextError,
          'Failed to open desktop login in your browser. Copy the login URL below and open it manually.'
        ))
        throw nextError
      }
    } catch (nextError) {
      setAuthBusy(false)
      setError(resolveErrorMessage(nextError, 'Failed to start desktop login.'))
      throw nextError
    }
  }, [openExternalUrl, resolveErrorMessage])

  const retryPendingBrowserOpen = useCallback(async () => {
    if (!pendingAuthStart) {
      throw new Error('No pending desktop login URL is available.')
    }

    setError('')
    setAuthBusy(true)
    try {
      await openExternalUrl(pendingAuthStart.authUrl)
      setAuthBusy(false)
    } catch (nextError) {
      setAuthBusy(false)
      setError(resolveErrorMessage(
        nextError,
        'Failed to open desktop login in your browser. Copy the login URL below and open it manually.'
      ))
      throw nextError
    }
  }, [openExternalUrl, pendingAuthStart, resolveErrorMessage])

  const logout = useCallback(async () => {
    try {
      setError('')
      setPendingAuthStart(null)
      const payload = await desktopApi.logout()
      syncBootstrap(payload)
    } catch (nextError) {
      setError(resolveErrorMessage(nextError, 'Failed to sign out from the desktop backend.'))
      throw nextError
    }
  }, [resolveErrorMessage, syncBootstrap])

  const exportDiagnostics = useCallback(async () => {
    try {
      setError('')
      const payload = await desktopApi.exportDiagnostics()
      setLastDiagnosticsExport(payload)
    } catch (nextError) {
      setError(resolveErrorMessage(nextError, 'Failed to export desktop diagnostics.'))
      throw nextError
    }
  }, [resolveErrorMessage])

  const updateNotifications = useCallback(async (preferences: DesktopNotificationPreferences) => {
    try {
      setError('')
      const payload = await desktopApi.updateNotifications(preferences)
      syncBootstrap(payload)
    } catch (nextError) {
      setError(resolveErrorMessage(nextError, 'Failed to update desktop notification preferences.'))
      throw nextError
    }
  }, [resolveErrorMessage, syncBootstrap])

  const sendTestNotification = useCallback(async () => {
    try {
      setError('')
      await desktopApi.sendTestNotification()
    } catch (nextError) {
      setError(resolveErrorMessage(nextError, 'Failed to send desktop test notification.'))
      throw nextError
    }
  }, [resolveErrorMessage])

  const activeProfile = useMemo(
    () => bootstrap?.profiles.find((profile) => profile.active) ?? null,
    [bootstrap?.profiles]
  )

  const value = useMemo<DesktopContextValue>(() => ({
    bootstrap,
    loading,
    error,
    authBusy,
    activeProfile,
    pendingAuthStart,
    connection,
    lastRealtimeEvent,
    lastDiagnosticsExport,
    lastProbe,
    refreshBootstrap,
    saveProfile,
    deleteProfile,
    setActiveProfile,
    probeBackend,
    beginBrowserLogin,
    retryPendingBrowserOpen,
    completeBrowserLogin,
    logout,
    exportDiagnostics,
    updateNotifications,
    sendTestNotification
  }), [
    activeProfile,
    authBusy,
    bootstrap,
    completeBrowserLogin,
    connection,
    error,
    exportDiagnostics,
    lastDiagnosticsExport,
    lastProbe,
    lastRealtimeEvent,
    loading,
    beginBrowserLogin,
    deleteProfile,
    logout,
    pendingAuthStart,
    probeBackend,
    refreshBootstrap,
    retryPendingBrowserOpen,
    saveProfile,
    sendTestNotification,
    setActiveProfile,
    updateNotifications
  ])

  return <DesktopContext.Provider value={value}>{children}</DesktopContext.Provider>
}

export function useDesktop() {
  const value = useContext(DesktopContext)
  if (!value) {
    throw new Error('useDesktop must be used inside DesktopProvider')
  }
  return value
}
