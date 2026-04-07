import { useCallback, useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import { useRealtimeFeed } from '../realtime'
import type { DevicesResponse, SystemInfoState } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, ErrorBanner, LoadingCard, MetricCard, PageShell, RelayBadge, SurfaceCard } from '../template'

export function OverviewPage() {
  const { t } = useI18n()
  const [systemInfo, setSystemInfo] = useState<SystemInfoState | null>(null)
  const [devices, setDevices] = useState<DevicesResponse | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const { connected, lastEvent } = useRealtimeFeed()

  const load = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      const [info, deviceList] = await Promise.all([apiClient.getSystemInfo(), apiClient.getDevices()])
      setSystemInfo(info)
      setDevices(deviceList)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  useEffect(() => {
    if (!lastEvent) return
    if (['device.registered', 'device.updated', 'device.heartbeat', 'device.revoked', 'config.updated', 'records.ingested'].includes(lastEvent.type)) {
      void load()
    }
  }, [lastEvent, load])

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'overview' })
          void load()
        }}
      >
        {t('overview.refresh')}
      </ActionButton>
    </div>
  )

  if (loading && (!systemInfo || !devices) && !error) {
    return (
      <PageShell title={t('overview.title')} description={t('overview.description')} badge={t('overview.title')} actions={actions}>
        <LoadingCard title={t('overview.loadingTitle')} message={t('overview.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('overview.title')} description={t('overview.remoteDescription')} badge={t('overview.title')} actions={actions}>
      <ErrorBanner message={error} />
      {systemInfo && devices && (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard title={t('overview.metric.service')} value={systemInfo.service} tone="info" helper={systemInfo.appEnv} />
            <MetricCard title={t('overview.metric.users')} value={systemInfo.userCount} helper={t('overview.metric.usersHelper')} />
            <MetricCard title={t('overview.metric.devicesRemote')} value={devices.devices.length} tone="success" helper={t('overview.metric.devicesRemoteHelper')} />
            <MetricCard title={t('overview.metric.database')} value={systemInfo.databaseReady ? t('common.ready') : t('common.offline')} tone={systemInfo.databaseReady ? 'success' : 'warning'} helper={new Date(systemInfo.time).toLocaleString()} />
          </div>

          <SurfaceCard title={t('overview.endpointsTitle')} subtitle={t('overview.endpointsSubtitle')}>
            <div className="space-y-3 text-sm text-[#465533]">
              <div><span className="font-medium text-[#243115]">{t('overview.endpoint.local')}:</span> {systemInfo.localBaseUrl}</div>
              <div><span className="font-medium text-[#243115]">{t('overview.endpoint.public')}:</span> {systemInfo.publicBaseUrl || t('overview.endpoint.publicEmpty')}</div>
            </div>
          </SurfaceCard>
        </>
      )}
    </PageShell>
  )
}
