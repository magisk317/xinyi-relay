import { useEffect, useState } from 'react'
import { Badge, Button } from 'flowbite-react'
import { apiClient } from '../api/client'
import type { RecordItem } from '../types'
import { trackEvent } from '../analytics'
import { EmptyCard, ErrorBanner, LoadingCard, PageShell, SurfaceCard } from '../template'

export function RecordsPage() {
  const [records, setRecords] = useState<RecordItem[]>([])
  const [error, setError] = useState('')

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
    <Button
      color="alternative"
      size="sm"
      onClick={() => {
        trackEvent('refresh', { page: 'records' })
        void load()
      }}
    >
      刷新记录
    </Button>
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
        {records.map((item) => (
          <SurfaceCard key={item.id} className="bg-white/96">
            <div className="flex items-center justify-between gap-2 max-md:flex-col max-md:items-start">
              <div>
                <div className="font-medium text-slate-900">{item.sender || item.packageName || '未知来源'}</div>
                <div className="text-xs text-slate-500">{new Date(item.date).toLocaleString()}</div>
              </div>
              <Button color="failure" outline size="xs" onClick={() => void deleteRecord(item.id)}>
                删除
              </Button>
            </div>
            <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700">{item.body}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Badge color="info">验证码: {item.smsCode || '-'}</Badge>
              <Badge color="gray">包名: {item.packageName || '-'}</Badge>
              <Badge color="success">状态: {item.forwardStatus}</Badge>
            </div>
          </SurfaceCard>
        ))}
        </div>
      )}
    </PageShell>
  )
}
