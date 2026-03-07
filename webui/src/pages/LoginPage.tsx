import { type FormEvent, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'

export function LoginPage() {
  const navigate = useNavigate()
  const { authenticated, loading, login } = useAuth()
  const [username, setUsername] = useState('relay')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  if (!loading && authenticated) {
    return <Navigate to="/overview" replace />
  }

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await login(username, password)
      navigate('/overview', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <form className="w-full max-w-md rounded-xl border bg-white p-6 shadow" onSubmit={onSubmit}>
        <h1 className="text-xl font-semibold">信驿 Relay WebUI</h1>
        <p className="mt-1 text-sm text-slate-500">使用应用内设置的 WebUI 账号密码登录</p>

        <label className="mt-4 block text-sm font-medium">用户名</label>
        <input
          className="mt-1 w-full rounded border px-3 py-2"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
        />

        <label className="mt-3 block text-sm font-medium">密码</label>
        <input
          className="mt-1 w-full rounded border px-3 py-2"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />

        {error && <p className="mt-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        <button
          className="mt-4 w-full rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
          type="submit"
          disabled={submitting}
        >
          {submitting ? '登录中...' : '登录'}
        </button>
      </form>
    </div>
  )
}
