import { useCallback, useEffect, useMemo, useState } from 'react'
import { desktopApi } from '../api/desktopApi'
import { useDesktopRealtimeRefresh } from '../hooks/useDesktopRealtimeRefresh'
import { useDesktopI18n } from '../i18n'
import { useDesktop } from '../state/DesktopContext'
import type { ConfigAuditLogItem, DeviceItem, RecordItem } from '../../../shared/contracts/console'
import { EmptyState, Metric, Panel, Tag, translateConnectionState } from '../ui'

const ANALYTICS_REFRESH_EVENTS = [
  'device.registered',
  'device.updated',
  'device.revoked',
  'device.heartbeat',
  'config.updated',
  'records.ingested'
] as const

export function AnalyticsPage() {
  const { t } = useDesktopI18n()
  const { connection } = useDesktop()
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
        desktopApi.getRecords(200),
        desktopApi.getDevices(),
        desktopApi.getConfigAuditLogs(30, 0),
        desktopApi.getConfigSnapshot()
      ])
      setRecords(recordsResp.records)
      setDevices(devicesResp.devices)
      setAuditLogs(auditResp.logs)
      setCurrentRevision(snapshot.revision)
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : t('error.loadAnalytics'))
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
  }, ANALYTICS_REFRESH_EVENTS)

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

  const recordBreakdown = useMemo(() => {
    const total = Math.max(records.length, 1)
    return [
      { label: t('analytics.verificationSms'), value: stats.smsCode, tone: 'success' as const },
      { label: t('analytics.plainSms'), value: stats.smsPlain, tone: 'neutral' as const },
      { label: t('analytics.appNotify'), value: stats.appNotify, tone: 'neutral' as const },
      { label: t('analytics.calls'), value: stats.callNotify, tone: 'warning' as const }
    ].map((item) => ({
      ...item,
      percent: Math.round((item.value / total) * 100)
    }))
  }, [records.length, stats, t])

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

  const topSenders = useMemo(() => {
    const counts = new Map<string, number>()
    for (const record of records) {
      const key = record.sender || record.packageName || t('common.unknown')
      counts.set(key, (counts.get(key) ?? 0) + 1)
    }
    return [...counts.entries()].sort((left, right) => right[1] - left[1]).slice(0, 6)
  }, [records, t])

  return (
    <div className="page-grid">
      <Panel
        title={t('analytics.title')}
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
        <div className="metrics-grid">
          <Metric label={t('analytics.cloudRevision')} value={currentRevision} />
          <Metric label={t('analytics.verificationSms')} value={stats.smsCode} />
          <Metric label={t('analytics.plainSms')} value={stats.smsPlain} />
          <Metric label={t('analytics.appNotify')} value={stats.appNotify} />
          <Metric label={t('analytics.calls')} value={stats.callNotify} />
          <Metric label={t('analytics.activeDevices')} value={stats.activeDevices} />
        </div>
      </Panel>

      <div className="split-grid">
        <Panel title={t('analytics.recordMixTitle')}>
          {!records.length && !loading ? (
            <EmptyState title={t('analytics.noRecordsTitle')} />
          ) : (
            <div className="stack">
              {recordBreakdown.map((item) => (
                <div key={item.label} className="bar-row">
                  <div className="bar-row-head">
                    <span>{item.label}</span>
                    <strong>{item.value}</strong>
                  </div>
                  <div className="bar-track">
                    <div className={`bar-fill bar-fill--${item.tone}`} style={{ width: `${item.percent}%` }} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </Panel>

        <Panel title={t('analytics.topSendersTitle')}>
          {!topSenders.length ? (
            <EmptyState title={t('analytics.noSenderActivityTitle')} />
          ) : (
            <div className="stack">
              {topSenders.map(([label, value], index) => (
                <div key={label} className="bar-row">
                  <div className="bar-row-head">
                    <span>#{index + 1} {label}</span>
                    <strong>{value}</strong>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Panel>
      </div>

      <div className="split-grid">
        <Panel title={t('analytics.auditTitle')}>
          {!auditLogs.length ? (
            <EmptyState title={t('analytics.auditEmptyTitle')} />
          ) : (
            <div className="list-grid">
              {auditLogs.map((log) => (
                <article key={log.id} className="list-card">
                  <div className="list-card-head">
                    <div>
                      <h3>{t('analytics.revisionLabel').replace('{revision}', String(log.revision))}</h3>
                      <p>{log.summary}</p>
                    </div>
                    <Tag tone="neutral">{log.actorType}</Tag>
                  </div>
                  <div className="list-card-body">
                    <div>{t('analytics.actorIdLabel')}: {log.actorId > 0 ? log.actorId : t('common.notAvailable')}</div>
                    <div>{new Date(log.createdAt).toLocaleString()}</div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </Panel>

        <Panel title={t('analytics.recentDeviceTitle')}>
          {!recentDeviceActivity.length ? (
            <EmptyState title={t('analytics.noDevicesTitle')} />
          ) : (
            <div className="list-grid">
              {recentDeviceActivity.map((device) => (
                <article key={device.id} className="list-card">
                  <div className="list-card-head">
                    <div>
                      <h3>{device.displayName || device.deviceName}</h3>
                      <p>{device.platform} / {device.deviceModel || t('common.unknownModel')}</p>
                    </div>
                    <Tag tone={device.enabled ? 'success' : 'warning'}>
                      {device.enabled ? t('profile.enabled') : t('profile.disabled')}
                    </Tag>
                  </div>
                  <div className="list-card-body">
                    <div>{t('analytics.version')}: {device.appVersion || t('common.unknown')}</div>
                    <div>{t('common.lastSeen')}: {device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString() : t('common.noHeartbeatYet')}</div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </Panel>
      </div>
    </div>
  )
}
