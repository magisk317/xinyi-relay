import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { ClipboardListIcon, HomeIcon, StarIcon } from 'flowbite-react/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './auth'
import { useI18n } from './i18n'
import { cx } from './template'

export function AppLayout() {
  const { t, selectedLocale, setSelectedLocale } = useI18n()
  const { username, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const navGroups = [
    {
      label: t('layout.nav.group.overview'),
      items: [
        { to: '/overview', label: t('layout.nav.overview'), icon: HomeIcon },
        { to: '/analytics', label: t('layout.nav.analytics'), icon: StarIcon }
      ]
    },
    {
      label: t('layout.nav.group.automation'),
      items: [
        { to: '/apps', label: t('layout.nav.apps'), icon: ClipboardListIcon },
        { to: '/senders', label: t('layout.nav.senders'), icon: StarIcon },
        { to: '/scheduled-tasks', label: t('layout.nav.scheduled_tasks'), icon: ClipboardListIcon }
      ]
    },
    {
      label: t('layout.nav.group.records'),
      items: [{ to: '/records', label: t('layout.nav.records'), icon: ClipboardListIcon }]
    },
    {
      label: t('layout.nav.group.devices'),
      items: [
        { to: '/advanced', label: t('layout.nav.devices'), icon: ClipboardListIcon },
        { to: '/device-control', label: t('layout.nav.deviceControl'), icon: ClipboardListIcon }
      ]
    },
    {
      label: t('layout.nav.group.settings'),
      items: [{ to: '/settings', label: t('layout.nav.settings'), icon: HomeIcon }]
    }
  ]
  const navItems = navGroups.flatMap((group) => group.items)

  const renderNavTabs = (mobile = false) => (
    <>
      {navItems.map((item) => {
        const active = location.pathname === item.to
        return (
          <button
            key={item.to}
            type="button"
            onClick={() => navigate(item.to)}
            className={cx(
              mobile ? 'relay-mobile-nav-item' : 'relay-nav-item flex w-full items-center gap-2.5 px-3 py-2.5 text-left text-sm transition',
              active && 'relay-nav-item-active'
            )}
          >
            {!mobile && (() => {
              const Icon = item.icon
              return (
                <span
                  className={cx(
                    'flex h-5 w-5 shrink-0 items-center justify-center',
                    active ? 'text-[var(--relay-accent-strong)]' : 'text-[var(--relay-muted)]'
                  )}
                >
                  <Icon className="h-4 w-4" aria-hidden="true" />
                </span>
              )
            })()}
            <span className="font-medium">{item.label}</span>
          </button>
        )
      })}
    </>
  )

  const renderSidebar = () => (
    <nav aria-label={t('layout.workspace')} className="space-y-2">
      {navGroups.map((group) => (
        <section key={group.label}>
          <h2 className="relay-nav-group-title">{group.label}</h2>
          <div className="space-y-0.5">
            {group.items.map((item) => {
              const Icon = item.icon
              const active = location.pathname === item.to
              return (
                <button
                  key={item.to}
                  type="button"
                  aria-current={active ? 'page' : undefined}
                  onClick={() => navigate(item.to)}
                  className={cx(
                    'relay-nav-item flex w-full items-center gap-2.5 px-3 py-2.5 text-left text-sm transition',
                    active && 'relay-nav-item-active'
                  )}
                >
                  <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                  <span className="font-medium">{item.label}</span>
                </button>
              )
            })}
          </div>
        </section>
      ))}
    </nav>
  )

  useLayoutEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' })
  }, [location.pathname])

  return (
    <div className="relay-shell">
      <header className="relay-topbar fixed inset-x-0 top-0 z-40 hidden lg:block">
        <div className="mx-auto flex max-w-[1600px] items-center justify-between gap-4 px-4 py-3 lg:px-8">
          <button
            type="button"
            className="flex items-center gap-3 text-left"
            onClick={() => navigate('/overview')}
          >
            <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-md border border-[var(--relay-border)] bg-[var(--relay-surface)]">
              <img src="/app-logo.png" alt="Xinyi Relay" className="h-full w-full object-contain p-1" />
            </div>
            <div>
              <div className="text-base font-semibold text-[var(--relay-text)]">Xinyi Relay</div>
              <div className="text-xs text-[var(--relay-muted)]">Web console</div>
            </div>
          </button>

          <AccountMenu
            username={username}
            onLogout={() => void logout()}
            selectedLocale={selectedLocale}
            onChangeLocale={setSelectedLocale}
          />
        </div>
      </header>

      <div className="mx-auto max-w-[1600px] px-4 pb-8 pt-4 lg:px-8 lg:pt-20">
        <section className="relay-mobile-brand mb-4 flex items-center justify-between gap-4 rounded-lg px-4 py-3 lg:hidden">
            <button
              type="button"
              className="flex min-w-0 items-center gap-3 text-left"
              onClick={() => navigate('/overview')}
            >
              <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-md border border-[var(--relay-border)] bg-[var(--relay-surface)]">
                <img src="/app-logo.png" alt="Xinyi Relay" className="h-full w-full object-contain p-1" />
              </div>
              <div className="min-w-0">
                <div className="truncate text-base font-semibold text-[var(--relay-text)]">Xinyi Relay</div>
              </div>
            </button>

            <AccountMenu
              username={username}
              onLogout={() => void logout()}
              selectedLocale={selectedLocale}
              onChangeLocale={setSelectedLocale}
              compact
            />
        </section>

        <div className="mt-4 flex gap-6">
          <aside className="hidden w-[236px] shrink-0 lg:block">
            <div className="relay-sidebar sticky top-20 p-2">
              {renderSidebar()}
            </div>
          </aside>

          <main className="relay-main-shell min-w-0 flex-1">
            <div className="relay-mobile-nav sticky top-0 z-30 -mx-4 mb-4 flex gap-1 overflow-x-auto px-4 py-2 lg:hidden">
              {renderNavTabs(true)}
            </div>

            <Outlet />

            <footer className="relay-footer mt-8 rounded-md px-4 py-3 text-sm leading-6 text-[var(--relay-muted)]">
              <span className="font-medium text-[var(--relay-text)]">Xinyi Relay</span>
              <span className="mx-2 text-[var(--relay-border)]">/</span>
              {t('layout.footerHint')}
            </footer>
          </main>
        </div>
      </div>
    </div>
  )
}

