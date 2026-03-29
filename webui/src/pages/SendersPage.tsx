import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '../api/client'
import type { SenderItem } from '../types'
import { trackEvent } from '../analytics'
import { translateSenderType, useI18n } from '../i18n'
import { ActionButton, EmptyCard, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard, ToggleRow } from '../template'

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
  const { t } = useI18n()
  const [items, setItems] = useState<SenderItem[]>([])
  const [draft, setDraft] = useState<Partial<SenderItem>>(emptySender)
  const [error, setError] = useState('')

  const senderTypeOptions = useMemo(
    () =>
      [3, 4, 5, 9, 13, 7, 16, 11, 10, 0, 12, 1, 2, 6, 8, 14, 15].map((value) => ({
        value,
        label: translateSenderType(value, t)
      })),
    [t]
  )

  const load = async () => {
    try {
      setError('')
      setItems(await apiClient.getSenders())
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const create = async () => {
    if (!draft.name?.trim()) {
      setError(t('common.nameRequired'))
      return
    }
    try {
      const created = await apiClient.createSender(draft)
      setItems((prev) => [created, ...prev])
      setDraft(emptySender)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.createFailed'))
    }
  }

  const patch = async (item: SenderItem, payload: Partial<SenderItem>) => {
    try {
      const updated = await apiClient.patchSender(item.id, payload)
      setItems((prev) => prev.map((x) => (x.id === item.id ? updated : x)))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.saveFailed'))
    }
  }

  const remove = async (senderId: number) => {
    if (!window.confirm(t('common.confirmDeleteSender'))) return
    try {
      await apiClient.deleteSender(senderId)
      setItems((prev) => prev.filter((x) => x.id !== senderId))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.deleteFailed'))
    }
  }

  const actions = (
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'senders' })
        void load()
      }}
    >
      {t('senders.refresh')}
    </ActionButton>
  )

  if (!items.length && !error) {
    return (
      <PageShell title={t('senders.title')} description={t('senders.description')} badge="Senders" actions={actions}>
        <LoadingCard title={t('senders.loadingTitle')} message={t('senders.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('senders.title')} description={t('senders.description')} badge="Senders" actions={actions}>
      <ErrorBanner message={error} />

      <SurfaceCard title={t('senders.newTitle')} subtitle={t('senders.newSubtitle')}>
        <div className="grid gap-3 md:grid-cols-2">
          <input
            className="relay-input"
            placeholder={t('senders.namePlaceholder')}
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
              placeholder={t('senders.jsonPlaceholder')}
              rows={4}
              value={draft.jsonSetting ?? ''}
              onChange={(e) => setDraft((prev) => ({ ...prev, jsonSetting: e.target.value }))}
            />
          </div>
        </div>
        <div className="mt-4">
          <ActionButton tone="primary" onClick={() => void create()}>
            {t('senders.create')}
          </ActionButton>
        </div>
      </SurfaceCard>

      {!items.length ? (
        <EmptyCard title={t('senders.emptyTitle')} message={t('senders.emptyMessage')} />
      ) : (
        <div className="space-y-4">
          {items.map((item) => (
            <SurfaceCard key={item.id} className="bg-white/96">
              <div className="mb-4 flex items-center justify-between gap-3">
                <div>
                  <div className="font-medium text-[#243115]">{item.name}</div>
                  <div className="mt-1 flex flex-wrap gap-2">
                    <RelayBadge tone="accent">{translateSenderType(item.type, t)}</RelayBadge>
                  </div>
                </div>
                <ActionButton tone="danger" className="px-3 py-2 text-xs" onClick={() => void remove(item.id)}>
                  {t('common.delete')}
                </ActionButton>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <ToggleRow label={t('common.enabled')} checked={item.status} onChange={(value) => void patch(item, { status: value })} />
                <ToggleRow label={t('senders.receiveCode')} checked={item.receiveCode} onChange={(value) => void patch(item, { receiveCode: value })} />
                <ToggleRow label={t('senders.receivePlain')} checked={item.receiveNonCode} onChange={(value) => void patch(item, { receiveNonCode: value })} />
                <ToggleRow label={t('senders.receiveAppNotify')} checked={item.receiveAppNotify} onChange={(value) => void patch(item, { receiveAppNotify: value })} />
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
