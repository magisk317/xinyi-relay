import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '../api/client'
import type { AppItem } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, ErrorBanner, LoadingCard, PageShell, RelaySwitch, SurfaceCard } from '../template'

export function AppsPage() {
  const { t } = useI18n()
  const [apps, setApps] = useState<AppItem[]>([])
  const [search, setSearch] = useState('')
  const [saving, setSaving] = useState<string>('')
  const [error, setError] = useState('')

  const load = async () => {
    try {
      setError('')
      setApps(await apiClient.getApps())
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return apps
    return apps.filter((item) =>
      item.packageName.toLowerCase().includes(q) || item.label.toLowerCase().includes(q)
    )
  }, [apps, search])

  const updateItem = async (item: AppItem, patch: Partial<AppItem>) => {
    setSaving(item.packageName)
    try {
      const updated = await apiClient.patchApp(item.packageName, patch)
      setApps((prev) => prev.map((x) => (x.packageName === item.packageName ? updated : x)))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.saveFailed'))
    } finally {
      setSaving('')
    }
  }

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <input
        className="relay-input min-w-[16rem] text-sm"
        placeholder={t('apps.searchPlaceholder')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'apps' })
          void load()
        }}
      >
        {t('apps.refresh')}
      </ActionButton>
    </div>
  )

  if (!apps.length && !error) {
    return (
      <PageShell
        title={t('apps.title')}
        description={t('apps.description')}
        badge="Applications"
        actions={actions}
      >
        <LoadingCard title={t('apps.loadingTitle')} message={t('apps.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell
      title={t('apps.title')}
      description={t('apps.description')}
      badge="Applications"
      actions={actions}
    >
      <ErrorBanner message={error} />
      <SurfaceCard
        title={t('apps.listTitle')}
        subtitle={t('apps.listSubtitle', { filtered: filtered.length, total: apps.length })}
      >
        <div className="relay-table-shell">
          <table className="relay-table">
            <thead>
              <tr>
                <th>{t('apps.table.app')}</th>
                <th>{t('apps.table.package')}</th>
                <th>{t('apps.table.blocked')}</th>
                <th>{t('apps.table.forwarding')}</th>
                <th>{t('apps.table.template')}</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((item) => (
                <tr key={item.packageName}>
                  <td data-strong="true">{item.label}</td>
                  <td>{item.packageName}</td>
                  <td>
                    <RelaySwitch
                      checked={item.blocked}
                      disabled={saving === item.packageName}
                      onChange={(value) => void updateItem(item, { blocked: value })}
                    />
                  </td>
                  <td>
                    <RelaySwitch
                      checked={item.forwarding}
                      disabled={saving === item.packageName}
                      onChange={(value) => void updateItem(item, { forwarding: value })}
                    />
                  </td>
                  <td>
                    <input
                      className="relay-input min-w-72 text-sm"
                      value={item.notifyTemplate}
                      disabled={saving === item.packageName}
                      onChange={(e) => {
                        const value = e.target.value
                        setApps((prev) =>
                          prev.map((x) =>
                            x.packageName === item.packageName ? { ...x, notifyTemplate: value } : x
                          )
                        )
                      }}
                      onBlur={() => void updateItem(item, { notifyTemplate: item.notifyTemplate })}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </SurfaceCard>
    </PageShell>
  )
}
