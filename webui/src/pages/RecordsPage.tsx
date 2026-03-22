import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '../api/client'
import type { RecordItem } from '../types'
import { trackEvent } from '../analytics'
import { ActionButton, EmptyCard, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard, cx } from '../template'

const recordTabs = [
  { key: 'code', label: '验证码' },
  { key: 'plain', label: '短信' },
  { key: 'app', label: '应用通知' },
  { key: 'call', label: '通话' }
] as const

type RecordTabKey = (typeof recordTabs)[number]['key']

export function RecordsPage() {
  const [records, setRecords] = useState<RecordItem[]>([])
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState<RecordTabKey>('code')

  const load = async () => {
    try {
      setError('')
      setRecords(await apiClient.getRecords())
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const deleteRecord = async (recordId: number) => {
    if (!window.confirm('确认删除该记录？')) return
    try {
      await apiClient.deleteRecord(recordId)
      setRecords((prev) => prev.filter((item) => item.id !== recordId))
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败')
    }
  }

  const actions = (
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'records' })
        void load()
      }}
    >
      刷新记录
    </ActionButton>
  )

  const filteredRecords = useMemo(
    () => records.filter((item) => recordTabKeyOf(item) === activeTab),
    [activeTab, records]
  )

  if (!records.length && !error) {
    return (
      <PageShell
        title="记录"
        description="查看最近转发和识别记录，支持直接删除历史项。"
        badge="Records"
        actions={actions}
      >
        <LoadingCard title="正在加载记录" message="正在读取最近 80 条记录。" />
      </PageShell>
    )
  }

  return (
    <PageShell
      title="记录"
      description="查看最近转发、验证码提取与来源信息，适合排查运行状态。"
      badge="Records"
      actions={actions}
    >
      <ErrorBanner message={error} />
      {!records.length ? (
        <EmptyCard title="暂无记录" message="当前还没有可展示的转发或识别记录。" />
      ) : (
        <div className="space-y-4">
          <SurfaceCard title="记录分类" subtitle="按和 app 一致的四类记录查看，避免短信、通知和通话混在一起。">
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
            <EmptyCard title={`暂无${recordTabs.find((item) => item.key === activeTab)?.label ?? '记录'}`} message="当前分类下还没有可展示的记录。" />
          ) : null}

          {filteredRecords.map((item) => (
            <SurfaceCard key={item.id} className="bg-white/96">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="font-medium text-[#243115]">{recordTitleOf(item)}</div>
                    <RelayBadge tone={recordBadgeToneOf(item)}>{recordLabelOf(item)}</RelayBadge>
                  </div>
                  <div className="mt-1 text-xs text-[#73805d]">{new Date(item.date).toLocaleString()}</div>
                </div>
                <ActionButton tone="danger" className="shrink-0 px-3 py-2 text-xs" onClick={() => void deleteRecord(item.id)}>
                  删除
                </ActionButton>
              </div>
              <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-[#465533]">{item.body || '-'}</p>
              <div className="mt-3 flex flex-wrap gap-2">
                {item.smsCode ? <RelayBadge tone="accent">验证码: {item.smsCode}</RelayBadge> : null}
                {item.packageName ? <RelayBadge>包名: {item.packageName}</RelayBadge> : null}
                <RelayBadge tone={forwardStatusTone(item.forwardStatus)}>{forwardStatusLabel(item.forwardStatus)}</RelayBadge>
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

function recordLabelOf(item: RecordItem): string {
  if (item.msgType === 1) return '应用通知'
  if (item.msgType === 2) return callTypeLabel(item.callType)
  return item.smsCode ? '验证码短信' : '普通短信'
}

function recordBadgeToneOf(item: RecordItem): 'accent' | 'muted' | 'warning' {
  if (item.msgType === 2) return 'warning'
  if (item.msgType === 1) return 'muted'
  return 'accent'
}

function recordTitleOf(item: RecordItem): string {
  return item.sender || item.packageName || (item.msgType === 2 ? '通话记录' : '未知来源')
}

function callTypeLabel(callType: number): string {
  switch (callType) {
    case 1:
      return '来电'
    case 2:
      return '去电'
    case 3:
      return '未接来电'
    case 7:
      return '异地接听'
    default:
      return '通话通知'
  }
}

function forwardStatusLabel(status: number): string {
  switch (status) {
    case 1:
      return '转发成功'
    case 2:
      return '转发失败'
    case 3:
      return '部分成功'
    case 4:
      return '已拦截'
    default:
      return '未转发'
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
