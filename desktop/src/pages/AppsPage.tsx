import { useEffect, useMemo, useState } from 'react'
import { cloneSnapshot } from '../configSnapshot'
import { useDesktopConfigSnapshotEditor } from '../hooks/useDesktopConfigSnapshotEditor'
import { useDesktopRealtimeRefresh } from '../hooks/useDesktopRealtimeRefresh'
import { useDesktopI18n } from '../i18n'
import type {
  SnapshotAppInfo,
  SnapshotForwardFilterRule,
  SnapshotNotifyRouteRule,
  SnapshotSmsCodeRule
} from '../../../shared/contracts/console'
import { EmptyState, Metric, Panel, Tag } from '../ui'

const EMPTY_APP_INFOS: SnapshotAppInfo[] = []
const EMPTY_NOTIFY_ROUTES: SnapshotNotifyRouteRule[] = []
const EMPTY_SMS_CODE_RULES: SnapshotSmsCodeRule[] = []
const EMPTY_FORWARD_FILTERS: SnapshotForwardFilterRule[] = []
const APP_REFRESH_EVENTS = ['config.updated'] as const

export function AppsPage() {
  const { t } = useDesktopI18n()
  const { config, root, saving, error, setError, load, saveRoot } = useDesktopConfigSnapshotEditor()
  const [search, setSearch] = useState('')
  const [draftPackageName, setDraftPackageName] = useState('')
  const [draftLabel, setDraftLabel] = useState('')

  useEffect(() => {
    queueMicrotask(() => {
      void load().catch(() => {})
    })
  }, [load])

  useDesktopRealtimeRefresh(() => {
    void load().catch(() => {})
  }, APP_REFRESH_EVENTS)

  const appInfos = useMemo(() => root?.appInfos ?? EMPTY_APP_INFOS, [root?.appInfos])
  const notifyRoutes = useMemo(() => root?.notifyRoutes ?? EMPTY_NOTIFY_ROUTES, [root?.notifyRoutes])
  const smsCodeRules = useMemo(() => root?.smsCodeRules ?? EMPTY_SMS_CODE_RULES, [root?.smsCodeRules])
  const forwardFilters = useMemo(() => root?.forwardFilters ?? EMPTY_FORWARD_FILTERS, [root?.forwardFilters])

  const filteredItems = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    if (!keyword) return appInfos
    return appInfos.filter((item) => {
      const label = item.label?.toLowerCase() ?? ''
      return item.packageName.toLowerCase().includes(keyword) || label.includes(keyword)
    })
  }, [appInfos, search])

  async function persistApps(nextApps: SnapshotAppInfo[]) {
    if (!root) return
    const nextRoot = cloneSnapshot(root)
    nextRoot.appInfos = nextApps
    try {
      await saveRoot(nextRoot)
    } catch {
      // saveRoot updates page error state.
    }
  }

  return (
    <div className="page-grid">
      <Panel
        title={t('apps.title')}
        actions={
          <div className="button-row">
            <button type="button" className="ghost-button" onClick={() => void load().catch(() => {})}>
              {t('common.refresh')}
            </button>
            {config ? <Tag tone="neutral">{t('analytics.revisionLabel').replace('{revision}', String(config.revision))}</Tag> : null}
          </div>
        }
      >
        {error ? <div className="banner banner--danger">{t(error)}</div> : null}
        <div className="metrics-grid">
          <Metric label={t('apps.trackedApps')} value={appInfos.length} />
          <Metric label={t('common.blocked')} value={appInfos.filter((item) => item.blocked).length} />
          <Metric label={t('common.forwardingEnabled')} value={appInfos.filter((item) => item.forwarding).length} />
          <Metric label={t('config.routingAssets')} value={notifyRoutes.length + smsCodeRules.length + forwardFilters.length} />
        </div>
      </Panel>

      <Panel title={t('apps.addTitle')}>
        <div className="editor-grid editor-grid--wide">
          <label className="field">
            <span>{t('apps.packageName')}</span>
            <input className="text-input" value={draftPackageName} onChange={(event) => setDraftPackageName(event.target.value)} placeholder={t('apps.packagePlaceholder')} />
          </label>
          <label className="field">
            <span>{t('apps.label')}</span>
            <input className="text-input" value={draftLabel} onChange={(event) => setDraftLabel(event.target.value)} placeholder={t('apps.labelPlaceholder')} />
          </label>
        </div>
        <div className="button-row">
          <button
            type="button"
            className="primary-button"
            disabled={saving}
            onClick={() => {
              const packageName = draftPackageName.trim()
              if (!packageName) {
                setError(t('apps.packageRequired'))
                return
              }
              const nextApp: SnapshotAppInfo = {
                packageName,
                label: draftLabel.trim() || packageName,
                blocked: false,
                forwarding: false,
                forwardingConfigured: false,
                notifyTemplate: ''
              }
              void persistApps([nextApp, ...appInfos.filter((item) => item.packageName !== packageName)]).then(() => {
                setDraftPackageName('')
                setDraftLabel('')
              })
            }}
          >
            {saving ? t('common.saving') : t('apps.addAction')}
          </button>
        </div>
      </Panel>

      <Panel title={t('apps.listTitle')}>
        <div className="editor-grid editor-grid--wide">
          <label className="field">
            <span>{t('apps.search')}</span>
            <input className="text-input" value={search} onChange={(event) => setSearch(event.target.value)} placeholder={t('apps.searchPlaceholder')} />
          </label>
        </div>

        {!filteredItems.length ? (
          <EmptyState
            title={appInfos.length ? t('apps.noMatchingTitle') : t('apps.emptyTitle')}
          />
        ) : (
          <div className="list-grid">
            {filteredItems.map((item) => {
              const routeCount = notifyRoutes.filter((route) => route.packageName === item.packageName).length
              const filterCount = forwardFilters.filter((rule) => rule.scopeKey === item.packageName || rule.scopeKey.startsWith(`${item.packageName}:`)).length
              const templateConfigured = item.notifyTemplate.trim().length > 0
              return (
                <article key={`${item.packageName}-${config?.revision ?? 0}`} className="list-card list-card--editor">
                  <div className="list-card-head">
                    <div>
                      <h3>{item.label || item.packageName}</h3>
                      <p>{item.packageName}</p>
                    </div>
                    <div className="button-row">
                      <Tag tone={item.blocked ? 'warning' : 'neutral'}>{item.blocked ? t('common.blocked') : t('common.allowed')}</Tag>
                      <Tag tone={item.forwarding ? 'success' : 'neutral'}>{item.forwarding ? t('common.forwardingEnabled') : t('common.forwardingDisabled')}</Tag>
                    </div>
                  </div>

                  <div className="metrics-grid metrics-grid--compact">
                    <Metric label={t('apps.notifyRoutes')} value={routeCount} />
                    <Metric label={t('apps.forwardFilters')} value={filterCount} />
                    <Metric label={t('apps.template')} value={templateConfigured ? t('common.configured') : t('common.empty')} />
                    <Metric label={t('apps.configured')} value={item.forwardingConfigured ? t('common.yes') : t('common.no')} />
                  </div>

                  <div className="editor-grid">
                    <label className="field">
                      <span>{t('apps.label')}</span>
                      <input
                        className="text-input"
                        defaultValue={item.label ?? ''}
                        onBlur={(event) => {
                          const value = event.target.value
                          if (value === (item.label ?? '')) return
                          void persistApps(
                            appInfos.map((app) => (app.packageName === item.packageName ? { ...app, label: value } : app))
                          )
                        }}
                      />
                    </label>
                    <label className="field">
                      <span>{t('apps.packageName')}</span>
                      <input className="text-input" value={item.packageName} readOnly />
                    </label>
                  </div>

                  <div className="toggle-grid">
                    <ToggleChip label={t('common.blocked')} checked={item.blocked} onToggle={(checked) => {
                      void persistApps(appInfos.map((app) => (app.packageName === item.packageName ? { ...app, blocked: checked } : app)))
                    }} />
                    <ToggleChip label={t('common.forwardingEnabled')} checked={item.forwarding} onToggle={(checked) => {
                      void persistApps(appInfos.map((app) => (
                        app.packageName === item.packageName
                          ? { ...app, forwarding: checked, forwardingConfigured: checked || app.forwardingConfigured }
                          : app
                      )))
                    }} />
                  </div>

                  <label className="field">
                    <span>{t('apps.notificationTemplate')}</span>
                    <textarea
                      className="text-area-field"
                      rows={5}
                      defaultValue={item.notifyTemplate}
                      placeholder={t('apps.templatePlaceholder')}
                      onBlur={(event) => {
                        const value = event.target.value
                        if (value === item.notifyTemplate) return
                        void persistApps(
                          appInfos.map((app) =>
                            app.packageName === item.packageName
                              ? {
                                  ...app,
                                  notifyTemplate: value,
                                  forwardingConfigured: value.trim().length > 0 || app.forwardingConfigured
                                }
                              : app
                          )
                        )
                      }}
                    />
                  </label>

                  <div className="button-row">
                    <button
                      type="button"
                      className="danger-button"
                      onClick={() => {
                        if (!window.confirm(`Delete app policy for ${item.packageName}?`)) return
                        void persistApps(appInfos.filter((app) => app.packageName !== item.packageName))
                      }}
                    >
                      {t('apps.deletePolicy')}
                    </button>
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </Panel>
    </div>
  )
}

function ToggleChip({
  label,
  checked,
  onToggle
}: {
  label: string
  checked: boolean
  onToggle: (checked: boolean) => void
}) {
  return (
    <label className="toggle-chip">
      <input type="checkbox" checked={checked} onChange={(event) => onToggle(event.target.checked)} />
      <span>{label}</span>
    </label>
  )
}
