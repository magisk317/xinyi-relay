import { useCallback, useEffect, useState } from 'react'
import { desktopApi } from '../api/desktopApi'
import { useDesktopDeviceConfig } from '../hooks/useDesktopDeviceConfig'
import { useDesktopRealtimeRefresh } from '../hooks/useDesktopRealtimeRefresh'
import { useDesktopI18n } from '../i18n'
import { useDesktop } from '../state/DesktopContext'
import type { RecordsResponse, SystemInfoState } from '../../../shared/contracts/console'
import { EmptyState, Metric, Panel, Tag, translateConnectionState } from '../ui'

const OVERVIEW_REFRESH_EVENTS = [
  'device.registered',
  'device.updated',
  'device.revoked',
  'device.heartbeat',
  'records.ingested',
  'device.config.updated',
  'device.config.command.updated'
] as const

type OverviewSnapshot = {
  systemInfo: SystemInfoState | null
  records: RecordsResponse['records']
}

export function OverviewPage() {
  const { t } = useDesktopI18n()
  const { bootstrap, activeProfile, connection, lastRealtimeEvent, session, runMode } = useDesktop()
  const { config, root, devices, refresh: refreshDeviceConfig } = useDesktopDeviceConfig()

  const [snapshot, setSnapshot] = useState<OverviewSnapshot>({
    systemInfo: null,
    records: []
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      const [systemInfo, _refreshed, records] = await Promise.all([
        runMode === 'local' ? Promise.resolve(null) : desktopApi.getSystemInfo(),
        refreshDeviceConfig(),
        desktopApi.getRecords(12)
      ])
      setSnapshot({
        systemInfo,
        records: records.records
      })
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : t('error.loadOverview'))
    } finally {
      setLoading(false)
    }
  }, [refreshDeviceConfig, runMode, t])

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
    root?.senders,
    root?.deviceAppInfos && Object.keys(root.deviceAppInfos).length > 0 ? root.deviceAppInfos : null,
    root?.notifyRoutes,
    root?.forwardFilters
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
        {error ? <div className="banner banner--danger">{t(error)}</div> : null}
        <div className="metrics-grid metrics-grid--overview">
          <Metric label={t('overview.platform')} value={runMode === 'local' ? 'Local SQLite' : (bootstrap?.platform ?? 'desktop')} />
          <Metric label={t('overview.session')} value={runMode === 'local' ? t('common.localMode') : (session.authenticated ? t('common.authenticated') : t('common.signedOut'))} />
          <Metric label={t('overview.backend')} value={runMode === 'local' ? 'local://sqlite' : (activeProfile?.name ?? t('common.noActiveBackend'))} />
          <Metric label={t('overview.connection')} value={runMode === 'local' ? t('common.localMode') : translateConnectionState(connection.state, t)} />
          <Metric label={t('analytics.cloudRevision')} value={config?.revision ?? t('common.none')} />
          <Metric label={t('app.route.devices')} value={devices.length} />
          <Metric label={t('app.route.records')} value={snapshot.records.length} />
          <Metric label={t('overview.trackedConfig')} value={routingAssets.filter(Boolean).length} />
        </div>
      </Panel>

      <div className="split-grid">
        <Panel title={runMode === 'local' ? t('overview.localStorageTitle') : t('overview.backendServiceTitle')}>
          <div className="metrics-grid metrics-grid--balanced">
            {runMode === 'local' ? (
              <>
                <Metric label={t('overview.service')} value="SQLite" />
                <Metric label={t('overview.localUrl')} value="local-data.db" compact />
                <Metric label={t('app.route.devices')} value={devices.length} />
                <Metric label={t('app.route.records')} value={snapshot.records.length} />
              </>
            ) : (
              <>
                <Metric label={t('overview.service')} value={snapshot.systemInfo?.service ?? (loading ? t('common.loading') : t('common.unavailable'))} />
                <Metric label={t('overview.localUrl')} value={snapshot.systemInfo?.localBaseUrl ?? activeProfile?.baseUrl ?? t('common.none')} compact />
                <Metric label={t('overview.users')} value={snapshot.systemInfo?.userCount ?? t('common.none')} />
                <Metric label={t('overview.database')} value={snapshot.systemInfo?.databaseReady ? t('common.ready') : t('common.unknown')} />
              </>
            )}
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
              {devices.slice(0, 3).map((device) => (
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
