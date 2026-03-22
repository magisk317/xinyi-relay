import { useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import type { SenderItem } from '../types'
import { trackEvent } from '../analytics'
import { ActionButton, EmptyCard, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard, ToggleRow } from '../template'

const senderTypeOptions = [
  { value: 3, label: 'Webhook' },
  { value: 4, label: '企业微信机器人' },
  { value: 5, label: '企业微信应用' },
  { value: 9, label: '飞书' },
  { value: 13, label: '飞书应用' },
  { value: 7, label: 'Telegram' },
  { value: 16, label: 'ntfy' },
  { value: 11, label: 'Gotify' },
  { value: 10, label: 'PushPlus' },
  { value: 0, label: '钉钉群机器人' },
  { value: 12, label: '钉钉内部机器人' },
  { value: 1, label: '邮件' },
  { value: 2, label: 'Bark' },
  { value: 6, label: 'Server酱' },
  { value: 8, label: '短信' },
  { value: 14, label: 'URL Scheme' },
  { value: 15, label: 'Socket' }
]

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

  const actions = (
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'senders' })
        void load()
      }}
    >
      刷新通道
    </ActionButton>
  )

  if (!items.length && !error) {
    return (
      <PageShell
        title="通道管理"
        description="维护 Webhook 与其他发送通道的配置，并控制它们接收的消息类型。"
        badge="Senders"
        actions={actions}
      >
        <LoadingCard title="正在加载通道" message="正在读取当前已配置的发送通道。" />
      </PageShell>
    )
  }

  return (
    <PageShell
      title="通道管理"
      description="统一管理发送通道的名称、启用状态和接收范围，JSON 配置在失焦后自动保存。"
      badge="Senders"
      actions={actions}
    >
      <ErrorBanner message={error} />

      <SurfaceCard title="新建通道" subtitle="先填基础名称和类型，再补充 JSON 设置。">
        <div className="grid gap-3 md:grid-cols-2">
          <input
            className="relay-input"
            placeholder="名称"
            value={draft.name ?? ''}
            onChange={(e) => setDraft((prev) => ({ ...prev, name: e.target.value }))}
          />
          <select
            className="relay-input"
            value={String(draft.type ?? 4)}
            onChange={(e) => setDraft((prev) => ({ ...prev, type: Number(e.target.value) || 4 }))}
          >
            {senderTypeOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <div className="md:col-span-2">
            <textarea
              className="relay-input"
              placeholder="jsonSetting"
              rows={4}
              value={draft.jsonSetting ?? ''}
              onChange={(e) => setDraft((prev) => ({ ...prev, jsonSetting: e.target.value }))}
            />
          </div>
        </div>
        <div className="mt-4">
          <ActionButton tone="primary" onClick={() => void create()}>
            创建通道
          </ActionButton>
        </div>
      </SurfaceCard>

      {!items.length ? (
        <EmptyCard title="暂无通道" message="创建第一个通道后，这里会展示它的接收范围和配置。" />
      ) : (
        <div className="space-y-4">
        {items.map((item) => (
          <SurfaceCard key={item.id} className="bg-white/96">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <div className="font-medium text-[#243115]">{item.name}</div>
                <div className="mt-1 flex flex-wrap gap-2">
                  <RelayBadge tone="accent">{item.typeLabel}</RelayBadge>
                </div>
              </div>
              <ActionButton tone="danger" className="px-3 py-2 text-xs" onClick={() => void remove(item.id)}>
                删除
              </ActionButton>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <ToggleRow label="启用" checked={item.status} onChange={(value) => void patch(item, { status: value })} />
              <ToggleRow label="接收验证码" checked={item.receiveCode} onChange={(value) => void patch(item, { receiveCode: value })} />
              <ToggleRow label="接收非验证码" checked={item.receiveNonCode} onChange={(value) => void patch(item, { receiveNonCode: value })} />
              <ToggleRow label="接收应用通知" checked={item.receiveAppNotify} onChange={(value) => void patch(item, { receiveAppNotify: value })} />
            </div>
            <textarea
              className="relay-input mt-4"
              rows={3}
              value={item.jsonSetting}
              onChange={(e) => {
                const value = e.target.value
                setItems((prev) => prev.map((x) => (x.id === item.id ? { ...x, jsonSetting: value } : x)))
              }}
              onBlur={() => void patch(item, { jsonSetting: item.jsonSetting })}
            />
          </SurfaceCard>
        ))}
        </div>
      )}
    </PageShell>
  )
}
