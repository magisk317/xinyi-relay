import { type FormEvent, useState } from 'react'
import { Alert, Badge, Button, Card, Label, Spinner, TextInput } from 'flowbite-react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { trackEvent } from '../analytics'

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
      trackEvent('login_success')
      navigate('/overview', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-[linear-gradient(180deg,#eef6ff_0%,#f8fafc_100%)] px-6 py-10">
      <div className="mx-auto flex min-h-[calc(100vh-5rem)] max-w-6xl flex-col justify-center">
        <div className="mb-8 flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[linear-gradient(135deg,#0f172a,#0ea5e9)] text-lg font-semibold text-white">
            R
          </div>
          <div>
            <div className="text-2xl font-semibold text-slate-950">信驿 Relay WebUI</div>
            <div className="text-sm text-slate-500">嵌入式控制台</div>
          </div>
        </div>

        <Card className="w-full border border-slate-200/80 shadow-xl shadow-slate-200/60 md:[&>*]:p-0">
          <div className="grid md:grid-cols-[1.05fr_0.95fr]">
            <div className="border-b border-slate-200/80 bg-[linear-gradient(135deg,#082f49,#0f172a)] p-8 text-white md:border-b-0 md:border-r">
              <Badge color="info" className="w-fit">
                Embedded Runtime Console
              </Badge>
              <h1 className="mt-6 text-4xl font-semibold tracking-tight">更轻的入口，更直接地管理 Relay</h1>
              <p className="mt-4 text-sm leading-7 text-slate-200">登录后可以查看概览、记录、通道与高级设置。</p>
              <div className="mt-8 grid gap-4 sm:grid-cols-3">
                <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                  <p className="text-sm text-slate-300">默认用户名</p>
                  <p className="mt-2 text-lg font-semibold">relay</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                  <p className="text-sm text-slate-300">推荐模式</p>
                  <p className="mt-2 text-lg font-semibold">HTTPS 本机访问</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                  <p className="text-sm text-slate-300">后台保活</p>
                  <p className="mt-2 text-lg font-semibold">查看状态栏服务</p>
                </div>
              </div>
            </div>

            <form className="p-8" onSubmit={onSubmit}>
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm uppercase tracking-[0.24em] text-cyan-700">Relay</p>
                  <h2 className="mt-2 text-3xl font-semibold text-slate-950">登录 WebUI</h2>
                </div>
                <Badge color={loading ? 'warning' : 'success'}>
                  {loading ? '正在连接' : '连接就绪'}
                </Badge>
              </div>

              <p className="mt-3 text-sm leading-6 text-slate-500">
                如果看到连接超时或 <code className="rounded bg-slate-100 px-1.5 py-0.5">failed to fetch</code>，
                请先把主应用切回前台，或确认状态栏中的 WebUI 服务通知仍在。
              </p>

              <div className="mt-6 flex flex-col gap-y-3">
                <Label htmlFor="webui-username">用户名</Label>
                <TextInput
                  id="webui-username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  placeholder="relay"
                />
              </div>

              <div className="mt-5 flex flex-col gap-y-3">
                <Label htmlFor="webui-password">密码</Label>
                <TextInput
                  id="webui-password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  placeholder="输入 WebUI 密码"
                />
              </div>

              {error && (
                <Alert color="failure" className="mt-5">
                  {error}
                </Alert>
              )}

              <Button className="mt-6 w-full" type="submit" color="info" disabled={submitting}>
                {submitting ? (
                  <div className="flex items-center gap-2">
                    <Spinner size="sm" />
                    正在验证连接...
                  </div>
                ) : (
                  '进入 WebUI'
                )}
              </Button>

              <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-900">
                如果登录特别慢，通常不是账号错误，而是系统已把主应用后台冻结。
              </div>
            </form>
          </div>
        </Card>
      </div>
    </div>
  )
}
