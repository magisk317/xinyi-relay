import { useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import type { OverviewState } from '../types'

export function OverviewPage() {
  const [data, setData] = useState<OverviewState | null>(null)
  const [error, setError] = useState('')

  const load = async () => {
    try {
      setError('')
      setData(await apiClient.getOverview())
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }

  useEffect(() => {
    void load()
  }, [])

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold">概览</h2>
        <button className="rounded border px-3 py-1.5 text-sm" onClick={() => void load()}>
          刷新
        </button>
      </div>
      {error && <p className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {data && (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard title="应用数" value={data.appCount} />
          <StatCard title="自动输入拦截" value={data.blockedCount} />
          <StatCard title="通知转发应用" value={data.forwardingCount} />
          <StatCard title="近期记录" value={data.recordCount} />
          <StatCard title="通道总数" value={data.senderTotal} />
          <StatCard title="启用通道" value={data.senderEnabled} />
          <StatCard title="应用通知通道" value={data.senderAppNotifyEnabled} />
          <StatCard title="当前版本" value={data.version.localVersionName} />
        </div>
      )}
    </div>
  )
}

function StatCard({ title, value }: { title: string; value: string | number }) {
  return (
    <div className="rounded-lg border bg-slate-50 p-3">
      <p className="text-xs text-slate-500">{title}</p>
      <p className="mt-1 text-xl font-semibold">{value}</p>
    </div>
  )
}
