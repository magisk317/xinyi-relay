import { useMemo, useState } from 'react'
import { ProfileManagerPanel } from '../components/ProfileManagerPanel'
import { desktopApi } from '../api/desktopApi'
import { useDesktopI18n } from '../i18n'
import { useDesktop } from '../state/DesktopContext'
import { DesktopSelect, Panel } from '../ui'
import type { RunMode, SyncReport } from '../../../shared/contracts/console'

export function AdvancedPage() {
  const { t } = useDesktopI18n()
  const {
    bootstrap,
    activeProfile,
    runMode,
    lastDiagnosticsExport,
    lastProbe,
    probeBackend,
    exportDiagnostics,
    updateNotifications,
    sendTestNotification,
    switchRunMode,
    syncData
  } = useDesktop()
  const [syncBusy, setSyncBusy] = useState(false)
  const [lastSyncReport, setLastSyncReport] = useState<SyncReport | null>(null)
  const [dbExportPath, setDbExportPath] = useState('')
  const [dbError, setDbError] = useState('')

  const notificationPrefs = useMemo(() => bootstrap?.notifications, [bootstrap?.notifications])

  return (
    <div className="page-grid">
      <ProfileManagerPanel
        title={t('advanced.backendProfilesTitle')}
        actions={
          <div className="button-row">
            <button
              type="button"
              className="ghost-button"
              disabled={!activeProfile}
              onClick={() => activeProfile && void probeBackend(activeProfile.id).catch(() => {})}
            >
              {t('advanced.probeActiveBackend')}
            </button>
            <button type="button" className="ghost-button" onClick={() => void exportDiagnostics().catch(() => {})}>
              {t('advanced.exportDiagnostics')}
            </button>
          </div>
        }
      />

      <Panel title={t('advanced.runModeTitle')}>
        <div className="stack">
          <div className="field">
            <span>{t('advanced.runModeLabel')}</span>
            <DesktopSelect<RunMode>
              value={runMode}
              options={[
                { value: 'remote', label: t('advanced.modeRemote') },
                { value: 'local', label: t('advanced.modeLocal') },
                { value: 'hybrid', label: t('advanced.modeHybrid') }
              ]}
              onChange={(value) => {
                if (value) void switchRunMode(value).catch(() => {})
              }}
            />
          </div>
          {runMode === 'hybrid' ? (
            <div className="button-row">
              <button
                type="button"
                className="ghost-button"
                disabled={syncBusy}
                onClick={() => {
                  setSyncBusy(true)
                  void syncData('pull')
                    .then(setLastSyncReport)
                    .catch(() => {})
                    .finally(() => setSyncBusy(false))
                }}
              >
                {syncBusy ? t('advanced.syncing') : t('advanced.pullFromRemote')}
              </button>
              <button
                type="button"
                className="ghost-button"
                disabled={syncBusy}
                onClick={() => {
                  setSyncBusy(true)
                  void syncData('push')
                    .then(setLastSyncReport)
                    .catch(() => {})
                    .finally(() => setSyncBusy(false))
                }}
              >
                {syncBusy ? t('advanced.syncing') : t('advanced.pushToRemote')}
              </button>
            </div>
          ) : null}
          {lastSyncReport ? (
            <SyncResultCard report={lastSyncReport} t={t} />
          ) : null}
        </div>
      </Panel>

      {runMode !== 'remote' ? (
        <Panel title={t('advanced.databaseTitle') ?? 'Database'}>
          <div className="stack">
            <div className="button-row">
              <button
                type="button"
                className="ghost-button"
                onClick={() => {
                  setDbError('')
                  void desktopApi.exportDatabase()
                    .then(setDbExportPath)
                    .catch((err) => setDbError(err instanceof Error ? err.message : String(err)))
                }}
              >
                {t('advanced.exportDatabase') ?? 'Export database'}
              </button>
            </div>
            {dbExportPath ? (
              <div className="callout callout--success">
                <div className="callout-title">{t('advanced.exportSuccess') ?? 'Export complete'}</div>
                <div className="callout-meta">{dbExportPath}</div>
              </div>
            ) : null}
            {dbError ? <div className="banner banner--danger">{dbError}</div> : null}
          </div>
        </Panel>
      ) : null}

      <Panel title={t('advanced.nativeBehaviorTitle')}>
        <div className="stack">
          <button
            type="button"
            className="ghost-button"
            onClick={() => {
              void sendTestNotification().catch(() => {})
            }}
          >
            {t('advanced.sendTestNotification')}
          </button>
          <label className="field field--checkbox">
            <input
              type="checkbox"
              checked={notificationPrefs?.enabled ?? false}
              onChange={(event) => {
                if (!notificationPrefs) return
                void updateNotifications({ ...notificationPrefs, enabled: event.target.checked }).catch(() => {})
              }}
            />
            <span>{t('advanced.notificationsEnabled')}</span>
          </label>
          <label className="field field--checkbox">
            <input
              type="checkbox"
              checked={notificationPrefs?.connection ?? false}
              onChange={(event) => {
                if (!notificationPrefs) return
                void updateNotifications({ ...notificationPrefs, connection: event.target.checked }).catch(() => {})
              }}
            />
            <span>{t('advanced.notificationsConnection')}</span>
          </label>
          <label className="field field--checkbox">
            <input
              type="checkbox"
              checked={notificationPrefs?.records ?? false}
              onChange={(event) => {
                if (!notificationPrefs) return
                void updateNotifications({ ...notificationPrefs, records: event.target.checked }).catch(() => {})
              }}
            />
            <span>{t('advanced.notificationsRecords')}</span>
          </label>
          <label className="field field--checkbox">
            <input
              type="checkbox"
              checked={notificationPrefs?.devices ?? false}
              onChange={(event) => {
                if (!notificationPrefs) return
                void updateNotifications({ ...notificationPrefs, devices: event.target.checked }).catch(() => {})
              }}
            />
            <span>{t('advanced.notificationsDevices')}</span>
          </label>
          {lastProbe ? (
            <div className="callout">
              <div className="callout-title">{t('advanced.lastProbe')}</div>
              <div>{lastProbe.message}</div>
              <div className="callout-meta">{t('advanced.tlsStatus')}: {lastProbe.certificateStatus}</div>
            </div>
          ) : null}
          {lastDiagnosticsExport ? (
            <div className="callout">
              <div className="callout-title">{t('advanced.lastDiagnostics')}</div>
              <div>{lastDiagnosticsExport.path}</div>
              <div className="callout-meta">{new Date(lastDiagnosticsExport.createdAt).toLocaleString()}</div>
            </div>
          ) : null}
        </div>
      </Panel>
    </div>
  )
}

