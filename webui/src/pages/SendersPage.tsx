import { useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import type { SenderItem } from '../types'

const emptySender: Partial<SenderItem> = {
  name: '',
  type: 4,
  jsonSetting: '',
  status: true,
  receiveCode: true,
  receiveNonCode: true,
  receiveAppNotify: true,
  receiveCallNotify: false
}

export function SendersPage() {
  const [items, setItems] = useState<SenderItem[]>([])
  const [draft, setDraft] = useState<Partial<SenderItem>>(emptySender)
  const [error, setError] = useState('')

  const load = async () => {
    try {
      setError('')
      setItems(await apiClient.getSenders())
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const create = async () => {
    if (!draft.name?.trim()) {
      setError('名称不能为空')
      return
    }
    try {
      const created = await apiClient.createSender(draft)
      setItems((prev) => [created, ...prev])
      setDraft(emptySender)
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建失败')
    }
  }

  const patch = async (item: SenderItem, payload: Partial<SenderItem>) => {
    try {
      const updated = await apiClient.patchSender(item.id, payload)
      setItems((prev) => prev.map((x) => (x.id === item.id ? updated : x)))
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败')
    }
  }

  const remove = async (senderId: number) => {
    if (!window.confirm('确认删除该通道？')) return
    try {
      await apiClient.deleteSender(senderId)
      setItems((prev) => prev.filter((x) => x.id !== senderId))
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败')
    }
  }

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold">通道管理</h2>
        <button className="rounded border px-3 py-1.5 text-sm" onClick={() => void load()}>
          刷新
        </button>
      </div>
      {error && <p className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      <div className="mb-4 rounded border bg-slate-50 p-3">
        <h3 className="mb-2 text-sm font-semibold">新建通道</h3>
        <div className="grid gap-2 md:grid-cols-2">
          <input
            className="rounded border px-2 py-1"
            placeholder="名称"
            value={draft.name ?? ''}
            onChange={(e) => setDraft((prev) => ({ ...prev, name: e.target.value }))}
          />
          <input
            className="rounded border px-2 py-1"
            placeholder="类型数字（默认 4=Webhook）"
            value={draft.type ?? 4}
            onChange={(e) => setDraft((prev) => ({ ...prev, type: Number(e.target.value) || 4 }))}
          />
          <textarea
            className="rounded border px-2 py-1 md:col-span-2"
            placeholder="jsonSetting"
            rows={3}
            value={draft.jsonSetting ?? ''}
            onChange={(e) => setDraft((prev) => ({ ...prev, jsonSetting: e.target.value }))}
          />
        </div>
        <button className="mt-2 rounded bg-slate-900 px-3 py-1.5 text-sm text-white" onClick={() => void create()}>
          创建
        </button>
      </div>

      <div className="space-y-3">
        {items.map((item) => (
          <div key={item.id} className="rounded border p-3">
            <div className="mb-2 flex items-center justify-between">
              <div>
                <div className="font-medium">{item.name}</div>
                <div className="text-xs text-slate-500">{item.typeLabel} (#{item.type})</div>
              </div>
              <button className="rounded border border-red-200 px-2 py-1 text-xs text-red-600" onClick={() => void remove(item.id)}>
                删除
              </button>
            </div>
            <div className="grid gap-2 md:grid-cols-2">
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={item.status} onChange={(e) => void patch(item, { status: e.target.checked })} />
                启用
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={item.receiveCode} onChange={(e) => void patch(item, { receiveCode: e.target.checked })} />
                接收验证码
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={item.receiveNonCode} onChange={(e) => void patch(item, { receiveNonCode: e.target.checked })} />
                接收非验证码
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={item.receiveAppNotify} onChange={(e) => void patch(item, { receiveAppNotify: e.target.checked })} />
                接收应用通知
              </label>
            </div>
            <textarea
              className="mt-2 w-full rounded border px-2 py-1"
              rows={3}
              value={item.jsonSetting}
              onChange={(e) => {
                const value = e.target.value
                setItems((prev) => prev.map((x) => (x.id === item.id ? { ...x, jsonSetting: value } : x)))
              }}
              onBlur={() => void patch(item, { jsonSetting: item.jsonSetting })}
            />
          </div>
        ))}
      </div>
    </div>
  )
}
