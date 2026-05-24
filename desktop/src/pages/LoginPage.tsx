import { useEffect, useMemo, useState, type Dispatch, type SetStateAction } from 'react'
import { useNavigate } from 'react-router-dom'
import { ProfileManagerPanel } from '../components/ProfileManagerPanel'
import { useDesktopI18n } from '../i18n'
import { useDesktop } from '../state/DesktopContext'
import { LoginShell, Panel, Tag } from '../ui'

export function LoginPage() {
  const navigate = useNavigate()
  const { t } = useDesktopI18n()
  const {
    session,
    activeProfile,
    authBusy,
    beginBrowserLogin,
    completeBrowserLogin,
    pendingAuthStart,
    retryPendingBrowserOpen,
    error,
    lastProbe,
    probeBackend
  } = useDesktop()
  const [manualAuthInput, setManualAuthInput] = useState('')
  const [copiedKey, setCopiedKey] = useState<'auth' | 'callback' | null>(null)
  const [autoSubmitting, setAutoSubmitting] = useState(false)

  useEffect(() => {
    if (session.authenticated) {
      navigate('/overview', { replace: true })
    }
  }, [navigate, session.authenticated])

  useEffect(() => {
    setManualAuthInput('')
    setCopiedKey(null)
    setAutoSubmitting(false)
  }, [pendingAuthStart?.authUrl])

  const manualAuthDetails = useMemo(
    () => parseManualAuthInput(manualAuthInput, pendingAuthStart?.state),
    [manualAuthInput, pendingAuthStart?.state]
  )

  useEffect(() => {
    if (!manualAuthDetails || authBusy || autoSubmitting) {
      return
    }

    const trimmed = manualAuthInput.trim()
    if (!trimmed.startsWith('http://127.0.0.1') && !trimmed.startsWith('https://127.0.0.1')) {
      return
    }

    setAutoSubmitting(true)
    void completeBrowserLogin(manualAuthDetails.code, manualAuthDetails.state)
      .catch(() => {})
      .finally(() => {
        setAutoSubmitting(false)
      })
  }, [authBusy, autoSubmitting, completeBrowserLogin, manualAuthDetails, manualAuthInput])

  return (
    <LoginShell>
      <div className="stack">
        <Panel
          title={t('login.panelTitle')}
          actions={session.authenticated ? <Tag tone="success">{t('login.alreadyAuthenticated')}</Tag> : null}
        >
          <div className="stack">
            {error ? <div className="banner banner--danger">{error}</div> : null}
            <div className="info-row">
              <span>{t('login.profile')}</span>
              <strong>{activeProfile?.name ?? t('login.noActiveProfile')}</strong>
            </div>
            <div className="info-row">
              <span>{t('login.backend')}</span>
              <strong>{activeProfile?.baseUrl ?? t('login.selectProfile')}</strong>
            </div>
            <div className="button-row">
              <button
                type="button"
                className="primary-button"
                disabled={!activeProfile || authBusy}
                onClick={() => activeProfile && void beginBrowserLogin(activeProfile.id).catch(() => {})}
              >
                {authBusy ? t('login.waitingCallback') : t('login.continueInBrowser')}
              </button>
              <button
                type="button"
                className="ghost-button"
                disabled={!activeProfile}
                onClick={() => activeProfile && void probeBackend(activeProfile.id).catch(() => {})}
              >
                {t('login.testBackend')}
              </button>
            </div>

            {pendingAuthStart ? (
              <div className="callout">
                <div className="callout-title">{t('login.manualTitle')}</div>
                <div className="stack">
                  <div className="field">
                    <span>{t('login.authUrl')}</span>
                    <textarea className="text-area-field auth-link-field" rows={3} value={pendingAuthStart.authUrl} readOnly />
                  </div>
                  <div className="button-row">
                    <button
                      type="button"
                      className="ghost-button"
                      onClick={() => void copyText(pendingAuthStart.authUrl, 'auth', setCopiedKey)}
                    >
                      {copiedKey === 'auth' ? t('common.copied') : t('login.copyLoginUrl')}
                    </button>
                    <button
                      type="button"
                      className="ghost-button"
                      onClick={() => void retryPendingBrowserOpen().catch(() => {})}
                    >
                      {t('login.retryOpenBrowser')}
                    </button>
                  </div>
                  <div className="field">
                    <span>{t('login.callbackUrl')}</span>
                    <input className="text-input" value={pendingAuthStart.callbackUrl} readOnly />
                  </div>
                  <div className="button-row">
                    <button
                      type="button"
                      className="ghost-button"
                      onClick={() => void copyText(pendingAuthStart.callbackUrl, 'callback', setCopiedKey)}
                    >
                      {copiedKey === 'callback' ? t('common.copied') : t('login.copyCallbackUrl')}
                    </button>
                  </div>
                  <div className="field">
                    <span>{t('login.callbackInput')}</span>
                    <textarea
                      className="text-area-field auth-link-field"
                      rows={3}
                      value={manualAuthInput}
                      placeholder={t('login.callbackPlaceholder')}
                      onChange={(event) => setManualAuthInput(event.target.value)}
                    />
                  </div>
                  <div className="button-row">
                    <button
                      type="button"
                      className="primary-button"
                      disabled={!manualAuthDetails || authBusy || autoSubmitting}
                      onClick={() => {
                        if (!manualAuthDetails) return
                        setAutoSubmitting(true)
                        void completeBrowserLogin(manualAuthDetails.code, manualAuthDetails.state)
                          .catch(() => {})
                          .finally(() => {
                            setAutoSubmitting(false)
                          })
                      }}
                    >
                      {authBusy || autoSubmitting ? t('login.waitingCallback') : t('login.completeAuth')}
                    </button>
                  </div>
                  {autoSubmitting ? (
                    <div className="callout-meta">{t('login.autoSubmitting')}</div>
                  ) : null}
                </div>
              </div>
            ) : null}

            {lastProbe ? (
              <div className="callout">
                <div className="callout-title">{t('login.probeResult')}</div>
                <div>{lastProbe.message}</div>
                <div className="callout-meta">
                  TLS: {lastProbe.certificateStatus} · Self-signed override: {lastProbe.allowSelfSigned ? 'enabled' : 'disabled'}
                </div>
              </div>
            ) : null}
          </div>
        </Panel>

        <ProfileManagerPanel
          title={t('advanced.backendProfilesTitle')}
        />
      </div>
    </LoginShell>
  )
}

function parseManualAuthInput(value: string, fallbackState?: string) {
  const trimmed = value.trim()
  if (!trimmed) return null

  try {
    const parsed = new URL(trimmed)
    const code = parsed.searchParams.get('code')?.trim()
    const state = parsed.searchParams.get('state')?.trim() || fallbackState?.trim()
    if (code && state) {
      return { code, state }
    }
  } catch {
    // Treat plain text as a one-time code.
  }

  if (!fallbackState) {
    return null
  }

  return {
    code: trimmed,
    state: fallbackState
  }
}

async function copyText(
  value: string,
  copiedKey: 'auth' | 'callback',
  setCopiedKey: Dispatch<SetStateAction<'auth' | 'callback' | null>>
) {
  await navigator.clipboard.writeText(value)
  setCopiedKey(copiedKey)
  window.setTimeout(() => {
    setCopiedKey((current) => (current === copiedKey ? null : current))
  }, 1500)
}
