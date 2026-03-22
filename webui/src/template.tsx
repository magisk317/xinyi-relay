import type { ButtonHTMLAttributes, ReactNode } from 'react'

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
  tone?: 'primary' | 'neutral' | 'danger'
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
          {actions && <div className="flex shrink-0 items-center gap-3">{actions}</div>}
        </div>
      </section>
      {children}
    </div>
  )
}

export function ErrorBanner({ message }: { message: string }) {
  if (!message) return null
  return (
    <div className="rounded-[28px] border border-[#f7c9bf] bg-[#fff8f3] px-5 py-4 text-sm leading-6 text-[#b24a24] shadow-[0_18px_50px_-34px_rgba(178,74,36,0.24)]">
      <span className="font-semibold">请求失败：</span>
      {message}
    </div>
  )
}

export function LoadingCard({
  title = '正在加载',
  message = '正在同步最新数据，请稍候。'
}: {
  title?: string
  message?: string
}) {
  return (
    <div className="rounded-[30px] border border-[rgba(126,153,45,0.14)] bg-white/92 px-6 py-7 shadow-[0_18px_60px_-38px_rgba(98,122,28,0.22)]">
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#eef8c8] text-[#6a861f]">
          <RelaySpinner className="h-6 w-6" />
        </div>
        <div>
          <p className="text-base font-semibold text-[#243115]">{title}</p>
          <p className="mt-1 text-sm leading-6 text-[#6c785d]">{message}</p>
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
