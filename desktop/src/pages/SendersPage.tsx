import { useEffect, useState } from 'react'
import { SenderFieldEditor } from '../components/SenderFieldEditor'
import { SenderActiveScheduleEditor } from '../components/SenderActiveScheduleEditor'
import { cloneSnapshot } from '../configSnapshot'
import { useDesktopConfigSnapshotEditor } from '../hooks/useDesktopConfigSnapshotEditor'
import { translateSenderType, useDesktopI18n } from '../i18n'
import { useDesktopRealtimeRefresh } from '../hooks/useDesktopRealtimeRefresh'
import {
  buildSenderDraftJson,
  nextSenderId,
  normalizeSnapshotSender,
  resolveSenderJsonForTypeChange
} from '../../../shared/senderDefaults'
import { buildDefaultSenderActiveSchedule } from '../../../shared/senderActiveSchedule'
import type { SnapshotSender } from '../../../shared/contracts/console'
import { DesktopSelect, EmptyState, Metric, Panel, Tag } from '../ui'

const SENDER_TYPE_OPTIONS = [3, 4, 5, 9, 13, 7, 16, 11, 10, 0, 12, 1, 2, 6, 8, 14, 15]
const SENDER_REFRESH_EVENTS = ['config.updated'] as const

export function SendersPage() {
  const { locale, t } = useDesktopI18n()
  const { config, root, loading, saving, error, setError, load, saveRoot } = useDesktopConfigSnapshotEditor()
  const [draft, setDraft] = useState(() => ({
    name: '',
    type: 4,
    jsonSetting: buildSenderDraftJson(4),
    activeSchedule: buildDefaultSenderActiveSchedule()
  }))

  useEffect(() => {
    queueMicrotask(() => {
      void load().catch(() => {})
    })
  }, [load])

  useDesktopRealtimeRefresh(() => {
    void load().catch(() => {})
  }, SENDER_REFRESH_EVENTS)

  const senders = root?.senders ?? []
  const rules = root?.rules ?? []
  const enabledCount = senders.filter((item) => item.status === 1).length
  const appNotifyCount = senders.filter((item) => item.receiveAppNotify === 1).length

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
      // saveRoot updates page error state.
    }
  }

  return (
    <div className="page-grid">
      <Panel
        title={t('senders.title')}
        actions={
          <div className="button-row">
            <button type="button" className="ghost-button" onClick={() => void load().catch(() => {})}>
              {t('common.refresh')}
            </button>
            {config ? <Tag tone="neutral">{t('analytics.revisionLabel').replace('{revision}', String(config.revision))}</Tag> : null}
          </div>
        }
      >
        {error ? <div className="banner banner--danger">{error}</div> : null}
        <div className="metrics-grid">
          <Metric label={t('senders.total')} value={senders.length} />
          <Metric label={t('devices.enabled')} value={enabledCount} />
          <Metric label={t('analytics.appNotify')} value={appNotifyCount} />
        </div>
      </Panel>

      <Panel title={t('senders.createTitle')}>
        <div className="editor-grid">
          <label className="field">
            <span>{t('senders.name')}</span>
            <input className="text-input" value={draft.name} onChange={(event) => setDraft((previous) => ({ ...previous, name: event.target.value }))} />
          </label>
          <label className="field">
            <span>{t('senders.senderType')}</span>
            <DesktopSelect
              value={draft.type}
              options={SENDER_TYPE_OPTIONS.map((value) => ({
                value,
                label: translateSenderType(value, t)
              }))}
              onChange={(value) => {
                const nextType = Number(value)
                setDraft((previous) => ({
                  ...previous,
                  type: nextType,
                  jsonSetting: resolveSenderJsonForTypeChange(previous.type, nextType, previous.jsonSetting)
                }))
              }}
            />
          </label>
        </div>
        <SenderFieldEditor
          type={draft.type}
          jsonSetting={draft.jsonSetting}
          locale={locale}
          onLiveChange={(jsonSetting) => setDraft((previous) => ({ ...previous, jsonSetting }))}
        />
        <SenderActiveScheduleEditor
          schedule={draft.activeSchedule}
          locale={locale}
          onChange={(activeSchedule) => setDraft((previous) => ({ ...previous, activeSchedule }))}
        />
        <div className="button-row">
          <button
            type="button"
            className="primary-button"
            disabled={saving}
            onClick={() => {
              if (!root) return
              if (!draft.name.trim()) {
                setError(t('senders.nameRequired'))
                return
              }
              const nextSender: SnapshotSender = {
                id: nextSenderId(senders),
                type: draft.type,
                name: draft.name.trim(),
                jsonSetting: draft.jsonSetting,
                activeSchedule: draft.activeSchedule,
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
                  jsonSetting: buildSenderDraftJson(draft.type),
                  activeSchedule: buildDefaultSenderActiveSchedule()
                })
              })
            }}
          >
            {saving ? t('common.saving') : t('senders.createAction')}
          </button>
        </div>
      </Panel>

      <Panel title={t('senders.existingTitle')}>
        {!senders.length && !loading ? (
          <EmptyState title={t('senders.emptyTitle')} />
        ) : (
          <div className="list-grid">
            {senders.map((item) => {
              const relatedRuleCount = rules.filter((rule) => rule.senderId === item.id).length
              return (
                <article key={`${item.id}-${config?.revision ?? 0}`} className="list-card list-card--editor">
                  <div className="list-card-head">
                    <div>
                      <h3>{item.name}</h3>
                      <p>{translateSenderType(item.type, t)}</p>
                    </div>
                    <div className="button-row">
                      <Tag tone={item.status === 1 ? 'success' : 'warning'}>{item.status === 1 ? t('profile.enabled') : t('profile.disabled')}</Tag>
                      <Tag tone="neutral">{relatedRuleCount} {t('senders.linkedRules')}</Tag>
                    </div>
                  </div>

                  <div className="editor-grid">
                    <label className="field">
                      <span>{t('senders.name')}</span>
                      <input
                        className="text-input"
                        defaultValue={item.name}
                        onBlur={(event) => {
                          const value = event.target.value.trim()
                          if (!value || value === item.name) return
                          void persistSenders(
                            senders.map((sender) => (sender.id === item.id ? { ...sender, name: value } : sender))
                          )
                        }}
                      />
                    </label>
                    <label className="field">
                      <span>{t('senders.senderType')}</span>
                      <DesktopSelect
                        value={item.type}
                        options={SENDER_TYPE_OPTIONS.map((value) => ({
                          value,
                          label: translateSenderType(value, t)
                        }))}
                        onChange={(nextValue) => {
                          const value = Number(nextValue)
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
                    </label>
                  </div>

                  <div className="toggle-grid">
                    <ToggleChip label={t('devices.enabled')} checked={item.status === 1} onToggle={(checked) => {
                      void persistSenders(senders.map((sender) => (sender.id === item.id ? { ...sender, status: checked ? 1 : 0 } : sender)))
                    }} />
                    <ToggleChip label={t('senders.receiveCode')} checked={item.receiveCode === 1} onToggle={(checked) => {
                      void persistSenders(senders.map((sender) => (sender.id === item.id ? { ...sender, receiveCode: checked ? 1 : 0 } : sender)))
                    }} />
                    <ToggleChip label={t('senders.receivePlain')} checked={item.receiveNonCode === 1} onToggle={(checked) => {
                      void persistSenders(senders.map((sender) => (sender.id === item.id ? { ...sender, receiveNonCode: checked ? 1 : 0 } : sender)))
                    }} />
                    <ToggleChip label={t('senders.receiveAppNotify')} checked={item.receiveAppNotify === 1} onToggle={(checked) => {
                      void persistSenders(senders.map((sender) => (sender.id === item.id ? { ...sender, receiveAppNotify: checked ? 1 : 0 } : sender)))
                    }} />
                  </div>

                  <SenderActiveScheduleEditor
                    schedule={item.activeSchedule}
                    locale={locale}
                    onChange={(activeSchedule) => {
                      void persistSenders(
                        senders.map((sender) => (sender.id === item.id ? { ...sender, activeSchedule } : sender))
                      )
                    }}
                  />

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

                  <div className="button-row">
                    <button
                      type="button"
                      className="danger-button"
                      onClick={() => {
                        if (!window.confirm(`Delete sender "${item.name}"?`)) return
                        void persistSenders(senders.filter((sender) => sender.id !== item.id), item.id)
                      }}
                    >
                      {t('senders.deleteAction')}
                    </button>
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </Panel>
    </div>
  )
}

function ToggleChip({
  label,
  checked,
  onToggle
}: {
  label: string
  checked: boolean
  onToggle: (checked: boolean) => void
}) {
  return (
    <label className="toggle-chip">
      <input type="checkbox" checked={checked} onChange={(event) => onToggle(event.target.checked)} />
      <span>{label}</span>
    </label>
  )
}
