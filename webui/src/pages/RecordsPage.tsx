import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '../api/client'
import type { RecordItem } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, EmptyCard, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard, cx } from '../template'

type RecordTabKey = 'code' | 'plain' | 'app' | 'call'

export function RecordsPage() {
  const { t } = useI18n()
  const [records, setRecords] = useState<RecordItem[]>([])
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState<RecordTabKey>('code')

  const recordTabs = [
    { key: 'code', label: t('records.tab.code') },
    { key: 'plain', label: t('records.tab.plain') },
    { key: 'app', label: t('records.tab.app') },
    { key: 'call', label: t('records.tab.call') }
  ] as const

  const load = async () => {
    try {
      setError('')
      setRecords(await apiClient.getRecords())
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const deleteRecord = async (recordId: number) => {
    if (!window.confirm(t('common.confirmDeleteRecord'))) return
    try {
      await apiClient.deleteRecord(recordId)
      setRecords((prev) => prev.filter((item) => item.id !== recordId))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.deleteFailed'))
    }
  }

  const actions = (
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'records' })
        void load()
      }}
    >
      {t('records.refresh')}
    </ActionButton>
  )

  const filteredRecords = useMemo(
    () => records.filter((item) => recordTabKeyOf(item) === activeTab),
    [activeTab, records]
  )

  if (!records.length && !error) {
    return (
      <PageShell title={t('records.title')} description={t('records.description')} badge="Records" actions={actions}>
        <LoadingCard title={t('records.loadingTitle')} message={t('records.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('records.title')} description={t('records.description')} badge="Records" actions={actions}>
      <ErrorBanner message={error} />
      {!records.length ? (
        <EmptyCard title={t('records.empty')} message={t('records.emptyMessage')} />
      ) : (
        <div className="space-y-4">
          <SurfaceCard title={t('records.classificationTitle')} subtitle={t('records.classificationSubtitle')}>
            <div className="flex flex-wrap gap-2">
              {recordTabs.map((tab) => {
                const count = records.filter((item) => recordTabKeyOf(item) === tab.key).length
                return (
                  <button
                    key={tab.key}
                    type="button"
                    onClick={() => setActiveTab(tab.key)}
                    className={cx(
                      'rounded-full px-4 py-2.5 text-sm font-medium transition',
                      activeTab === tab.key
                        ? 'bg-[linear-gradient(135deg,#87ad1e,#6f8e18)] text-[#243115] shadow-[0_20px_36px_-24px_rgba(111,142,24,0.44)]'
                        : 'bg-[#f7fbe8] text-[#5d6c40] ring-1 ring-[#d4e3a1] hover:bg-white'
                    )}
                  >
                    {tab.label} {count > 0 ? `(${count})` : ''}
                  </button>
                )
              })}
            </div>
          </SurfaceCard>

          {!filteredRecords.length ? (
            <EmptyCard
              title={t('records.emptyTab', { label: recordTabs.find((item) => item.key === activeTab)?.label ?? t('records.title') })}
              message={t('records.emptyTabMessage')}
            />
          ) : null}

          {filteredRecords.map((item) => (
            <SurfaceCard key={item.id} className="bg-white/96">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="font-medium text-[#243115]">{recordTitleOf(item, t)}</div>
                    <RelayBadge tone={recordBadgeToneOf(item)}>{recordLabelOf(item, t)}</RelayBadge>
                  </div>
                  <div className="mt-1 text-xs text-[#73805d]">{new Date(item.date).toLocaleString()}</div>
                </div>
                <ActionButton tone="danger" className="shrink-0 px-3 py-2 text-xs" onClick={() => void deleteRecord(item.id)}>
                  {t('common.delete')}
                </ActionButton>
              </div>
              <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-[#465533]">{item.body || '-'}</p>
              <div className="mt-3 flex flex-wrap gap-2">
                {item.smsCode ? <RelayBadge tone="accent">{t('records.smsCode', { code: item.smsCode })}</RelayBadge> : null}
                {item.packageName ? <RelayBadge>{t('records.package', { packageName: item.packageName })}</RelayBadge> : null}
                <RelayBadge tone={forwardStatusTone(item.forwardStatus)}>{forwardStatusLabel(item.forwardStatus, t)}</RelayBadge>
              </div>
            </SurfaceCard>
          ))}
        </div>
      )}
    </PageShell>
  )
}

function recordTabKeyOf(item: RecordItem): RecordTabKey {
  if (item.msgType === 1) return 'app'
  if (item.msgType === 2) return 'call'
  return item.smsCode ? 'code' : 'plain'
}

function recordLabelOf(item: RecordItem, t: ReturnType<typeof useI18n>['t']): string {
  if (item.msgType === 1) return t('records.badge.app')
  if (item.msgType === 2) return callTypeLabel(item.callType, t)
  return item.smsCode ? t('records.badge.code') : t('records.badge.plain')
}

function recordBadgeToneOf(item: RecordItem): 'accent' | 'muted' | 'warning' {
  if (item.msgType === 2) return 'warning'
  if (item.msgType === 1) return 'muted'
  return 'accent'
}

function recordTitleOf(item: RecordItem, t: ReturnType<typeof useI18n>['t']): string {
  return item.sender || item.packageName || (item.msgType === 2 ? t('records.title.call') : t('records.title.unknown'))
}

function callTypeLabel(callType: number, t: ReturnType<typeof useI18n>['t']): string {
  switch (callType) {
    case 1:
      return t('records.call.incoming')
    case 2:
      return t('records.call.outgoing')
    case 3:
      return t('records.call.missed')
    case 7:
      return t('records.call.external')
    default:
      return t('records.call.default')
  }
}

function forwardStatusLabel(status: number, t: ReturnType<typeof useI18n>['t']): string {
  switch (status) {
    case 1:
      return t('records.status.success')
    case 2:
      return t('records.status.failed')
    case 3:
      return t('records.status.partial')
    case 4:
      return t('records.status.blocked')
    default:
      return t('records.status.none')
  }
}

function forwardStatusTone(status: number): 'muted' | 'success' | 'danger' | 'warning' {
  switch (status) {
    case 1:
      return 'success'
    case 2:
      return 'danger'
    case 3:
    case 4:
      return 'warning'
    default:
      return 'muted'
  }
}
