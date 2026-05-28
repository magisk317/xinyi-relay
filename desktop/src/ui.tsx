import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
  type ReactNode
} from 'react'
import appLogo from '../../webui/public/app-logo.png'
import { useDesktopI18n } from './i18n'
import { useDesktop } from './state/DesktopContext'

export function DesktopShell() {
  const navigate = useNavigate()
  const { activeProfile, connection, error, logout, refreshBootstrap, session, runMode } = useDesktop()
  const { brandName, t } = useDesktopI18n()
  const localProfileName = t('shell.localProfileName')
  const localProfileUrl = t('shell.localProfileUrl')
  const [showBackendUrl, setShowBackendUrl] = useState(false)
  const nav = [
    ['overview', t('app.route.overview')],
    ['devices', t('app.route.devices')],
    ['records', t('app.route.records')],
    ['config', t('app.route.config')],
    ['senders', t('app.route.senders')],
    ['apps', t('app.route.apps')],
    ['analytics', t('app.route.analytics')],
    ['advanced', t('app.route.advanced')]
  ]

  return (
    <div className="desktop-shell">
      <aside className="desktop-sidebar">
        <button type="button" className="brand-block" onClick={() => navigate('/overview')}>
          <div className="brand-chip">
            <img src={appLogo} alt={brandName} className="brand-logo" />
          </div>
          <div>
            <div className="brand-title">{brandName}</div>
          </div>
        </button>

        <div className="sidebar-section">
          <div className="sidebar-label">{t('shell.activeBackend')}</div>
          <div className="profile-card">
            <div className="profile-card-head">
              <div className="profile-name">
                {runMode === 'local' ? localProfileName : (activeProfile?.name ?? t('shell.noProfile'))}
              </div>
              {runMode !== 'local' && activeProfile?.baseUrl ? (
                <button
                  type="button"
                  className="icon-toggle-button"
                  aria-label={showBackendUrl ? t('shell.hideBackend') : t('shell.showBackend')}
                  title={showBackendUrl ? t('shell.hideBackend') : t('shell.showBackend')}
                  onClick={(event) => {
                    event.stopPropagation()
                    setShowBackendUrl((value) => !value)
                  }}
                >
                  {showBackendUrl ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              ) : null}
            </div>
            <div className={`profile-url${showBackendUrl ? '' : ' profile-url--hidden'}`}>
              {runMode === 'local'
                ? localProfileUrl
                : activeProfile?.baseUrl
                  ? (showBackendUrl ? activeProfile.baseUrl : maskSensitiveText(activeProfile.baseUrl))
                  : '—'}
            </div>
          </div>
        </div>

        <div className="sidebar-section">
          <div className="sidebar-label">{t('shell.mode')}</div>
          <Tag tone={runModeTone(runMode)}>{t(`status.${runMode}`) || runMode.toUpperCase()}</Tag>
        </div>

        <nav className="nav-rail">
          {nav.map(([to, label]) => (
            <NavLink
              key={to}
              to={`/${to}`}
              className={({ isActive }) => `nav-link${isActive ? ' nav-link--active' : ''}`}
            >
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <Tag tone={connectionTone(connection.state)}>{translateConnectionState(connection.state, t).toUpperCase()}</Tag>
          <button type="button" className="ghost-button" onClick={() => void (session.authenticated ? logout() : refreshBootstrap())}>
            {session.authenticated ? t('shell.signOut') : t('shell.refresh')}
          </button>
        </div>
      </aside>

      <main className="desktop-main">
        <header className="desktop-topbar">
          <div>
            <div className="eyebrow">{t('shell.desktopNative')}</div>
            <h1>{brandName}</h1>
          </div>
          <div className="topbar-status">
            <Tag tone={connectionTone(connection.state)}>{connection.message}</Tag>
            {session.authenticated ? <Tag tone="success">{session.username}</Tag> : null}
          </div>
        </header>

        {error ? <div className="banner banner--danger">{t(error)}</div> : null}
        <Outlet />
      </main>
    </div>
  )
}

export function LoginShell({ children }: PropsWithChildren) {
  const { t } = useDesktopI18n()

  return (
    <div className="login-shell">
      <div className="login-panel">
        <div className="eyebrow">{t('login.desktopAuth')}</div>
        <h1>{t('login.heading')}</h1>
        {children}
      </div>
    </div>
  )
}

export function Panel({
  title,
  subtitle,
  actions,
  children
}: PropsWithChildren<{ title: string; subtitle?: string; actions?: ReactNode }>) {
  return (
    <section className="panel">
      <div className="panel-head">
        <div>
          <h2>{title}</h2>
          {subtitle ? <p>{subtitle}</p> : null}
        </div>
        {actions ? <div className="panel-actions">{actions}</div> : null}
      </div>
      {children}
    </section>
  )
}

export function Metric({
  label,
  value,
  detail,
  compact = false
}: {
  label: string
  value: string | number
  detail?: string
  compact?: boolean
}) {
  return (
    <div className={`metric-card${compact ? ' metric-card--compact-value' : ''}`}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
      {detail ? <div className="metric-detail">{detail}</div> : null}
    </div>
  )
}

type DesktopSelectOption<T extends string | number> = {
  value: T
  label: ReactNode
}

export function DesktopSelect<T extends string | number>({
  value,
  options,
  onChange,
  placeholder
}: {
  value: T | ''
  options: ReadonlyArray<DesktopSelectOption<T>>
  onChange: (value: T | '') => void
  placeholder?: string
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    function handleOutsideClick(event: MouseEvent) {
      if (!ref.current || ref.current.contains(event.target as Node)) return
      setOpen(false)
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleOutsideClick)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [])

  const selected = useMemo(
    () => options.find((option) => option.value === value) ?? null,
    [options, value]
  )

  return (
    <div ref={ref} className="desktop-select">
      <button
        type="button"
        className={`desktop-select-trigger${open ? ' desktop-select-trigger--open' : ''}`}
        onClick={() => setOpen((current) => !current)}
      >
        <span className="desktop-select-value">{selected?.label ?? placeholder ?? ''}</span>
        <span className="desktop-select-caret" aria-hidden="true">▾</span>
      </button>
      {open ? (
        <div className="desktop-select-menu">
          {options.map((option) => {
            const selectedOption = option.value === value
            return (
              <button
                key={String(option.value)}
                type="button"
                className={`desktop-select-option${selectedOption ? ' desktop-select-option--selected' : ''}`}
                onClick={() => {
                  onChange(option.value)
                  setOpen(false)
                }}
              >
                {option.label}
              </button>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}

export function Tag({ children, tone = 'neutral' }: PropsWithChildren<{ tone?: 'neutral' | 'success' | 'warning' | 'danger' }>) {
  return <span className={`tag tag--${tone}`}>{children}</span>
}

export function EmptyState({ title, body = '' }: { title: string; body?: string }) {
  return (
    <div className="empty-state">
      <div className="empty-title">{title}</div>
      {body ? <div className="empty-body">{body}</div> : null}
    </div>
  )
}

export function actionButtonLabel(label: string) {
  return <span>{label}</span>
}

function runModeTone(mode: string): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (mode) {
    case 'local':
      return 'warning'
    case 'hybrid':
      return 'success'
    default:
      return 'neutral'
  }
}

function connectionTone(state: string): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (state) {
    case 'connected':
      return 'success'
    case 'degraded':
      return 'warning'
    case 'disconnected':
      return 'danger'
    case 'local':
      return 'warning'
    case 'hybrid':
      return 'success'
    default:
      return 'neutral'
  }
}

export function translateConnectionState(state: string, t: (key: string) => string) {
  switch (state) {
    case 'connected':
      return t('status.connected')
    case 'degraded':
      return t('status.degraded')
    case 'disconnected':
      return t('status.disconnected')
    case 'connecting':
      return t('status.connecting')
    case 'local':
      return t('status.local')
    case 'hybrid':
      return t('status.hybrid')
    default:
      return state
  }
}

function maskSensitiveText(value: string) {
  return value.replace(/[A-Za-z0-9]/g, '*')
}

function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M1.5 12s3.8-6 10.5-6 10.5 6 10.5 6-3.8 6-10.5 6S1.5 12 1.5 12Zm10.5 3.5A3.5 3.5 0 1 0 12 8.5a3.5 3.5 0 0 0 0 7Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="12" r="1.5" fill="currentColor" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M3 3 21 21M10.6 6.2A12.6 12.6 0 0 1 12 6c6.7 0 10.5 6 10.5 6a18.7 18.7 0 0 1-4.3 4.5M6.7 6.8A18.7 18.7 0 0 0 1.5 12s3.8 6 10.5 6c1.2 0 2.4-.2 3.5-.6M9.9 9.9A3 3 0 0 0 9 12a3 3 0 0 0 4.9 2.3"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
