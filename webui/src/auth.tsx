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

type AuthState = {
  loading: boolean
  authenticated: boolean
  username: string
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: PropsWithChildren) {
  const [loading, setLoading] = useState(true)
  const [authenticated, setAuthenticated] = useState(false)
  const [username, setUsername] = useState('')

  useEffect(() => {
    let cancelled = false
    const bootstrap = async () => {
      try {
        const me = await apiClient.me()
        if (cancelled) return
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
  }, [])

  const login = useCallback(async (inputUsername: string, password: string) => {
    const resp = await apiClient.login(inputUsername, password)
    setAuthenticated(resp.authenticated)
    setUsername(resp.username)
    setCsrfToken(resp.csrfToken)
  }, [])

  const logout = useCallback(async () => {
    await apiClient.logout()
    setAuthenticated(false)
    setUsername('')
    setCsrfToken('')
  }, [])

  const value = useMemo(
    () => ({
      loading,
      authenticated,
      username,
      login,
      logout
    }),
    [authenticated, loading, login, logout, username]
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
