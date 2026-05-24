import { useCallback, useEffect, useState } from 'react'
import { desktopApi } from '../api/desktopApi'
import { useDesktopRealtimeRefresh } from '../hooks/useDesktopRealtimeRefresh'
import { useDesktopI18n } from '../i18n'
import { useDesktop } from '../state/DesktopContext'
import type { ConfigSnapshotState, DevicesResponse, RecordsResponse, SystemInfoState } from '../../../shared/contracts/console'
import { EmptyState, Metric, Panel, Tag, translateConnectionState } from '../ui'

const OVERVIEW_REFRESH_EVENTS = [
  'device.registered',
  'device.updated',
  'device.revoked',
  'device.heartbeat',
  'records.ingested',
  'config.updated'
] as const

type OverviewSnapshot = {
  systemInfo: SystemInfoState | null
  devices: DevicesResponse['devices']
  records: RecordsResponse['records']
  config: ConfigSnapshotState | null
}

export function OverviewPage() {
  const { t } = useDesktopI18n()
  const { bootstrap, activeProfile, connection, lastRealtimeEvent } = useDesktop()
  const [snapshot, setSnapshot] = useState<OverviewSnapshot>({
    systemInfo: null,
    devices: [],
    records: [],
    config: null
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      const [systemInfo, devices, records, config] = await Promise.all([
        desktopApi.getSystemInfo(),
        desktopApi.getDevices(),
        desktopApi.getRecords(12),
        desktopApi.getConfigSnapshot()
      ])
      setSnapshot({
        systemInfo,
        devices: devices.devices,
        records: records.records,
        config
      })
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : t('error.loadOverview'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  useDesktopRealtimeRefresh(() => {
    void load()
  }, OVERVIEW_REFRESH_EVENTS)

  const latestRecord = snapshot.records[0] ?? null
  const routingAssets = [
    snapshot.config?.snapshot.senders,
    snapshot.config?.snapshot.appInfos,
    snapshot.config?.snapshot.notifyRoutes,
    snapshot.config?.snapshot.forwardFilters
  ]

  return (
    <div className="page-grid">
      <Panel
        title={t('overview.title')}
        actions={
          <div className="button-row">
            <button type="button" className="ghost-button" onClick={() => void load()}>
              {t('common.refresh')}
            </button>
            <Tag tone={connection.state === 'connected' ? 'success' : connection.state === 'degraded' ? 'warning' : 'danger'}>
              {translateConnectionState(connection.state, t)}
            </Tag>
          </div>
        }
      >
        {error ? <div className="banner banner--danger">{error}</div> : null}
        <div className="metrics-grid metrics-grid--overview">
          <Metric label={t('overview.platform')} value={bootstrap?.platform ?? 'desktop'} />
          <Metric label={t('overview.session')} value={bootstrap?.session.authenticated ? t('common.authenticated') : t('common.signedOut')} />
          <Metric label={t('overview.backend')} value={activeProfile?.name ?? t('common.noActiveBackend')} />
          <Metric label={t('overview.connection')} value={translateConnectionState(connection.state, t)} />
          <Metric label={t('analytics.cloudRevision')} value={snapshot.config?.revision ?? t('common.none')} />
          <Metric label={t('app.route.devices')} value={snapshot.devices.length} />
          <Metric label={t('app.route.records')} value={snapshot.records.length} />
          <Metric label={t('overview.trackedConfig')} value={routingAssets.filter(Boolean).length} />
        </div>
      </Panel>

      <div className="split-grid">
        <Panel title={t('overview.backendServiceTitle')}>
          <div className="metrics-grid metrics-grid--balanced">
            <Metric label={t('overview.service')} value={snapshot.systemInfo?.service ?? (loading ? t('common.loading') : t('common.unavailable'))} />
            <Metric label={t('overview.localUrl')} value={snapshot.systemInfo?.localBaseUrl ?? activeProfile?.baseUrl ?? t('common.none')} compact />
            <Metric label={t('overview.users')} value={snapshot.systemInfo?.userCount ?? t('common.none')} />
            <Metric label={t('overview.database')} value={snapshot.systemInfo?.databaseReady ? t('common.ready') : t('common.unknown')} />
          </div>
        </Panel>

        <Panel title={t('overview.recentActivityTitle')}>
          {latestRecord ? (
            <div className="stack">
              <div className="callout">
                <div className="callout-title">{latestRecord.sender || latestRecord.packageName || t('overview.latestRecord')}</div>
                <div>{latestRecord.body || t('common.emptyBodyValue')}</div>
                <div className="callout-meta">
                  {latestRecord.recordType} · {new Date(latestRecord.occurredAt).toLocaleString()}
                </div>
              </div>
              {snapshot.devices.slice(0, 3).map((device) => (
                <div key={device.id} className="info-row">
                  <span>{device.displayName || device.deviceName}</span>
                  <strong>{device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString() : t('common.noHeartbeatYet')}</strong>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState title={t('overview.emptyActivityTitle')} />
          )}
        </Panel>
      </div>

      <Panel title={t('overview.latestEventTitle')}>
        {lastRealtimeEvent ? (
          <div className="code-block">
            <div className="code-title">{lastRealtimeEvent.type}</div>
            <pre>{JSON.stringify(lastRealtimeEvent, null, 2)}</pre>
          </div>
        ) : (
          <EmptyState title={t('overview.emptyEventTitle')} />
        )}
      </Panel>
    </div>
  )
}
