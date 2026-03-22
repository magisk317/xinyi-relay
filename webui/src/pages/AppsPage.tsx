import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '../api/client'
import type { AppItem } from '../types'
import { trackEvent } from '../analytics'
import { ActionButton, ErrorBanner, LoadingCard, PageShell, RelaySwitch, SurfaceCard } from '../template'

export function AppsPage() {
  const [apps, setApps] = useState<AppItem[]>([])
  const [search, setSearch] = useState('')
  const [saving, setSaving] = useState<string>('')
  const [error, setError] = useState('')

  const load = async () => {
    try {
      setError('')
      setApps(await apiClient.getApps())
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
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
      setError(err instanceof Error ? err.message : '保存失败')
    } finally {
      setSaving('')
    }
  }

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <input
        className="relay-input min-w-[16rem] text-sm"
        placeholder="搜索包名或应用名"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'apps' })
          void load()
        }}
      >
        刷新应用
      </ActionButton>
    </div>
  )

  if (!apps.length && !error) {
    return (
      <PageShell
        title="应用控制"
        description="在这里统一管理自动输入拦截、通知转发和应用级模板。"
        badge="Applications"
        actions={actions}
      >
        <LoadingCard title="正在加载应用列表" message="正在合并已安装应用与现有配置。" />
      </PageShell>
    )
  }

  return (
    <PageShell
      title="应用控制"
      description="管理应用级拦截和通知转发策略，模板会在失焦后自动保存。"
      badge="Applications"
      actions={actions}
    >
      <ErrorBanner message={error} />
      <SurfaceCard
        title="应用列表"
        subtitle={`当前显示 ${filtered.length} / ${apps.length} 个应用，模板字段失焦后自动提交。`}
      >
        <div className="relay-table-shell">
          <table className="relay-table">
            <thead>
              <tr>
                <th>应用</th>
                <th>包名</th>
                <th>自动输入拦截</th>
                <th>通知转发</th>
                <th>模板</th>
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
