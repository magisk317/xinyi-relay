import { useCallback, useEffect, useMemo, useState } from 'react'
import { desktopApi } from '../api/desktopApi'
import { useDesktopDeviceConfig } from '../hooks/useDesktopDeviceConfig'
import { useDesktopRealtimeRefresh } from '../hooks/useDesktopRealtimeRefresh'
import { useDesktopI18n } from '../i18n'
import type {
  DeviceConfigAuditLogItem
} from '../../../shared/contracts/console'
import { EmptyState, Metric, Panel, Tag } from '../ui'

const CONFIG_REFRESH_EVENTS = [
  'device.registered',
  'device.updated',
  'device.config.updated',
  'device.config.command.updated'
] as const

export function ConfigPage() {
  const { t } = useDesktopI18n()
  const {
    devices,
    selectedDeviceId,
    setSelectedDeviceId,
    config,
    root,
    loading,
    error,
    refresh,
  } = useDesktopDeviceConfig()
  const [logs, setLogs] = useState<DeviceConfigAuditLogItem[]>([])

  useEffect(() => {
    queueMicrotask(() => {
      if (!selectedDeviceId) {
        setLogs([])
        return
      }
      void desktopApi.getDeviceConfigAuditLogs(selectedDeviceId, 30, 0)
        .then((nextLogs) => {
          setLogs(nextLogs.logs ?? [])
        })
        .catch(() => {
          setLogs([])
        })
    })
  }, [selectedDeviceId])

  const load = useCallback(async () => {
    try {
      await refresh()
      if (!selectedDeviceId) {
        setLogs([])
        return
      }
      const nextLogs = await desktopApi.getDeviceConfigAuditLogs(selectedDeviceId, 30, 0)
      setLogs(nextLogs.logs ?? [])
    } catch {
      setLogs([])
    }
  }, [refresh, selectedDeviceId])

  useDesktopRealtimeRefresh(() => {
    void load()
  }, CONFIG_REFRESH_EVENTS)

  useEffect(() => {
    queueMicrotask(() => {
      void load().catch(() => {
        setLogs([])
      })
    })
  }, [load])

  const routingAssets = useMemo(
    () => (root?.notifyRoutes?.length ?? 0) + (root?.smsCodeRules?.length ?? 0) + (root?.forwardFilters?.length ?? 0),
    [root]
  )

  const selectedDevice = devices.find((device) => device.id === selectedDeviceId) ?? null

  return (
    <div className="page-grid">
      <Panel
        title={t('config.title')}
        actions={
          <div className="button-row">
            <button type="button" className="ghost-button" onClick={() => void load()}>
              {t('common.refresh')}
            </button>
            {config ? <Tag tone="neutral">{t('analytics.revisionLabel').replace('{revision}', String(config.revision))}</Tag> : null}
          </div>
        }
      >
        {error ? <div className="banner banner--danger">{t(error)}</div> : null}
        {!devices.length && !loading ? (
          <EmptyState title={t('analytics.noDevicesTitle')} />
        ) : (
          <>
            {devices.length > 0 ? (
              <div className="field" style={{ marginBottom: '1rem' }}>
                <span>{t('devices.title')}</span>
                <select
                  className="text-input"
                  value={selectedDeviceId ?? ''}
                  onChange={(event) => setSelectedDeviceId(Number(event.target.value))}
                >
                  {devices.map((device) => (
                    <option key={device.id} value={device.id}>
                      {device.displayName || device.deviceName}
                    </option>
                  ))}
                </select>
              </div>
            ) : null}

            <div className="metrics-grid">
              <Metric label={t('analytics.cloudRevision')} value={config?.revision ?? 0} />
              <Metric label={t('app.route.senders')} value={root?.senders?.length ?? 0} />
              <Metric label={t('app.route.apps')} value={Object.values(root?.deviceAppInfos ?? {}).reduce((acc, apps) => acc + apps.length, 0)} />
              <Metric label={t('config.routingAssets')} value={routingAssets} />
              <Metric label={t('analytics.auditTitle')} value={config?.pendingCommands.length ?? 0} detail="pending" compact />
              <Metric label={t('devices.title')} value={selectedDevice?.deviceModel || t('common.notAvailable')} detail={selectedDevice?.platform || ''} compact />
            </div>
          </>
        )}
      </Panel>

      <Panel title={t('config.auditTitle')} subtitle={selectedDevice ? selectedDevice.displayName || selectedDevice.deviceName : undefined}>
        {!config?.pendingCommands.length ? (
          <EmptyState title={t('config.auditEmptyTitle')} />
        ) : (
          <div className="list-grid">
            {config.pendingCommands.map((command) => (
              <article key={command.id} className="list-card">
                <div className="list-card-head">
                  <div>
                    <h3>{command.summary}</h3>
                    <p>{t('analytics.revisionLabel').replace('{revision}', String(command.targetRevision))}</p>
                  </div>
                  <Tag tone={command.status === 'applied' ? 'success' : command.status === 'failed' ? 'danger' : 'warning'}>
                    {command.status}
                  </Tag>
                </div>
                <div className="list-card-body">
                  <div>{t('config.actorId')}: {command.actorId > 0 ? command.actorId : t('common.notAvailable')}</div>
                  <div>{new Date(command.createdAt).toLocaleString()}</div>
                  {command.failureReason ? <div>{command.failureReason}</div> : null}
                </div>
              </article>
            ))}
          </div>
        )}
      </Panel>

      <Panel title={t('analytics.auditTitle')}>
        {!logs.length && !loading ? (
          <EmptyState title={t('analytics.auditEmptyTitle')} />
        ) : (
          <div className="list-grid">
            {logs.map((log) => (
              <article key={log.id} className="list-card">
                <div className="list-card-head">
                  <div>
                    <h3>{log.eventType}</h3>
                    <p>{log.summary}</p>
                  </div>
                  <Tag tone="neutral">{log.actorType}</Tag>
                </div>
                <div className="list-card-body">
                  <div>{t('analytics.revisionLabel').replace('{revision}', String(log.revision))}</div>
                  <div>{new Date(log.createdAt).toLocaleString()}</div>
                </div>
              </article>
            ))}
          </div>
        )}
      </Panel>
    </div>
  )
}
