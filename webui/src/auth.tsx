import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren
} from 'react'
import { apiClient, setCsrfToken } from './api/client'
import { useI18n } from './i18n'

type AuthState = {
  loading: boolean
  connected: boolean
  authenticated: boolean
  username: string
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: PropsWithChildren) {
  const { syncServerLanguageTag } = useI18n()
  const [loading, setLoading] = useState(true)
  const [connected, setConnected] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)
  const [username, setUsername] = useState('')

  useEffect(() => {
    let cancelled = false
    const bootstrap = async () => {
      try {
        const me = await apiClient.me()
        if (cancelled) return
        setConnected(true)
        syncServerLanguageTag(me.languageTag ?? '')
        if (me.authenticated && me.username && me.csrfToken) {
            setAuthenticated(true)
            setUsername(me.username)
          setCsrfToken(me.csrfToken)
        } else {
          setAuthenticated(false)
          setUsername('')
          setCsrfToken('')
        }
      } catch {
        if (!cancelled) {
          setConnected(false)
          setAuthenticated(false)
          setUsername('')
          setCsrfToken('')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }
    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [syncServerLanguageTag])

  const login = useCallback(async (inputUsername: string, password: string) => {
    try {
      const resp = await apiClient.login(inputUsername, password)
      setConnected(true)
      setAuthenticated(resp.authenticated)
      setUsername(resp.username)
      setCsrfToken(resp.csrfToken)
      syncServerLanguageTag(resp.languageTag ?? '')
    } catch (error) {
      if (error instanceof Error) {
        const message = error.message
        if (
          message.includes('Unable to connect to the remote backend') ||
          message.includes('Unable to connect') ||
          message.includes('Connection timed out') ||
          message.includes('无法连接到远程后端') ||
          message.includes('连接超时')
        ) {
          setConnected(false)
        } else {
          setConnected(true)
        }
      }
      throw error
    }
  }, [syncServerLanguageTag])

  const logout = useCallback(async () => {
    await apiClient.logout()
    setConnected(true)
    setAuthenticated(false)
    setUsername('')
    setCsrfToken('')
  }, [])

  const value = useMemo(
    () => ({
      loading,
      connected,
      authenticated,
      username,
      login,
      logout
    }),
    [authenticated, connected, loading, login, logout, username]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return ctx
}
