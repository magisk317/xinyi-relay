import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '../api/client'
import type { AppItem } from '../types'

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

  return (
    <div>
      <div className="mb-3 flex items-center justify-between gap-2 max-md:flex-col max-md:items-stretch">
        <h2 className="text-lg font-semibold">应用控制</h2>
        <div className="flex gap-2">
          <input
            className="rounded border px-3 py-1.5 text-sm"
            placeholder="搜索包名或应用名"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <button className="rounded border px-3 py-1.5 text-sm" onClick={() => void load()}>
            刷新
          </button>
        </div>
      </div>

      {error && <p className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b text-left text-slate-500">
              <th className="py-2 pr-3">应用</th>
              <th className="py-2 pr-3">包名</th>
              <th className="py-2 pr-3">自动输入拦截</th>
              <th className="py-2 pr-3">通知转发</th>
              <th className="py-2 pr-3">模板</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item) => (
              <tr key={item.packageName} className="border-b align-top">
                <td className="py-2 pr-3 font-medium">{item.label}</td>
                <td className="py-2 pr-3 text-slate-500">{item.packageName}</td>
                <td className="py-2 pr-3">
                  <input
                    type="checkbox"
                    checked={item.blocked}
                    disabled={saving === item.packageName}
                    onChange={(e) => void updateItem(item, { blocked: e.target.checked })}
                  />
                </td>
                <td className="py-2 pr-3">
                  <input
                    type="checkbox"
                    checked={item.forwarding}
                    disabled={saving === item.packageName}
                    onChange={(e) => void updateItem(item, { forwarding: e.target.checked })}
                  />
                </td>
                <td className="py-2 pr-3">
                  <input
                    className="w-80 rounded border px-2 py-1"
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
    </div>
  )
}
