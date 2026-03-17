import { Alert, Badge, Card, Spinner } from 'flowbite-react'
import type { ReactNode } from 'react'

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

export function PageShell({ title, description, badge, actions, children }: PageShellProps) {
  return (
    <div className="space-y-6">
      <section className="rounded-3xl border border-slate-200/70 bg-[linear-gradient(135deg,rgba(255,255,255,0.98),rgba(240,249,255,0.92))] p-6 shadow-sm shadow-slate-200/60">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-3">
            {badge && (
              <Badge color="info" className="w-fit">
                {badge}
              </Badge>
            )}
            <div>
              <h1 className="text-2xl font-semibold tracking-tight text-slate-950">{title}</h1>
              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">{description}</p>
            </div>
          </div>
          {actions}
        </div>
      </section>
      {children}
    </div>
  )
}

export function ErrorBanner({ message }: { message: string }) {
  if (!message) return null
  return (
    <Alert color="failure" className="border border-rose-200/70">
      <span className="font-medium">请求失败：</span>
      {message}
    </Alert>
  )
}

export function LoadingCard({ title = '正在加载', message = '正在同步最新数据，请稍候。' }: { title?: string; message?: string }) {
  return (
    <Card className="rounded-3xl border border-slate-200/70 bg-white/95 shadow-sm">
      <div className="flex items-center gap-4 py-6">
        <Spinner color="info" size="lg" />
        <div>
          <p className="text-base font-semibold text-slate-900">{title}</p>
          <p className="mt-1 text-sm text-slate-500">{message}</p>
        </div>
      </div>
    </Card>
  )
}

export function EmptyCard({ title, message }: { title: string; message: string }) {
  return (
    <Card className="rounded-3xl border border-dashed border-slate-300 bg-white/80 shadow-none">
      <div className="py-8 text-center">
        <p className="text-base font-semibold text-slate-800">{title}</p>
        <p className="mt-2 text-sm text-slate-500">{message}</p>
      </div>
    </Card>
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
    <Card className={cx('rounded-3xl border border-slate-200/70 bg-white/95 shadow-sm shadow-slate-200/50', className)}>
      {(title || subtitle) && (
        <div className="mb-4">
          {title && <h2 className="text-lg font-semibold text-slate-950">{title}</h2>}
          {subtitle && <p className="mt-1 text-sm leading-6 text-slate-500">{subtitle}</p>}
        </div>
      )}
      {children}
    </Card>
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
  const accentClass =
    tone === 'success'
      ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
      : tone === 'info'
        ? 'bg-cyan-50 text-cyan-700 ring-cyan-200'
        : tone === 'warning'
          ? 'bg-amber-50 text-amber-700 ring-amber-200'
          : 'bg-slate-100 text-slate-700 ring-slate-200'

  return (
    <Card className="rounded-3xl border border-slate-200/70 bg-white/95 shadow-sm shadow-slate-200/50">
      <div className="space-y-3">
        <Badge className={cx('w-fit ring-1', accentClass)}>{title}</Badge>
        <div className="text-3xl font-semibold tracking-tight text-slate-950">{value}</div>
        {helper && <p className="text-sm text-slate-500">{helper}</p>}
      </div>
    </Card>
  )
}

export { cx }
