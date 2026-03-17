import { useEffect, useMemo, useState } from 'react'
import { Button, Checkbox, Table, TableBody, TableCell, TableHead, TableHeadCell, TableRow, TextInput } from 'flowbite-react'
import { apiClient } from '../api/client'
import type { AppItem } from '../types'
import { trackEvent } from '../analytics'
import { ErrorBanner, LoadingCard, PageShell, SurfaceCard } from '../template'

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
      <TextInput
        sizing="sm"
        placeholder="搜索包名或应用名"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      <Button
        color="alternative"
        size="sm"
        onClick={() => {
          trackEvent('refresh', { page: 'apps' })
          void load()
        }}
      >
        刷新应用
      </Button>
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
        <div className="overflow-x-auto">
          <Table hoverable>
            <TableHead>
              <TableRow>
                <TableHeadCell>应用</TableHeadCell>
                <TableHeadCell>包名</TableHeadCell>
                <TableHeadCell>自动输入拦截</TableHeadCell>
                <TableHeadCell>通知转发</TableHeadCell>
                <TableHeadCell>模板</TableHeadCell>
              </TableRow>
            </TableHead>
            <TableBody className="divide-y">
            {filtered.map((item) => (
              <TableRow key={item.packageName} className="bg-white align-top">
                <TableCell className="font-medium text-slate-900">{item.label}</TableCell>
                <TableCell className="text-slate-500">{item.packageName}</TableCell>
                <TableCell>
                  <Checkbox
                    checked={item.blocked}
                    disabled={saving === item.packageName}
                    onChange={(e) => void updateItem(item, { blocked: e.target.checked })}
                  />
                </TableCell>
                <TableCell>
                  <Checkbox
                    checked={item.forwarding}
                    disabled={saving === item.packageName}
                    onChange={(e) => void updateItem(item, { forwarding: e.target.checked })}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    sizing="sm"
                    className="min-w-72"
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
                </TableCell>
              </TableRow>
            ))}
            </TableBody>
          </Table>
        </div>
      </SurfaceCard>
    </PageShell>
  )
}
