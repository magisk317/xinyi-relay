import { useEffect, useState } from 'react'
import { Button } from 'flowbite-react'
import { apiClient } from '../api/client'
import type { OverviewState } from '../types'
import { trackEvent } from '../analytics'
import { ErrorBanner, LoadingCard, MetricCard, PageShell } from '../template'

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
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const actions = (
    <Button
      color="alternative"
      size="sm"
      onClick={() => {
        trackEvent('refresh', { page: 'overview' })
        void load()
      }}
    >
      刷新概览
    </Button>
  )

  if (!data && !error) {
    return (
      <PageShell
        title="概览"
        description="统一查看当前应用数量、通道启用状态和近期记录规模。"
        badge="Dashboard"
        actions={actions}
      >
        <LoadingCard title="正在加载概览" message="正在汇总应用、记录和通道状态。" />
      </PageShell>
    )
  }

  return (
    <PageShell
      title="概览"
      description="这是 WebUI 的总览页，适合快速确认当前规模、版本和通道覆盖面。"
      badge="Dashboard"
      actions={actions}
    >
      <ErrorBanner message={error} />
      {data && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard title="应用数" value={data.appCount} helper="已纳入控制台的应用总数" />
          <MetricCard title="自动输入拦截" value={data.blockedCount} tone="warning" helper="已开启验证码拦截的应用" />
          <MetricCard title="通知转发应用" value={data.forwardingCount} tone="info" helper="已配置通知转发的应用" />
          <MetricCard title="近期记录" value={data.recordCount} helper="最近一次汇总看到的记录条数" />
          <MetricCard title="通道总数" value={data.senderTotal} tone="info" helper="当前配置中的全部发送通道" />
          <MetricCard title="启用通道" value={data.senderEnabled} tone="success" helper="已处于启用状态的发送通道" />
          <MetricCard title="应用通知通道" value={data.senderAppNotifyEnabled} helper="可接收应用通知的通道" />
          <MetricCard title="当前版本" value={data.version.localVersionName} tone="success" helper={`版本号 ${data.version.localVersionCode}`} />
        </div>
      )}
    </PageShell>
  )
}