function AccountMenu({
  username,
  onLogout,
  selectedLocale,
  onChangeLocale,
  compact = false
}: {
  username: string
  onLogout: () => void
  selectedLocale: 'system' | 'zh-CN' | 'zh-TW' | 'en'
  onChangeLocale: (next: 'system' | 'zh-CN' | 'zh-TW' | 'en') => void
  compact?: boolean
}) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const triggerRef = useRef<HTMLButtonElement | null>(null)
  const [menuStyle, setMenuStyle] = useState({ top: 0, left: 0, width: 0 })

  useEffect(() => {
    if (!open) return
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    window.addEventListener('pointerdown', handlePointerDown)
    return () => window.removeEventListener('pointerdown', handlePointerDown)
  }, [open])

  useLayoutEffect(() => {
    if (!open) return
    const updateMenuPosition = () => {
      const rect = triggerRef.current?.getBoundingClientRect()
      if (!rect) return
      setMenuStyle({
        top: rect.bottom + 10,
        left: rect.right - 224,
        width: 224
      })
    }
    updateMenuPosition()
    window.addEventListener('resize', updateMenuPosition)
    window.addEventListener('scroll', updateMenuPosition, true)
    return () => {
      window.removeEventListener('resize', updateMenuPosition)
      window.removeEventListener('scroll', updateMenuPosition, true)
    }
  }, [open])

  return (
    <div ref={rootRef} className="relative shrink-0">
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen((value) => !value)}
        className={cx(
          'relay-account-trigger inline-flex items-center gap-2 px-3 py-2 text-sm transition',
          compact ? 'px-3.5 py-2 text-sm' : 'px-4 py-2.5 text-sm'
        )}
      >
        <span className="max-w-[5.5rem] truncate">{username}</span>
        <span className={cx('text-[10px] transition', open && 'rotate-180')}>▼</span>
      </button>

      {open
        ? createPortal(
            <div
              className="relay-account-menu fixed z-[1300] p-2"
              style={{
                top: menuStyle.top,
                left: Math.max(8, menuStyle.left),
                width: Math.min(menuStyle.width, window.innerWidth - 16)
              }}
            >
              <div className="px-2 pb-2 pt-1">
                <div className="relay-menu-section-title">{t('layout.language')}</div>
                <div className="mt-2 grid gap-2">
                  {(['system', 'zh-CN', 'zh-TW', 'en'] as const).map((value) => (
                    <button
                      key={value}
                      type="button"
                      onClick={() => onChangeLocale(value)}
                      className={cx(
                        'relay-menu-choice flex w-full items-center justify-center px-3 py-2 text-sm font-medium whitespace-nowrap transition',
                        selectedLocale === value && 'relay-menu-choice-active'
                      )}
                    >
                      {t(`locale.${value}`)}
                    </button>
                  ))}
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  setOpen(false)
                  onLogout()
                }}
                className="relay-menu-logout flex w-full items-center justify-center px-4 py-2.5 text-center text-sm font-medium transition"
              >
                <span>{t('layout.logout')}</span>
              </button>
            </div>,
            document.body
          )
        : null}
    </div>
  )
}
