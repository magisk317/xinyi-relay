import { useCallback, useEffect, useEffectEvent, useMemo, useState } from 'react'
import { apiClient } from '../api/client'
import { useRealtimeFeed } from '../realtime'
import type { ConfigAuditLogItem, DeviceItem, RecordItem } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import {
  ActionButton,
  EmptyCard,
  ErrorBanner,
  LoadingCard,
  MetricCard,
  PageShell,
  RelayBadge,
  SurfaceCard
} from '../template'

export function AnalyticsPage() {
  const { t } = useI18n()
  const { connected, lastEvent } = useRealtimeFeed()
  const [records, setRecords] = useState<RecordItem[]>([])
  const [devices, setDevices] = useState<DeviceItem[]>([])
  const [auditLogs, setAuditLogs] = useState<ConfigAuditLogItem[]>([])
  const [currentRevision, setCurrentRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      const [recordsResp, devicesResp, auditResp, snapshot] = await Promise.all([
        apiClient.getRecords(200),
        apiClient.getDevices(),
        apiClient.getConfigAuditLogs(30),
        apiClient.getConfigSnapshot()
      ])
      setRecords(recordsResp.records)
      setDevices(devicesResp.devices)
      setAuditLogs(auditResp.logs)
      setCurrentRevision(snapshot.revision)
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

  const handleRealtimeEvent = useEffectEvent((eventType: string) => {
    if (['device.registered', 'device.updated', 'device.revoked', 'device.heartbeat', 'config.updated', 'records.ingested'].includes(eventType)) {
      void load()
    }
  })

  useEffect(() => {
    if (!lastEvent) return
    handleRealtimeEvent(lastEvent.type)
  }, [lastEvent])

  const stats = useMemo(() => {
    const smsCode = records.filter((record) => record.recordType === 'sms_code').length
    const smsPlain = records.filter((record) => record.recordType === 'sms_plain').length
    const appNotify = records.filter((record) => record.recordType === 'app_notify').length
    const callNotify = records.filter((record) => record.recordType === 'call').length
    const activeDevices = devices.filter((device) => device.lastSeenAt).length
    return {
      smsCode,
      smsPlain,
      appNotify,
      callNotify,
      activeDevices
    }
  }, [devices, records])

  const recentDeviceActivity = useMemo(
    () =>
      [...devices]
        .sort((left, right) => {
          const leftTime = left.lastSeenAt ? new Date(left.lastSeenAt).getTime() : 0
          const rightTime = right.lastSeenAt ? new Date(right.lastSeenAt).getTime() : 0
          return rightTime - leftTime
        })
        .slice(0, 8),
    [devices]
  )

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'analytics' })
          void load()
        }}
      >
        {t('analytics.refresh')}
      </ActionButton>
    </div>
  )

  if (loading && !records.length && !auditLogs.length && !error) {
    return (
      <PageShell title={t('analytics.title')} description={t('analytics.description')} badge={t('analytics.title')} actions={actions}>
        <LoadingCard title={t('analytics.loadingTitle')} message={t('analytics.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('analytics.title')} description={t('analytics.remoteDescription')} badge={t('analytics.title')} actions={actions}>
      <ErrorBanner message={error} />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <MetricCard title={t('analytics.metric.cloudRevision')} value={currentRevision} tone="info" helper={t('analytics.metric.cloudRevisionHelper')} />
        <MetricCard title={t('analytics.metric.smsCode')} value={stats.smsCode} tone="success" helper={t('analytics.metric.smsCodeHelper')} />
        <MetricCard title={t('analytics.metric.smsPlain')} value={stats.smsPlain} helper={t('analytics.metric.smsPlainHelper')} />
        <MetricCard title={t('analytics.metric.appNotify')} value={stats.appNotify} helper={t('analytics.metric.appNotifyHelper')} />
        <MetricCard title={t('analytics.metric.callNotify')} value={stats.callNotify} tone="warning" helper={t('analytics.metric.callNotifyHelper')} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.2fr_1fr]">
        <SurfaceCard title={t('analytics.auditTitle')} subtitle={t('analytics.auditSubtitle')}>
          {!auditLogs.length ? (
            <EmptyCard title={t('analytics.auditEmptyTitle')} message={t('analytics.auditEmptyMessage')} />
          ) : (
            <div className="space-y-3">
              {auditLogs.map((log) => (
                <div
                  key={log.id}
                  className="rounded-[24px] border border-[#d9e6b1] bg-[linear-gradient(180deg,#ffffff_0%,#f7fbe9_100%)] px-4 py-3.5 shadow-[0_12px_30px_-24px_rgba(98,122,28,0.2)]"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <RelayBadge tone="accent">{t('analytics.auditRevision', { revision: log.revision })}</RelayBadge>
                    <RelayBadge>{log.actorType}</RelayBadge>
                    {log.actorId > 0 ? <RelayBadge>{t('analytics.auditActor', { actorId: log.actorId })}</RelayBadge> : null}
                  </div>
                  <div className="mt-2 text-sm font-medium text-[#243115]">{log.summary}</div>
                  <div className="mt-1 text-xs text-[#6c785d]">{new Date(log.createdAt).toLocaleString()}</div>
                </div>
              ))}
            </div>
          )}
        </SurfaceCard>

        <SurfaceCard title={t('analytics.deviceActivityTitle')} subtitle={t('analytics.deviceActivitySubtitle', { total: devices.length, active: stats.activeDevices })}>
          {!recentDeviceActivity.length ? (
            <EmptyCard title={t('analytics.deviceEmptyTitle')} message={t('analytics.deviceEmptyMessage')} />
          ) : (
            <div className="space-y-3">
              {recentDeviceActivity.map((device) => (
                <div
                  key={device.id}
                  className="rounded-[24px] border border-[#d9e6b1] bg-[linear-gradient(180deg,#ffffff_0%,#f7fbe9_100%)] px-4 py-3.5 shadow-[0_12px_30px_-24px_rgba(98,122,28,0.2)]"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="font-medium text-[#243115]">{device.displayName || device.deviceName}</div>
                    <RelayBadge tone={device.enabled ? 'success' : 'warning'}>
                      {device.enabled ? t('common.enabled') : t('common.disabled')}
                    </RelayBadge>
                  </div>
                  <div className="mt-1 text-sm text-[#6c785d]">{device.platform} / {device.deviceModel || t('common.unknownModel')} / {device.appVersion || t('common.unknownVersion')}</div>
                  <div className="mt-1 text-xs text-[#6c785d]">
                    {device.lastSeenAt ? t('common.lastSeen', { time: new Date(device.lastSeenAt).toLocaleString() }) : t('common.noHeartbeatYet')}
                  </div>
                </div>
              ))}
            </div>
          )}
        </SurfaceCard>
      </div>
    </PageShell>
  )
}
