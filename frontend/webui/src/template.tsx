import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type ReactNode
} from 'react'
import { createPortal } from 'react-dom'
import { useI18n } from './i18n'

function cx(...values: Array<string | false | null | undefined>): string {
  return values.filter(Boolean).join(' ')
}

type PageShellProps = {
  title: string
  description: string
  badge?: string
  actions?: ReactNode
  children: ReactNode
}

type ActionButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  tone?: 'primary' | 'neutral' | 'warning' | 'danger'
}

type RelayBadgeProps = {
  tone?: 'accent' | 'muted' | 'success' | 'warning' | 'danger'
  className?: string
  children: ReactNode
}

type RelaySwitchProps = {
  checked: boolean
  onChange: (value: boolean) => void
  disabled?: boolean
  className?: string
}

type RelaySelectOption<T extends string | number> = {
  value: T
  label: ReactNode
  description?: ReactNode
}

type RelaySelectProps<T extends string | number> = {
  value: T
  options: ReadonlyArray<RelaySelectOption<T>>
  onChange: (value: T) => void
  className?: string
  menuClassName?: string
}

export function ActionButton({
  tone = 'neutral',
  className,
  type = 'button',
  children,
  ...props
}: ActionButtonProps) {
  const toneClass =
    tone === 'primary'
      ? 'bg-[linear-gradient(135deg,#bce620,#99bf1f)] text-[#263215] shadow-[0_18px_42px_-24px_rgba(135,171,22,0.68)] hover:brightness-[1.02]'
      : tone === 'warning'
        ? 'bg-[#fff8ea] text-[#8f5d12] ring-1 ring-[#e8c873] hover:bg-[#fff2da]'
      : tone === 'danger'
        ? 'bg-[#fffaf0] text-[#8a5318] ring-1 ring-[#e6c36f] hover:bg-[#fff3df]'
        : 'bg-[#f8fbe9] text-[#34461b] ring-1 ring-[#d2e09d] hover:bg-[#ffffff]'

  return (
    <button
      type={type}
      className={cx(
        'inline-flex items-center justify-center rounded-full px-4 py-2.5 text-sm font-medium transition duration-200 disabled:cursor-not-allowed disabled:opacity-60',
        toneClass,
        className
      )}
      {...props}
    >
      {children}
    </button>
  )
}

export function RelaySpinner({ className }: { className?: string }) {
  return (
    <span
      className={cx(
        'inline-block h-5 w-5 animate-spin rounded-full border-2 border-[#d5e79b] border-t-[#6f8e18]',
        className
      )}
    />
  )
}

export function PageShell({ title, description, badge, actions, children }: PageShellProps) {
  return (
    <div className="space-y-6">
      <section className="overflow-hidden rounded-[32px] border border-[rgba(126,153,45,0.18)] bg-[linear-gradient(135deg,rgba(248,252,232,0.98),rgba(232,244,188,0.96))] px-6 py-6 shadow-[0_22px_60px_-34px_rgba(98,122,28,0.24)] md:px-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-3xl">
            {badge && (
              <div className="mb-4 inline-flex items-center rounded-full bg-[#f4fbe0] px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.24em] text-[#58711e] ring-1 ring-[#d7e6a6]">
                {badge}
              </div>
            )}
            <h1 className="text-[2rem] font-semibold tracking-[-0.04em] text-[#243115] md:text-[2.4rem]">{title}</h1>
            <p className="mt-3 text-sm leading-7 text-[#647254] md:text-[15px]">{description}</p>
          </div>
          {actions ? (
            <div className="flex w-full flex-wrap items-center gap-3 md:w-auto md:shrink-0 md:justify-end">
              {actions}
            </div>
          ) : null}
        </div>
      </section>
      {children}
    </div>
  )
}

export function ErrorBanner({ message }: { message: string }) {
  const { t } = useI18n()
  if (!message) return null
  return (
    <div className="rounded-[28px] border border-[#f7c9bf] bg-[#fff8f3] px-5 py-4 text-sm leading-6 text-[#b24a24] shadow-[0_18px_50px_-34px_rgba(178,74,36,0.24)]">
      <span className="font-semibold">{t('common.requestFailed')}</span>
      {message}
    </div>
  )
}

export function LoadingCard({
  title,
  message
}: {
  title?: string
  message?: string
}) {
  const { t } = useI18n()
  return (
    <div className="rounded-[30px] border border-[rgba(126,153,45,0.14)] bg-white/92 px-6 py-7 shadow-[0_18px_60px_-38px_rgba(98,122,28,0.22)]">
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#eef8c8] text-[#6a861f]">
          <RelaySpinner className="h-6 w-6" />
        </div>
        <div>
          <p className="text-base font-semibold text-[#243115]">{title ?? t('common.loading')}</p>
          <p className="mt-1 text-sm leading-6 text-[#6c785d]">{message ?? t('common.loadingMessage')}</p>
        </div>
      </div>
    </div>
  )
}

