import { useCallback, useEffect, useMemo, useState } from 'react'
import { desktopApi } from '../api/desktopApi'
import { normalizeConfigRoot } from '../configSnapshot'
import { useDesktopRealtimeRefresh } from '../hooks/useDesktopRealtimeRefresh'
import { useDesktopConfigSnapshotEditor } from '../hooks/useDesktopConfigSnapshotEditor'
import { useDesktopI18n } from '../i18n'
import type { ConfigAuditLogItem } from '../../../shared/contracts/console'
import { EmptyState, Metric, Panel, Tag } from '../ui'

const CONFIG_REFRESH_EVENTS = ['config.updated'] as const

export function ConfigPage() {
  const { t } = useDesktopI18n()
  const {
    config,
    root,
    loading,
    saving,
    error,
    setError,
    load: loadSnapshot,
    saveRoot
  } = useDesktopConfigSnapshotEditor()
  const [logs, setLogs] = useState<ConfigAuditLogItem[]>([])
  const [rawDraft, setRawDraft] = useState('{}')

  const loadAuditLogs = useCallback(async () => {
    const auditPayload = await desktopApi.fetchConfigAuditLogs(30, 0)
    setLogs(auditPayload.logs)
  }, [])

  const load = useCallback(async () => {
    try {
      const [snapshot] = await Promise.all([loadSnapshot(), loadAuditLogs()])
      setRawDraft(JSON.stringify(normalizeConfigRoot(snapshot.snapshot), null, 2))
    } catch {
      // loadSnapshot already updates page error state.
    }
  }, [loadAuditLogs, loadSnapshot])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  useEffect(() => {
    if (!root) return
    setRawDraft(JSON.stringify(root, null, 2))
  }, [config?.revision, root])

  useDesktopRealtimeRefresh(() => {
    void load()
  }, CONFIG_REFRESH_EVENTS)

  const routingAssets = useMemo(
    () => (root?.notifyRoutes?.length ?? 0) + (root?.smsCodeRules?.length ?? 0) + (root?.forwardFilters?.length ?? 0),
    [root?.forwardFilters?.length, root?.notifyRoutes?.length, root?.smsCodeRules?.length]
  )

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
        {error ? <div className="banner banner--danger">{error}</div> : null}
        <div className="metrics-grid">
          <Metric label={t('analytics.cloudRevision')} value={config?.revision ?? 0} />
          <Metric label={t('app.route.senders')} value={root?.senders?.length ?? 0} />
          <Metric label={t('app.route.apps')} value={root?.appInfos?.length ?? 0} />
          <Metric label={t('config.routingAssets')} value={routingAssets} />
        </div>
      </Panel>

      <Panel title={t('config.advancedJsonTitle')}>
        <details className="sender-editor-advanced">
          <summary>{t('config.rawSnapshot')}</summary>
          <div className="stack">
            <textarea
              className="code-editor"
              value={rawDraft}
              onChange={(event) => setRawDraft(event.target.value)}
            />
            <div className="button-row">
              <button
                type="button"
                className="primary-button"
                disabled={!config || saving}
                onClick={() => {
                  if (!config) return
                  try {
                    const parsed = JSON.parse(rawDraft) as Record<string, unknown>
                    const normalized = normalizeConfigRoot(parsed)
                    void saveRoot(normalized).then((nextSnapshot) => {
                      setRawDraft(JSON.stringify(normalizeConfigRoot(nextSnapshot.snapshot), null, 2))
                      return loadAuditLogs()
                    }).catch(() => {})
                  } catch (nextError) {
                    setError(nextError instanceof Error ? nextError.message : t('config.invalidJson'))
                  }
                }}
              >
                {saving ? t('common.saving') : t('config.saveSnapshot')}
              </button>
            </div>
          </div>
        </details>
      </Panel>

      <Panel title={t('config.auditTitle')}>
        {!logs.length && !loading ? (
          <EmptyState title={t('config.auditEmptyTitle')} />
        ) : (
          <div className="list-grid">
            {logs.map((log) => (
              <article key={log.id} className="list-card">
                <div className="list-card-head">
                  <div>
                    <h3>{t('analytics.revisionLabel').replace('{revision}', String(log.revision))}</h3>
                    <p>{log.summary}</p>
                  </div>
                  <Tag tone="neutral">{log.actorType}</Tag>
                </div>
                <div className="list-card-body">
                  <div>{t('config.actorId')}: {log.actorId > 0 ? log.actorId : t('common.notAvailable')}</div>
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
