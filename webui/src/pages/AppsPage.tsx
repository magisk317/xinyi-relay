import { useEffect, useMemo, useState } from 'react'
import { cloneSnapshot } from '../configSnapshot'
import { useRealtimeFeed } from '../realtime'
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
  SurfaceCard,
  ToggleRow
} from '../template'
import type {
  SnapshotAppInfo,
  SnapshotForwardFilterRule,
  SnapshotNotifyRouteRule,
  SnapshotSmsCodeRule
} from '../types'
import { useConfigSnapshotEditor } from '../useConfigSnapshotEditor'

const EMPTY_APP_INFOS: SnapshotAppInfo[] = []
const EMPTY_NOTIFY_ROUTES: SnapshotNotifyRouteRule[] = []
const EMPTY_SMS_CODE_RULES: SnapshotSmsCodeRule[] = []
const EMPTY_FORWARD_FILTERS: SnapshotForwardFilterRule[] = []

export function AppsPage() {
  const { t } = useI18n()
  const { connected, lastEvent } = useRealtimeFeed()
  const { config, root, loading, saving, error, setError, load, saveRoot } = useConfigSnapshotEditor()
  const [search, setSearch] = useState('')
  const [draftPackageName, setDraftPackageName] = useState('')
  const [draftLabel, setDraftLabel] = useState('')

  useEffect(() => {
    queueMicrotask(() => {
      void load().catch(() => {})
    })
  }, [load])

  useEffect(() => {
    if (lastEvent?.type === 'config.updated') {
      void load().catch(() => {})
    }
  }, [lastEvent, load])

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

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'apps' })
          void load().catch(() => {})
        }}
      >
        {t('apps.refresh')}
      </ActionButton>
    </div>
  )

  async function persistApps(nextApps: SnapshotAppInfo[]) {
    if (!root) return
    const nextRoot = cloneSnapshot(root)
    nextRoot.appInfos = nextApps
    try {
      await saveRoot(nextRoot)
    } catch {
      // saveRoot updates error state itself.
    }
  }

  if (loading && !root && !error) {
    return (
      <PageShell title={t('apps.title')} description={t('apps.description')} badge={t('apps.title')} actions={actions}>
        <LoadingCard title={t('apps.loadingTitle')} message={t('apps.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('apps.title')} description={t('apps.remoteDescription')} badge={t('apps.title')} actions={actions}>
      <ErrorBanner message={error} />

      <div className="grid gap-4 md:grid-cols-4">
        <MetricCard title={t('apps.metric.tracked')} value={appInfos.length} helper={t('apps.metric.trackedHelper')} />
        <MetricCard title={t('apps.metric.blocked')} value={appInfos.filter((item) => item.blocked).length} tone="warning" helper={t('apps.metric.blockedHelper')} />
        <MetricCard title={t('apps.metric.forwarding')} value={appInfos.filter((item) => item.forwarding).length} tone="success" helper={t('apps.metric.forwardingHelper')} />
        <MetricCard title={t('apps.metric.routingAssets')} value={notifyRoutes.length + smsCodeRules.length + forwardFilters.length} tone="info" helper={t('apps.metric.routingAssetsHelper')} />
      </div>

      <SurfaceCard title={t('apps.addTitle')} subtitle={t('apps.addSubtitle')}>
        <div className="grid gap-3 md:grid-cols-[1.4fr_1fr_auto]">
          <input
            className="relay-input"
            placeholder={t('apps.packagePlaceholder')}
            value={draftPackageName}
            onChange={(event) => setDraftPackageName(event.target.value)}
          />
          <input
            className="relay-input"
            placeholder={t('apps.labelPlaceholder')}
            value={draftLabel}
            onChange={(event) => setDraftLabel(event.target.value)}
          />
          <ActionButton
            tone="primary"
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
          </ActionButton>
        </div>
      </SurfaceCard>

      <SurfaceCard title={t('apps.listTitle')} subtitle={t('apps.filteredSubtitle', { filtered: filteredItems.length, total: appInfos.length })}>
        <div className="mb-4">
          <input
            className="relay-input"
            placeholder={t('apps.searchPlaceholder')}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>

        {!filteredItems.length ? (
          <EmptyCard
            title={appInfos.length ? t('apps.emptyFilteredTitle') : t('apps.emptyTitle')}
            message={appInfos.length ? t('apps.emptyFilteredMessage') : t('apps.emptyMessage')}
          />
        ) : (
          <div className="space-y-4">
            {filteredItems.map((item) => {
              const routeCount = notifyRoutes.filter((route) => route.packageName === item.packageName).length
              const filterCount = forwardFilters.filter((rule) => rule.scopeKey === item.packageName || rule.scopeKey.startsWith(`${item.packageName}:`)).length
              return (
                <SurfaceCard key={`${item.packageName}-${config?.revision ?? 0}`} className="bg-white/96">
                  <div className="mb-4 flex items-start justify-between gap-3">
                    <div>
                      <div className="font-medium text-[#243115]">{item.label || item.packageName}</div>
                      <div className="mt-1 text-sm text-[#6c785d]">{item.packageName}</div>
                      <div className="mt-2 flex flex-wrap gap-2">
                        <RelayBadge tone={item.blocked ? 'warning' : 'muted'}>
                          {item.blocked ? t('apps.badge.blocked') : t('apps.badge.allowed')}
                        </RelayBadge>
                        <RelayBadge tone={item.forwarding ? 'success' : 'muted'}>
                          {item.forwarding ? t('apps.badge.forwardingEnabled') : t('apps.badge.forwardingDisabled')}
                        </RelayBadge>
                        <RelayBadge>{t('apps.badge.routes', { count: routeCount })}</RelayBadge>
                        <RelayBadge>{t('apps.badge.filters', { count: filterCount })}</RelayBadge>
                      </div>
                    </div>
                    <ActionButton
                      tone="danger"
                      className="px-3 py-2 text-xs"
                      onClick={() => {
                        if (!window.confirm(t('apps.deleteConfirm'))) return
                        void persistApps(appInfos.filter((app) => app.packageName !== item.packageName))
                      }}
                    >
                      {t('common.delete')}
                    </ActionButton>
                  </div>

                  <div className="grid gap-3 md:grid-cols-2">
                    <input
                      className="relay-input"
                      defaultValue={item.label ?? ''}
                      onBlur={(event) => {
                        const value = event.target.value
                        if (value === (item.label ?? '')) return
                        void persistApps(
                          appInfos.map((app) => (app.packageName === item.packageName ? { ...app, label: value } : app))
                        )
                      }}
                    />
                    <div className="rounded-[24px] border border-[#d9e6b1] bg-[linear-gradient(180deg,#ffffff_0%,#f7fbe9_100%)] px-4 py-3.5 text-sm text-[#5d6c47] shadow-[0_12px_30px_-24px_rgba(98,122,28,0.2)]">
                      {t('apps.forwardingConfigured')}: <span className="font-medium text-[#243115]">{item.forwardingConfigured ? t('common.yes') : t('common.no')}</span>
                    </div>
                  </div>

                  <div className="mt-4 grid gap-3 md:grid-cols-2">
                    <ToggleRow
                      label={t('apps.table.blocked')}
                      checked={item.blocked}
                      onChange={(value) => {
                        void persistApps(
                          appInfos.map((app) => (app.packageName === item.packageName ? { ...app, blocked: value } : app))
                        )
                      }}
                    />
                    <ToggleRow
                      label={t('apps.table.forwarding')}
                      checked={item.forwarding}
                      onChange={(value) => {
                        void persistApps(
                          appInfos.map((app) =>
                            app.packageName === item.packageName
                              ? { ...app, forwarding: value, forwardingConfigured: value || app.forwardingConfigured }
                              : app
                          )
                        )
                      }}
                    />
                  </div>

                  <textarea
                    className="relay-input mt-4 min-h-[8rem]"
                    placeholder={t('apps.table.template')}
                    defaultValue={item.notifyTemplate}
                    onBlur={(event) => {
                      const value = event.target.value
                      if (value === item.notifyTemplate) return
                      void persistApps(
                        appInfos.map((app) =>
                          app.packageName === item.packageName
                            ? { ...app, notifyTemplate: value, forwardingConfigured: value.trim().length > 0 || app.forwardingConfigured }
                            : app
                        )
                      )
                    }}
                  />
                </SurfaceCard>
              )
            })}
          </div>
        )}
      </SurfaceCard>
    </PageShell>
  )
}