export function EmptyCard({ title, message }: { title: string; message: string }) {
  return (
    <div className="rounded-[30px] border border-dashed border-[#d7e6a5] bg-[rgba(252,255,245,0.84)] px-6 py-10 text-center">
      <p className="text-base font-semibold text-[#34461b]">{title}</p>
      <p className="mt-2 text-sm leading-6 text-[#6c785d]">{message}</p>
    </div>
  )
}

export function SurfaceCard({
  title,
  subtitle,
  children,
  className
}: {
  title?: string
  subtitle?: string
  children: ReactNode
  className?: string
}) {
  return (
    <section
      className={cx(
        'rounded-[30px] border border-[rgba(126,153,45,0.14)] bg-[rgba(255,255,255,0.94)] px-6 py-6 shadow-[0_18px_60px_-38px_rgba(98,122,28,0.18)]',
        className
      )}
    >
      {(title || subtitle) && (
        <div className="mb-5">
          {title && <h2 className="text-xl font-semibold tracking-[-0.03em] text-[#243115]">{title}</h2>}
          {subtitle && <p className="mt-2 text-sm leading-6 text-[#6c785d]">{subtitle}</p>}
        </div>
      )}
      {children}
    </section>
  )
}

export function MetricCard({
  title,
  value,
  tone = 'default',
  helper
}: {
  title: string
  value: string | number
  tone?: 'default' | 'success' | 'info' | 'warning'
  helper?: string
}) {
  const toneClass =
    tone === 'success'
      ? 'bg-[#ebf9c9] text-[#58711e]'
      : tone === 'info'
        ? 'bg-[#f2f8d5] text-[#6a861f]'
        : tone === 'warning'
          ? 'bg-[#fff7de] text-[#9a6412]'
          : 'bg-[#f4f8e8] text-[#4f5d31]'

  return (
    <div className="rounded-[28px] border border-[rgba(126,153,45,0.14)] bg-white/94 px-5 py-5 shadow-[0_18px_60px_-40px_rgba(98,122,28,0.16)]">
      <div className="space-y-4">
        <div className={cx('inline-flex rounded-full px-3 py-1 text-xs font-semibold tracking-[0.16em] uppercase', toneClass)}>
          {title}
        </div>
        <div className="text-[2rem] font-semibold tracking-[-0.05em] text-[#243115] md:text-[2.3rem]">{value}</div>
        {helper && <p className="text-sm leading-6 text-[#6c785d]">{helper}</p>}
      </div>
    </div>
  )
}

export function RelayBadge({ tone = 'muted', className, children }: RelayBadgeProps) {
  const toneClass =
    tone === 'accent'
      ? 'bg-[#d8f0a5] text-[#476018]'
      : tone === 'success'
        ? 'bg-[#dff4b8] text-[#4b6517]'
        : tone === 'warning'
          ? 'bg-[#fff0d9] text-[#8a5518]'
          : tone === 'danger'
            ? 'bg-[#fde5d9] text-[#9f4e22]'
            : 'bg-[#eef4df] text-[#566347]'

  return (
    <span
      className={cx(
        'inline-flex items-center rounded-xl px-3 py-1.5 text-sm font-medium leading-none',
        toneClass,
        className
      )}
    >
      {children}
    </span>
  )
}

export function RelaySwitch({ checked, onChange, disabled = false, className }: RelaySwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cx(
        'relative inline-flex h-10 w-[3.65rem] shrink-0 items-center rounded-full border transition duration-200 focus:outline-none focus-visible:ring-4 focus-visible:ring-[#dceaa4] disabled:cursor-not-allowed disabled:opacity-60',
        checked
          ? 'border-[#91b725] bg-[linear-gradient(135deg,#9bc41f,#7a9d1b)] shadow-[0_14px_28px_-20px_rgba(122,157,27,0.66)]'
          : 'border-[#cbd8a4] bg-[#dfe8c4]',
        className
      )}
    >
      <span
        className={cx(
          'inline-block h-8 w-8 rounded-full bg-white shadow-[0_10px_20px_-14px_rgba(52,70,27,0.54)] transition-transform duration-200',
          checked ? 'translate-x-[1.35rem]' : 'translate-x-1'
        )}
      />
    </button>
  )
}

