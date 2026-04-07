import { type FormEvent, type ReactNode, useState } from 'react'
import { useEffect } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { trackEvent } from '../analytics'
import { apiClient } from '../api/client'
import { useI18n } from '../i18n'
import { ActionButton, cx } from '../template'

export function LoginPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const { authenticated, loading, connected, login } = useAuth()
  const [username, setUsername] = useState('relay')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [adminInitialized, setAdminInitialized] = useState(true)

  useEffect(() => {
    let cancelled = false
    void apiClient
      .getSystemInfo()
      .then((info) => {
        if (!cancelled) {
          setAdminInitialized(info.userCount > 0)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setAdminInitialized(true)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

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
      setError(err instanceof Error ? err.message : t('login.failed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="relative min-h-screen overflow-hidden bg-[radial-gradient(circle_at_top,#fbfef3_0%,#f0f8db_42%,#e6f0ca_100%)] px-5 py-6 sm:px-6 sm:py-8">
      <div className="absolute inset-x-0 top-0 h-[320px] bg-[radial-gradient(circle_at_top,rgba(188,230,32,0.26),transparent_46%)]" />
      <div className="absolute right-[-80px] top-[18%] h-[240px] w-[240px] rounded-full bg-[rgba(206,241,85,0.26)] blur-3xl" />
      <div className="absolute left-[-100px] bottom-[12%] h-[260px] w-[260px] rounded-full bg-[rgba(151,191,29,0.16)] blur-3xl" />

      <div className="relative mx-auto flex min-h-[calc(100vh-3rem)] max-w-xl items-center justify-center">
        <section className="w-full rounded-[36px] border border-[#d8e9a6]/72 bg-[rgba(252,255,245,0.86)] px-6 py-8 shadow-[0_30px_80px_-44px_rgba(98,122,28,0.22)] backdrop-blur-xl sm:px-8 sm:py-10">
          <div className="flex flex-col items-center text-center">
            <div className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-[28px] bg-white ring-1 ring-[#dbe9a9] shadow-[0_18px_50px_-26px_rgba(98,122,28,0.36)]">
              <img src="/app-logo.png" alt="Xinyi Relay" className="h-full w-full object-cover" />
            </div>
            <div className="mt-5 text-[11px] font-semibold uppercase tracking-[0.32em] text-[#708b23]">{t('login.brand')}</div>
            <h1 className="mt-3 text-4xl font-semibold tracking-[-0.06em] text-[#243115]">{t('login.title')}</h1>
            <div
              className={cx(
                'mt-5 rounded-full px-3 py-2 text-sm font-medium',
                loading
                  ? 'bg-[#fff7de] text-[#9a6412]'
                  : connected
                    ? 'bg-[#ecf8cb] text-[#58711e]'
                    : 'bg-[#fff4ef] text-[#b24a24]'
              )}
            >
              {loading ? t('login.status.connecting') : connected ? t('login.status.ready') : t('login.status.failed')}
            </div>
          </div>

          <form className="mt-8 space-y-5" onSubmit={onSubmit}>
              <Field label={t('login.username')}>
                <input
                  id="webui-username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  placeholder="relay"
                  className="relay-input"
                />
              </Field>

              <Field label={t('login.password')}>
                <input
                  id="webui-password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  placeholder={t('login.passwordPlaceholder')}
                  className="relay-input"
                />
              </Field>

              {error && (
                <div className="rounded-[22px] border border-[#f7c9bf] bg-[#fff8f3] px-4 py-3 text-sm leading-6 text-[#b24a24]">
                  {error}
                </div>
              )}

              <ActionButton
                type="submit"
                tone="primary"
                disabled={submitting}
                className="w-full rounded-[22px] py-3 text-base font-semibold"
              >
                {submitting ? t('login.submitting') : t('login.submit')}
              </ActionButton>

              {!adminInitialized ? (
                <div className="rounded-[22px] border border-[#d7e6a6] bg-[#f9fce9] px-4 py-3 text-sm leading-6 text-[#5f6f47]">
                  {t('login.bootstrapHint')}
                </div>
              ) : null}
          </form>
        </section>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <div className="mb-2 text-sm font-medium text-[#243115]">{label}</div>
      {children}
    </label>
  )
}
