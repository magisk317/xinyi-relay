import { useEffect, useMemo, useState } from 'react'
import { SenderFieldEditor } from '../SenderFieldEditor'
import { cloneSnapshot } from '../configSnapshot'
import { useRealtimeFeed } from '../realtime'
import { trackEvent } from '../analytics'
import { translateSenderType, useI18n } from '../i18n'
import {
  buildSenderDraftJson,
  nextSenderId,
  normalizeSnapshotSender,
  resolveSenderJsonForTypeChange
} from '../senderDefaults'
import {
  ActionButton,
  EmptyCard,
  ErrorBanner,
  LoadingCard,
  MetricCard,
  PageShell,
  RelayBadge,
  RelaySelect,
  SurfaceCard,
  ToggleRow
} from '../template'
import type { SnapshotSender } from '../types'
import { useConfigSnapshotEditor } from '../useConfigSnapshotEditor'

const SENDER_TYPE_OPTIONS = [3, 4, 5, 9, 13, 7, 16, 11, 10, 0, 12, 1, 2, 6, 8, 14, 15]

export function SendersPage() {
  const { t, locale } = useI18n()
  const { connected, lastEvent } = useRealtimeFeed()
  const { config, root, loading, saving, error, setError, load, saveRoot } = useConfigSnapshotEditor()
  const [draft, setDraft] = useState(() => ({
    name: '',
    type: 4,
    jsonSetting: buildSenderDraftJson(4)
  }))

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

  const senderTypeOptions = useMemo(
    () =>
      SENDER_TYPE_OPTIONS.map((value) => ({
        value,
        label: translateSenderType(value, t)
      })),
    [t]
  )

  const senders = root?.senders ?? []
  const rules = root?.rules ?? []
  const enabledCount = senders.filter((item) => item.status === 1).length
  const appNotifyCount = senders.filter((item) => item.receiveAppNotify === 1).length

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'senders' })
          void load().catch(() => {})
        }}
      >
        {t('senders.refresh')}
      </ActionButton>
    </div>
  )

  async function persistSenders(nextSenders: SnapshotSender[], removedSenderId?: number) {
    if (!root) return
    const nextRoot = cloneSnapshot(root)
    nextRoot.senders = nextSenders.map(normalizeSnapshotSender)
    if (removedSenderId != null) {
      nextRoot.rules = (nextRoot.rules ?? []).filter((rule) => rule.senderId !== removedSenderId)
      nextRoot.notifyRoutes = (nextRoot.notifyRoutes ?? []).filter((route) => route.senderId !== removedSenderId)
      nextRoot.forwardFilters = (nextRoot.forwardFilters ?? []).filter((rule) => rule.senderId !== removedSenderId)
    }
    try {
      await saveRoot(nextRoot)
    } catch {
      // saveRoot already updates UI state and error.
    }
  }

  if (loading && !root && !error) {
    return (
      <PageShell title={t('senders.title')} description={t('senders.description')} badge={t('senders.title')} actions={actions}>
        <LoadingCard title={t('senders.loadingTitle')} message={t('senders.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('senders.title')} description={t('senders.remoteDescription')} badge={t('senders.title')} actions={actions}>
      <ErrorBanner message={error} />

      <div className="grid gap-4 md:grid-cols-3">
        <MetricCard title={t('senders.metric.total')} value={senders.length} helper={t('senders.metric.totalHelper')} />
        <MetricCard title={t('senders.metric.enabled')} value={enabledCount} tone="success" helper={t('senders.metric.enabledHelper')} />
        <MetricCard title={t('senders.metric.appNotify')} value={appNotifyCount} tone="info" helper={t('senders.metric.appNotifyHelper')} />
      </div>

      <SurfaceCard title={t('senders.newTitle')} subtitle={t('senders.newSubtitle')}>
        <div className="grid gap-3 md:grid-cols-2">
          <input
            className="relay-input"
            placeholder={t('senders.namePlaceholder')}
            value={draft.name}
            onChange={(event) => setDraft((prev) => ({ ...prev, name: event.target.value }))}
          />
          <RelaySelect
            value={String(draft.type)}
            options={senderTypeOptions.map((option) => ({
              value: String(option.value),
              label: option.label
            }))}
            onChange={(event) => {
              const nextType = Number(event)
              setDraft((prev) => {
                return {
                  ...prev,
                  type: nextType,
                  jsonSetting: resolveSenderJsonForTypeChange(prev.type, nextType, prev.jsonSetting)
                }
              })
            }}
          />
          <div className="md:col-span-2">
            <SenderFieldEditor
              type={draft.type}
              jsonSetting={draft.jsonSetting}
              locale={locale}
              onLiveChange={(jsonSetting) => setDraft((prev) => ({ ...prev, jsonSetting }))}
            />
          </div>
        </div>
        <div className="mt-4">
          <ActionButton
            tone="primary"
            disabled={saving}
            onClick={() => {
              if (!root) return
              if (!draft.name.trim()) {
                setError(t('common.nameRequired'))
                return
              }
              const nextSender: SnapshotSender = {
                id: nextSenderId(senders),
                type: draft.type,
                name: draft.name.trim(),
                jsonSetting: draft.jsonSetting,
                status: 1,
                receiveCode: 1,
                receiveNonCode: 1,
                receiveAppNotify: 1,
                receiveCallNotify: 0
              }
              void persistSenders([nextSender, ...senders]).then(() => {
                setDraft({
                  name: '',
                  type: draft.type,
                  jsonSetting: buildSenderDraftJson(draft.type)
                })
              })
            }}
          >
            {saving ? t('common.saving') : t('senders.create')}
          </ActionButton>
        </div>
      </SurfaceCard>

      {!senders.length ? (
        <EmptyCard title={t('senders.emptyTitle')} message={t('senders.emptyMessage')} />
      ) : (
        <div className="space-y-4">
          {senders.map((item) => {
            const relatedRuleCount = rules.filter((rule) => rule.senderId === item.id).length
            return (
              <SurfaceCard key={`${item.id}-${config?.revision ?? 0}`} className="bg-white/96">
                <div className="mb-4 flex items-start justify-between gap-3">
                  <div>
                    <div className="font-medium text-[#243115]">{item.name}</div>
                    <div className="mt-1 flex flex-wrap gap-2">
                      <RelayBadge tone="accent">{translateSenderType(item.type, t)}</RelayBadge>
                      <RelayBadge tone={item.status === 1 ? 'success' : 'warning'}>
                        {item.status === 1 ? t('common.enabled') : t('common.disabled')}
                      </RelayBadge>
                      <RelayBadge>{t('senders.linkedRules', { count: relatedRuleCount })}</RelayBadge>
                    </div>
                  </div>
                  <ActionButton
                    tone="danger"
                    className="px-3 py-2 text-xs"
                    onClick={() => {
                      if (!window.confirm(t('common.confirmDeleteSender'))) return
                      void persistSenders(senders.filter((sender) => sender.id !== item.id), item.id)
                    }}
                  >
                    {t('common.delete')}
                  </ActionButton>
                </div>

                <div className="grid gap-3 md:grid-cols-2">
                  <input
                    className="relay-input"
                    defaultValue={item.name}
                    onBlur={(event) => {
                      const value = event.target.value.trim()
                      if (value === item.name || !value) return
                      void persistSenders(senders.map((sender) => (sender.id === item.id ? { ...sender, name: value } : sender)))
                    }}
                  />
                  <RelaySelect
                    value={String(item.type)}
                    options={senderTypeOptions.map((option) => ({
                      value: String(option.value),
                      label: option.label
                    }))}
                    onChange={(event) => {
                      const value = Number(event)
                      void persistSenders(
                        senders.map((sender) =>
                          sender.id === item.id
                            ? {
                                ...sender,
                                type: value,
                                jsonSetting: resolveSenderJsonForTypeChange(sender.type, value, sender.jsonSetting)
                              }
                            : sender
                        )
                      )
                    }}
                  />
                </div>

                <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                  <ToggleRow
                    label={t('common.enabled')}
                    checked={item.status === 1}
                    onChange={(value) => {
                      void persistSenders(senders.map((sender) => (sender.id === item.id ? { ...sender, status: value ? 1 : 0 } : sender)))
                    }}
                  />
                  <ToggleRow
                    label={t('senders.receiveCode')}
                    checked={item.receiveCode === 1}
                    onChange={(value) => {
                      void persistSenders(senders.map((sender) => (sender.id === item.id ? { ...sender, receiveCode: value ? 1 : 0 } : sender)))
                    }}
                  />
                  <ToggleRow
                    label={t('senders.receivePlain')}
                    checked={item.receiveNonCode === 1}
                    onChange={(value) => {
                      void persistSenders(senders.map((sender) => (sender.id === item.id ? { ...sender, receiveNonCode: value ? 1 : 0 } : sender)))
                    }}
                  />
                  <ToggleRow
                    label={t('senders.receiveAppNotify')}
                    checked={item.receiveAppNotify === 1}
                    onChange={(value) => {
                      void persistSenders(senders.map((sender) => (sender.id === item.id ? { ...sender, receiveAppNotify: value ? 1 : 0 } : sender)))
                    }}
                  />
                </div>

                <div className="mt-4">
                  <SenderFieldEditor
                    type={item.type}
                    jsonSetting={item.jsonSetting}
                    locale={locale}
                    onCommit={(jsonSetting) => {
                      if (jsonSetting === item.jsonSetting) return
                      void persistSenders(
                        senders.map((sender) => (sender.id === item.id ? { ...sender, jsonSetting } : sender))
                      )
                    }}
                  />
                </div>
              </SurfaceCard>
            )
          })}
        </div>
      )}
    </PageShell>
  )
}