export function RelaySelect<T extends string | number>({
  value,
  options,
  onChange,
  className,
  menuClassName
}: RelaySelectProps<T>) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const triggerRef = useRef<HTMLButtonElement | null>(null)
  const menuRef = useRef<HTMLDivElement | null>(null)
  const [menuStyle, setMenuStyle] = useState<{ top: number; left: number; width: number }>({
    top: 0,
    left: 0,
    width: 0
  })

  useEffect(() => {
    if (!open) return
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node
      if (!rootRef.current?.contains(target) && !menuRef.current?.contains(target)) {
        setOpen(false)
      }
    }
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    window.addEventListener('pointerdown', handlePointerDown)
    window.addEventListener('keydown', handleEscape)
    return () => {
      window.removeEventListener('pointerdown', handlePointerDown)
      window.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  useLayoutEffect(() => {
    if (!open) return

    const updateMenuPosition = () => {
      const rect = triggerRef.current?.getBoundingClientRect()
      if (!rect) return
      setMenuStyle({
        top: rect.bottom + 10,
        left: rect.left,
        width: Math.max(rect.width, 224)
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

  const selected = useMemo(
    () => options.find((option) => String(option.value) === String(value)) ?? options[0],
    [options, value]
  )

  return (
    <div ref={rootRef} className={cx('relative', className)}>
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className={cx(
          'flex w-full items-center justify-between gap-3 rounded-[24px] border border-[rgba(136,166,64,0.34)] bg-[linear-gradient(180deg,rgba(255,255,255,0.98),rgba(247,251,232,0.94))] px-4 py-3.5 text-left text-sm text-[#243115] shadow-[inset_0_1px_0_rgba(255,255,255,0.94),0_14px_36px_-28px_rgba(98,122,28,0.2)] transition hover:border-[#a9c84d] hover:bg-white focus:outline-none focus-visible:ring-4 focus-visible:ring-[#dceaa4]'
        )}
      >
        <span className="min-w-0 truncate font-medium">{selected?.label}</span>
        <span
          className={cx(
            'flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#eef8cf] text-[#6f8e18] transition',
            open && 'rotate-180 bg-[#dff4b8]'
          )}
        >
          ▼
        </span>
      </button>

      {open
        ? createPortal(
            <div
              ref={menuRef}
              className={cx(
                'fixed z-[1200] overflow-hidden rounded-[26px] border border-[#d8e6ae] bg-[linear-gradient(180deg,rgba(255,255,249,0.99),rgba(245,250,229,0.97))] p-2 shadow-[0_24px_64px_-34px_rgba(67,86,20,0.34)] backdrop-blur-xl',
                menuClassName
              )}
              style={{
                top: menuStyle.top,
                left: menuStyle.left,
                width: menuStyle.width,
                maxWidth: 'calc(100vw - 1rem)',
                maxHeight: 'min(22rem, calc(100vh - 2rem))'
              }}
            >
              <div className="space-y-1 overflow-y-auto overscroll-contain">
                {options.map((option) => {
                  const active = String(option.value) === String(value)
                  return (
                    <button
                      key={String(option.value)}
                      type="button"
                      role="option"
                      aria-selected={active}
                      onClick={() => {
                        setOpen(false)
                        onChange(option.value)
                      }}
                      className={cx(
                        'flex w-full items-center justify-between gap-3 rounded-[18px] px-4 py-3 text-left transition',
                        active
                          ? 'bg-[linear-gradient(135deg,#87ad1e,#6f8e18)] text-[#1f2a10] shadow-[0_18px_32px_-24px_rgba(111,142,24,0.48)]'
                          : 'text-[#42512a] hover:bg-white/90'
                      )}
                    >
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium">{option.label}</span>
                        {option.description ? (
                          <span className={cx('mt-1 block text-xs', active ? 'text-[#304016]' : 'text-[#71805d]')}>
                            {option.description}
                          </span>
                        ) : null}
                      </span>
                      {active ? <span className="text-sm">✓</span> : null}
                    </button>
                  )
                })}
              </div>
            </div>,
            document.body
          )
        : null}
    </div>
  )
}

export function ToggleRow({
  label,
  hint,
  checked,
  onChange,
  className
}: {
  label: string
  hint?: string
  checked: boolean
  onChange: (value: boolean) => void
  className?: string
}) {
  return (
    <div
      className={cx(
        'flex items-center justify-between gap-4 rounded-[24px] border border-[#d9e6b1] bg-[linear-gradient(180deg,#ffffff_0%,#f7fbe9_100%)] px-4 py-3.5 shadow-[0_12px_30px_-24px_rgba(98,122,28,0.2)]',
        className
      )}
    >
      <div>
        <div className="text-sm font-medium text-[#243115]">{label}</div>
        {hint && <div className="mt-1 text-xs leading-5 text-[#70805d]">{hint}</div>}
      </div>
      <RelaySwitch checked={checked} onChange={onChange} />
    </div>
  )
}

export { cx }
