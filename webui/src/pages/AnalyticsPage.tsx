import { useCallback, useEffect, useMemo, useState } from 'react'
import { apiClient } from '../api/client'
import type { AnalyticsResponse, AnalyticsWindow } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, ErrorBanner, LoadingCard, MetricCard, PageShell, SurfaceCard, cx } from '../template'

type RangeKey = 'allTime' | 'last7d' | 'last30d'

export function AnalyticsPage() {
  const { t } = useI18n()
  const [data, setData] = useState<AnalyticsResponse | null>(null)
  const [error, setError] = useState('')
  const [range, setRange] = useState<RangeKey>('allTime')

  const ranges = [
    { key: 'allTime', label: t('analytics.range.all') },
    { key: 'last7d', label: t('analytics.range.7d') },
    { key: 'last30d', label: t('analytics.range.30d') }
  ] as const

  const load = useCallback(async () => {
    try {
      setError('')
      setData(await apiClient.getAnalytics())
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    }
  }, [t])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  const window = useMemo<AnalyticsWindow | null>(() => {
    if (!data) return null
    return data[range]
  }, [data, range])

  const actions = (
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'analytics' })
        void load()
      }}
    >
      {t('analytics.refresh')}
    </ActionButton>
  )

  if (!data && !error) {
    return (
      <PageShell title={t('analytics.title')} description={t('analytics.description')} badge="Analytics" actions={actions}>
        <LoadingCard title={t('analytics.loadingTitle')} message={t('analytics.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('analytics.title')} description={t('analytics.description')} badge="Analytics" actions={actions}>
      <ErrorBanner message={error} />
      <SurfaceCard title={t('analytics.rangeTitle')} subtitle={t('analytics.rangeSubtitle')}>
        <div className="flex flex-wrap gap-2">
          {ranges.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setRange(item.key)}
              className={cx(
                'rounded-full px-4 py-2.5 text-sm font-medium transition',
                range === item.key
                  ? 'bg-[linear-gradient(135deg,#87ad1e,#6f8e18)] text-[#243115] shadow-[0_20px_36px_-24px_rgba(111,142,24,0.44)]'
                  : 'bg-[#f7fbe8] text-[#5d6c40] ring-1 ring-[#d4e3a1] hover:bg-white'
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
        <div className="mt-4 text-sm text-[#6c785d]">
          {t('analytics.range.switched', { label: ranges.find((item) => item.key === range)?.label ?? t('analytics.range.all') })}
        </div>
      </SurfaceCard>
      {window && (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
            <MetricCard title={t('analytics.metric.detected')} value={window.summary.smsCodeDetected} tone="info" />
            <MetricCard title={t('analytics.metric.attempt')} value={window.summary.autoInputAttempt} />
            <MetricCard title={t('analytics.metric.success')} value={window.summary.autoInputSuccess} tone="success" />
            <MetricCard title={t('analytics.metric.fail')} value={window.summary.autoInputFail} tone="warning" />
            <MetricCard title={t('analytics.metric.total')} value={window.summary.messageTotal} />
          </div>

          <SurfaceCard title={t('analytics.tableTitle')} subtitle={t('analytics.tableSubtitle')}>
            <div className="relay-table-shell">
              <table className="relay-table">
                <thead>
                  <tr>
                    <th>{t('analytics.table.type')}</th>
                    <th>{t('analytics.table.configured')}</th>
                    <th>{t('analytics.table.enabled')}</th>
                    <th>{t('analytics.table.sent')}</th>
                    <th>{t('analytics.table.success')}</th>
                    <th>{t('analytics.table.failed')}</th>
                  </tr>
                </thead>
                <tbody>
                  {window.senderStats.map((stat) => (
                    <tr key={stat.senderType}>
                      <td data-strong="true">{stat.senderTypeLabel}</td>
                      <td>{stat.configured}</td>
                      <td>{stat.enabled}</td>
                      <td>{stat.sent}</td>
                      <td>{stat.success}</td>
                      <td>{stat.failed}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </SurfaceCard>
        </>
      )}
    </PageShell>
  )
}