function SyncResultCard({ report, t }: { report: SyncReport; t: (key: string) => string }) {
  const config = report.config
  let tone: 'success' | 'warning' | 'danger' | 'info' = 'info'
  let title = ''
  let detail = ''

  if (config.UpToDate) {
    tone = 'success'
    title = t('advanced.syncUpToDate') ?? 'Up to date'
  } else if (config.Pulled) {
    tone = 'success'
    title = t('advanced.syncPulled') ?? 'Pulled from remote'
    detail = `revision ${config.Pulled.newRevision}`
  } else if (config.Pushed) {
    tone = 'success'
    title = t('advanced.syncPushed') ?? 'Pushed to remote'
    detail = `revision ${config.Pushed.newRevision}`
  } else if (config.Conflict) {
    tone = 'danger'
    title = t('advanced.syncConflict') ?? 'Conflict'
    detail = `local r${config.Conflict.localRevision} vs remote r${config.Conflict.remoteRevision}`
  }

  return (
    <div className={`callout callout--${tone}`}>
      <div className="callout-title">{t('advanced.syncResult')}: {title}</div>
      {detail ? <div>{detail}</div> : null}
      <div className="callout-meta">
        {t('advanced.syncDevices')}: {report.devicesSynced} | {t('advanced.syncRecords')}: {report.recordsSynced}
      </div>
      {config.Conflict ? (
        <div className="callout-meta" style={{ marginTop: '0.5rem', opacity: 0.7 }}>
          {t('advanced.syncConflictHint') ?? 'Use Pull to accept remote, or Push to overwrite remote.'}
        </div>
      ) : null}
    </div>
  )
}
