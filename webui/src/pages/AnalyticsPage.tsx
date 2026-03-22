import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '../api/client'
import type { AnalyticsResponse, AnalyticsWindow } from '../types'
import { trackEvent } from '../analytics'
import { ActionButton, ErrorBanner, LoadingCard, MetricCard, PageShell, SurfaceCard, cx } from '../template'

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
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'analytics' })
        void load()
      }}
    >
      刷新统计
    </ActionButton>
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
        <div className="flex flex-wrap gap-2">
          {ranges.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setRange(item.key)}
              className={cx(
                'rounded-full px-4 py-2.5 text-sm font-medium transition',
                range === item.key
                  ? 'bg-[linear-gradient(135deg,#87ad1e,#6f8e18)] text-[#243115] shadow-[0_20px_36px_-24px_rgba(111,142,24,0.44)]'
                  : 'bg-[#f7fbe8] text-[#5d6c40] ring-1 ring-[#d4e3a1] hover:bg-white'
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
        <div className="mt-4 text-sm text-[#6c785d]">已切换到 {ranges.find((item) => item.key === range)?.label ?? '全量'} 统计窗口。</div>
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
            <div className="relay-table-shell">
              <table className="relay-table">
                <thead>
                  <tr>
                    <th>通道类型</th>
                    <th>配置数</th>
                    <th>启用数</th>
                    <th>发送数</th>
                    <th>成功</th>
                    <th>失败</th>
                  </tr>
                </thead>
                <tbody>
                  {window.senderStats.map((stat) => (
                    <tr key={stat.senderType}>
                      <td data-strong="true">{stat.senderTypeLabel}</td>
                      <td>{stat.configured}</td>
                      <td>{stat.enabled}</td>
                      <td>{stat.sent}</td>
                      <td>{stat.success}</td>
                      <td>{stat.failed}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </SurfaceCard>
        </>
      )}
    </PageShell>
  )
}
