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
  return (
    <button
      type={type}
      className={cx(
        'relay-action-button inline-flex items-center justify-center disabled:cursor-not-allowed disabled:opacity-60',
        `relay-action-${tone}`,
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
        'relay-spinner inline-block h-5 w-5 animate-spin rounded-full border-2',
        className
      )}
    />
  )
}

export function PageShell({ title, description, badge, actions, children }: PageShellProps) {
  return (
    <div className="relay-page-shell">
      <section className="relay-page-header">
        <div className="min-w-0">
          {badge && <div className="relay-page-badge">{badge}</div>}
          <h1 className="relay-page-title">{title}</h1>
          <p className="relay-page-description">{description}</p>
        </div>
        {actions ? <div className="relay-page-actions">{actions}</div> : null}
      </section>
      {children}
    </div>
  )
}

export function ErrorBanner({ message }: { message: string }) {
  const { t } = useI18n()
  if (!message) return null
  return (
    <div className="relay-error-banner">
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
    <div className="relay-loading-card px-5 py-5">
      <div className="flex items-center gap-4">
        <div className="relay-loading-mark flex h-10 w-10 items-center justify-center rounded-full">
          <RelaySpinner className="h-6 w-6" />
        </div>
        <div>
          <p className="font-semibold text-[var(--relay-text)]">{title ?? t('common.loading')}</p>
          <p className="mt-1 text-sm leading-6 text-[var(--relay-muted)]">{message ?? t('common.loadingMessage')}</p>
        </div>
      </div>
    </div>
  )
}

export function EmptyCard({ title, message }: { title: string; message: string }) {
  return (
    <div className="relay-empty-card px-5 py-8 text-center">
      <p className="font-semibold text-[var(--relay-text)]">{title}</p>
      <p className="mt-2 text-sm leading-6 text-[var(--relay-muted)]">{message}</p>
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
        'relay-surface-card',
        className
      )}
    >
      {(title || subtitle) && (
        <div className="mb-4">
          {title && <h2 className="relay-surface-title">{title}</h2>}
          {subtitle && <p className="relay-surface-subtitle">{subtitle}</p>}
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
  return (
    <div className="relay-metric-card px-4 py-4">
      <div className="space-y-4">
        <div className={cx('relay-metric-label inline-flex rounded px-2 py-1 text-xs font-semibold uppercase', `relay-metric-${tone}`)}>
          {title}
        </div>
        <div className="text-[1.75rem] font-semibold text-[var(--relay-text)]">{value}</div>
        {helper && <p className="text-sm leading-6 text-[var(--relay-muted)]">{helper}</p>}
      </div>
    </div>
  )
}

export function RelayBadge({ tone = 'muted', className, children }: RelayBadgeProps) {
  return (
    <span
      className={cx(
        'relay-badge inline-flex items-center px-2 py-1 text-xs font-medium leading-none',
        `relay-badge-${tone}`,
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
        'relay-switch relative inline-flex h-8 w-12 shrink-0 items-center rounded-full border transition duration-150 disabled:cursor-not-allowed disabled:opacity-60',
        checked ? 'relay-switch-checked' : 'relay-switch-unchecked',
        className
      )}
    >
      <span
        className={cx(
          'relay-switch-thumb inline-block h-6 w-6 rounded-full transition-transform duration-150',
          checked ? 'translate-x-[1.25rem]' : 'translate-x-0.5'
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
          'relay-select-trigger flex w-full items-center justify-between gap-3 px-3 py-2.5 text-left text-sm'
        )}
      >
        <span className="min-w-0 truncate font-medium">{selected?.label}</span>
        <span
          className={cx(
            'relay-select-arrow flex h-6 w-6 shrink-0 items-center justify-center rounded transition',
            open && 'rotate-180'
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
                'relay-select-menu fixed z-[1200] overflow-hidden p-1',
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
                        'relay-select-option flex w-full items-center justify-between gap-3 px-3 py-2.5 text-left transition',
                        active && 'relay-select-option-active'
                      )}
                    >
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium">{option.label}</span>
                        {option.description ? (
                          <span className="relay-select-description mt-1 block text-xs">
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
