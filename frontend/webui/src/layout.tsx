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
  const navItems = [
    { to: '/overview', label: t('layout.nav.overview'), icon: HomeIcon },
    { to: '/analytics', label: t('layout.nav.analytics'), icon: StarIcon },
    { to: '/apps', label: t('layout.nav.apps'), icon: ClipboardListIcon },
    { to: '/records', label: t('layout.nav.records'), icon: ClipboardListIcon },
    { to: '/senders', label: t('layout.nav.senders'), icon: StarIcon },
    { to: '/settings', label: t('layout.nav.settings'), icon: HomeIcon },
    { to: '/advanced', label: t('layout.nav.advanced'), icon: ClipboardListIcon }
  ] as const

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
              mobile
                ? 'shrink-0 rounded-full px-4 py-2.5 text-sm font-medium transition duration-200'
                : 'flex w-full items-center gap-3 rounded-[22px] px-4 py-3 text-left text-sm transition duration-200',
              active
                ? 'bg-[linear-gradient(135deg,#87ad1e,#6f8e18)] text-[#1f2a10] shadow-[0_22px_40px_-28px_rgba(111,142,24,0.54)]'
                : mobile
                  ? 'bg-white/84 text-[#596743] ring-1 ring-[#d4e3a1]'
                  : 'text-[#42512a] hover:bg-white/80'
            )}
          >
            {!mobile && (() => {
              const Icon = item.icon
              return (
                <span
                  className={cx(
                    'flex h-9 w-9 items-center justify-center rounded-full',
                    active ? 'bg-white/30 text-[#2c3818]' : 'bg-[#eff8cf] text-[#708b23]'
                  )}
                >
                  <Icon className="h-4 w-4" />
                </span>
              )
            })()}
            <span className="font-medium">{item.label}</span>
          </button>
        )
      })}
    </>
  )

  useLayoutEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' })
  }, [location.pathname])

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top,#fbfef2_0%,#f0f8d9_50%,#e7f1cb_100%)] text-[#243115]">
      <header className="fixed inset-x-0 top-0 z-40 hidden border-b border-[#89a240]/18 bg-[linear-gradient(135deg,rgba(135,173,30,0.96),rgba(111,142,24,0.94))] backdrop-blur-xl lg:block">
        <div className="mx-auto flex max-w-[1600px] items-center justify-between gap-4 px-4 py-4 lg:px-8">
          <button
            type="button"
            className="flex items-center gap-3 text-left"
            onClick={() => navigate('/overview')}
          >
            <div className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-[18px] bg-white/14 ring-1 ring-white/18">
              <img src="/app-logo.png" alt="Xinyi Relay" className="h-full w-full object-contain p-1.5" />
            </div>
            <div>
              <div className="text-lg font-semibold tracking-[-0.03em] text-[#f8ffe6]">Xinyi Relay</div>
              <div className="text-xs uppercase tracking-[0.24em] text-[#eef8cf]">WebUI</div>
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

      <div className="mx-auto max-w-[1600px] px-4 pb-8 pt-4 lg:px-8 lg:pt-28">
        <section className="overflow-hidden rounded-[30px] border border-[#89a240]/18 bg-[linear-gradient(135deg,rgba(135,173,30,0.96),rgba(111,142,24,0.94))] shadow-[0_24px_70px_-40px_rgba(67,86,20,0.34)] lg:hidden">
          <div className="flex items-center justify-between gap-4 px-5 py-5">
            <button
              type="button"
              className="flex min-w-0 items-center gap-3 text-left"
              onClick={() => navigate('/overview')}
            >
              <div className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-[18px] bg-white/14 ring-1 ring-white/18">
                <img src="/app-logo.png" alt="Xinyi Relay" className="h-full w-full object-contain p-1.5" />
              </div>
              <div className="min-w-0">
                <div className="truncate text-[1.55rem] font-semibold tracking-[-0.05em] text-[#f8ffe6]">Xinyi</div>
              </div>
            </button>

            <AccountMenu
              username={username}
              onLogout={() => void logout()}
              selectedLocale={selectedLocale}
              onChangeLocale={setSelectedLocale}
              compact
            />
          </div>
        </section>

        <div className="mt-4 flex gap-8">
          <aside className="hidden w-[260px] shrink-0 lg:block">
            <div className="sticky top-32 overflow-hidden rounded-[34px] border border-[#d5e79b]/50 bg-[linear-gradient(180deg,rgba(255,255,255,0.74),rgba(244,251,223,0.94))] p-4 shadow-[0_24px_70px_-40px_rgba(98,122,28,0.2)] backdrop-blur-xl">
              <nav className="space-y-1.5">
                {renderNavTabs()}
              </nav>
            </div>
          </aside>

          <main className="relay-main-shell min-w-0 flex-1">
            <div className="sticky top-0 z-30 -mx-4 mb-4 border-b border-[#d8e6ae]/70 bg-[linear-gradient(180deg,rgba(251,255,244,0.88),rgba(243,249,220,0.96))] px-4 pb-3 pt-4 shadow-[0_18px_42px_-34px_rgba(98,122,28,0.18)] backdrop-blur-xl lg:hidden">
              <div className="flex gap-2 overflow-x-auto py-1">{renderNavTabs(true)}</div>
            </div>

            <Outlet />

            <footer className="mt-8 rounded-[28px] border border-[#d5e79b]/42 bg-white/72 px-5 py-4 text-sm leading-6 text-[#6c785d] shadow-[0_18px_50px_-38px_rgba(98,122,28,0.18)]">
              <span className="font-medium text-[#34461b]">Relay WebUI</span>
              <span className="mx-2 text-[#b3c37d]">/</span>
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
          'inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/12 text-[#f6ffe0] shadow-[0_14px_28px_-24px_rgba(52,70,27,0.42)] transition hover:bg-white/18',
          compact ? 'px-3.5 py-2 text-sm' : 'px-4 py-2.5 text-sm'
        )}
      >
        <span className="max-w-[5.5rem] truncate">{username}</span>
        <span className={cx('text-[10px] transition', open && 'rotate-180')}>▼</span>
      </button>

      {open
        ? createPortal(
            <div
              className="fixed z-[1300] rounded-[22px] border border-[#d8e6ae] bg-[rgba(255,255,248,0.98)] p-2 shadow-[0_28px_80px_-42px_rgba(67,86,20,0.34)] backdrop-blur-xl"
              style={{
                top: menuStyle.top,
                left: Math.max(8, menuStyle.left),
                width: Math.min(menuStyle.width, window.innerWidth - 16)
              }}
            >
              <div className="px-3 pb-2 pt-1">
                <div className="text-[11px] font-semibold uppercase tracking-[0.2em] text-[#708b23]">{t('layout.language')}</div>
                <div className="mt-2 grid gap-2">
                  {(['system', 'zh-CN', 'zh-TW', 'en'] as const).map((value) => (
                    <button
                      key={value}
                      type="button"
                      onClick={() => onChangeLocale(value)}
                      className={cx(
                        'flex w-full items-center justify-center rounded-full px-3 py-2 text-sm font-medium whitespace-nowrap transition',
                        selectedLocale === value
                          ? 'bg-[linear-gradient(135deg,#87ad1e,#6f8e18)] text-[#243115]'
                          : 'bg-[#f4fbe0] text-[#51613a]'
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
                className="flex w-full items-center justify-center rounded-[18px] px-4 py-3 text-center text-sm font-medium text-[#415117] transition hover:bg-[#f4fbe0]"
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
