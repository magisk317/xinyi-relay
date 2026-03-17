import { useEffect, useState } from 'react'
import { Badge, Button, TextInput, Textarea, ToggleSwitch } from 'flowbite-react'
import { apiClient } from '../api/client'
import type { SenderItem } from '../types'
import { trackEvent } from '../analytics'
import { EmptyCard, ErrorBanner, LoadingCard, PageShell, SurfaceCard } from '../template'

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
    <Button
      color="alternative"
      size="sm"
      onClick={() => {
        trackEvent('refresh', { page: 'senders' })
        void load()
      }}
    >
      刷新通道
    </Button>
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
          <TextInput
            placeholder="名称"
            value={draft.name ?? ''}
            onChange={(e) => setDraft((prev) => ({ ...prev, name: e.target.value }))}
          />
          <TextInput
            placeholder="类型数字（默认 4 = Webhook）"
            value={String(draft.type ?? 4)}
            onChange={(e) => setDraft((prev) => ({ ...prev, type: Number(e.target.value) || 4 }))}
          />
          <div className="md:col-span-2">
            <Textarea
              placeholder="jsonSetting"
              rows={4}
              value={draft.jsonSetting ?? ''}
              onChange={(e) => setDraft((prev) => ({ ...prev, jsonSetting: e.target.value }))}
            />
          </div>
        </div>
        <div className="mt-4">
          <Button color="info" onClick={() => void create()}>
            创建通道
          </Button>
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
                <div className="font-medium text-slate-950">{item.name}</div>
                <div className="mt-1 flex flex-wrap gap-2">
                  <Badge color="info">{item.typeLabel}</Badge>
                  <Badge color="gray">#{item.type}</Badge>
                </div>
              </div>
              <Button color="failure" outline size="xs" onClick={() => void remove(item.id)}>
                删除
              </Button>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <Toggle label="启用" checked={item.status} onChange={(value) => void patch(item, { status: value })} />
              <Toggle label="接收验证码" checked={item.receiveCode} onChange={(value) => void patch(item, { receiveCode: value })} />
              <Toggle label="接收非验证码" checked={item.receiveNonCode} onChange={(value) => void patch(item, { receiveNonCode: value })} />
              <Toggle label="接收应用通知" checked={item.receiveAppNotify} onChange={(value) => void patch(item, { receiveAppNotify: value })} />
            </div>
            <Textarea
              className="mt-4"
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

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return (
    <div className="flex items-center justify-between rounded-2xl border border-slate-200/80 bg-slate-50/80 px-4 py-3">
      <div className="text-sm font-medium text-slate-900">{label}</div>
      <ToggleSwitch checked={checked} onChange={onChange} />
    </div>
  )
}
