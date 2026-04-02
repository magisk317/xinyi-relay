import { useCallback, useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import type { OverviewState } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, ErrorBanner, LoadingCard, MetricCard, PageShell } from '../template'

export function OverviewPage() {
  const { t } = useI18n()
  const [data, setData] = useState<OverviewState | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setError('')
      setData(await apiClient.getOverview())
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    }
  }, [t])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  const actions = (
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'overview' })
        void load()
      }}
    >
      {t('overview.refresh')}
    </ActionButton>
  )

  if (!data && !error) {
    return (
      <PageShell
        title={t('overview.title')}
        description={t('overview.description')}
        badge="Dashboard"
        actions={actions}
      >
        <LoadingCard title={t('overview.loadingTitle')} message={t('overview.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell
      title={t('overview.title')}
      description={t('overview.description')}
      badge="Dashboard"
      actions={actions}
    >
      <ErrorBanner message={error} />
      {data && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard title={t('overview.metric.appCount')} value={data.appCount} helper={t('overview.metric.appCountHelper')} />
          <MetricCard title={t('overview.metric.blockedCount')} value={data.blockedCount} tone="warning" helper={t('overview.metric.blockedCountHelper')} />
          <MetricCard title={t('overview.metric.forwardingCount')} value={data.forwardingCount} tone="info" helper={t('overview.metric.forwardingCountHelper')} />
          <MetricCard title={t('overview.metric.recordCount')} value={data.recordCount} helper={t('overview.metric.recordCountHelper')} />
          <MetricCard title={t('overview.metric.senderTotal')} value={data.senderTotal} tone="info" helper={t('overview.metric.senderTotalHelper')} />
          <MetricCard title={t('overview.metric.senderEnabled')} value={data.senderEnabled} tone="success" helper={t('overview.metric.senderEnabledHelper')} />
          <MetricCard title={t('overview.metric.senderAppNotifyEnabled')} value={data.senderAppNotifyEnabled} helper={t('overview.metric.senderAppNotifyEnabledHelper')} />
          <MetricCard title={t('overview.metric.version')} value={data.version.localVersionName} tone="success" helper={t('overview.metric.versionHelper', { code: data.version.localVersionCode })} />
        </div>
      )}
    </PageShell>
  )
}
