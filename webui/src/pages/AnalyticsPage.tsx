import { useEffect, useMemo, useState } from 'react'
import { Button, Table, TableBody, TableCell, TableHead, TableHeadCell, TableRow, TabItem, Tabs } from 'flowbite-react'
import { apiClient } from '../api/client'
import type { AnalyticsResponse, AnalyticsWindow } from '../types'
import { trackEvent } from '../analytics'
import { ErrorBanner, LoadingCard, MetricCard, PageShell, SurfaceCard } from '../template'

const ranges = [
  { key: 'allTime', label: '全量' },
  { key: 'last7d', label: '近7天' },
  { key: 'last30d', label: '近30天' }
] as const

type RangeKey = (typeof ranges)[number]['key']

export function AnalyticsPage() {
  const [data, setData] = useState<AnalyticsResponse | null>(null)
  const [error, setError] = useState('')
  const [range, setRange] = useState<RangeKey>('allTime')

  const load = async () => {
    try {
      setError('')
      setData(await apiClient.getAnalytics())
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const window = useMemo<AnalyticsWindow | null>(() => {
    if (!data) return null
    return data[range]
  }, [data, range])

  const actions = (
    <Button
      color="alternative"
      size="sm"
      onClick={() => {
        trackEvent('refresh', { page: 'analytics' })
        void load()
      }}
    >
      刷新统计
    </Button>
  )

  if (!data && !error) {
    return (
      <PageShell
        title="统计"
        description="按时间范围查看验证码识别、自动输入和各通道发送表现。"
        badge="Analytics"
        actions={actions}
      >
        <LoadingCard title="正在加载统计" message="正在汇总全量、7 天和 30 天窗口数据。" />
      </PageShell>
    )
  }

  return (
    <PageShell
      title="统计"
      description="用统一窗口对比验证码识别、自动输入和通道发送表现。"
      badge="Analytics"
      actions={actions}
    >
      <ErrorBanner message={error} />
      <SurfaceCard title="时间窗口" subtitle="切换不同统计范围，卡片和通道表会同步更新。">
        <Tabs
          variant="underline"
          onActiveTabChange={(index) => setRange(ranges[index]?.key ?? 'allTime')}
        >
          {ranges.map((item) => (
            <TabItem key={item.key} active={range === item.key} title={item.label}>
              <div className="text-sm text-slate-500">已切换到 {item.label} 统计窗口。</div>
            </TabItem>
          ))}
        </Tabs>
      </SurfaceCard>
      {window && (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
            <MetricCard title="验证码识别" value={window.summary.smsCodeDetected} tone="info" />
            <MetricCard title="自动输入尝试" value={window.summary.autoInputAttempt} />
            <MetricCard title="自动输入成功" value={window.summary.autoInputSuccess} tone="success" />
            <MetricCard title="自动输入失败" value={window.summary.autoInputFail} tone="warning" />
            <MetricCard title="消息总量" value={window.summary.messageTotal} />
          </div>

          <SurfaceCard title="通道表现" subtitle="按通道类型查看配置、启用和发送结果。">
            <div className="overflow-x-auto">
              <Table hoverable>
                <TableHead>
                  <TableRow>
                    <TableHeadCell>通道类型</TableHeadCell>
                    <TableHeadCell>配置数</TableHeadCell>
                    <TableHeadCell>启用数</TableHeadCell>
                    <TableHeadCell>发送数</TableHeadCell>
                    <TableHeadCell>成功</TableHeadCell>
                    <TableHeadCell>失败</TableHeadCell>
                  </TableRow>
                </TableHead>
                <TableBody className="divide-y">
                {window.senderStats.map((stat) => (
                  <TableRow key={stat.senderType} className="bg-white">
                    <TableCell className="font-medium text-slate-900">{stat.senderTypeLabel}</TableCell>
                    <TableCell>{stat.configured}</TableCell>
                    <TableCell>{stat.enabled}</TableCell>
                    <TableCell>{stat.sent}</TableCell>
                    <TableCell>{stat.success}</TableCell>
                    <TableCell>{stat.failed}</TableCell>
                  </TableRow>
                ))}
                </TableBody>
              </Table>
            </div>
          </SurfaceCard>
        </>
      )}
    </PageShell>
  )
}
