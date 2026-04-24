import { useMemo } from 'react'
import { ProfileManagerPanel } from '../components/ProfileManagerPanel'
import { useDesktopI18n } from '../i18n'
import { useDesktop } from '../state/DesktopContext'
import { Panel } from '../ui'

export function AdvancedPage() {
  const { t } = useDesktopI18n()
  const {
    bootstrap,
    activeProfile,
    lastDiagnosticsExport,
    lastProbe,
    probeBackend,
    exportDiagnostics,
    updateNotifications,
    sendTestNotification
  } = useDesktop()

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
