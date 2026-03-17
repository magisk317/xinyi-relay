import { useEffect, useState } from 'react'
import { Badge, Button } from 'flowbite-react'
import { apiClient } from '../api/client'
import type { SettingsState } from '../types'
import { trackEvent } from '../analytics'
import { ErrorBanner, LoadingCard, PageShell, SurfaceCard } from '../template'

export function SettingsPage() {
  const [data, setData] = useState<SettingsState | null>(null)
  const [error, setError] = useState('')

  const load = async () => {
    try {
      setError('')
      setData(await apiClient.getSettings())
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const update = async (patch: Partial<SettingsState>) => {
    if (!data) return
    try {
      const next = await apiClient.patchSettings(patch)
      setData(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败')
    }
  }

  const actions = (
    <Button
      color="alternative"
      size="sm"
      onClick={() => {
        trackEvent('refresh', { page: 'settings' })
        void load()
      }}
    >
      刷新设置
    </Button>
  )

  if (!data) {
    return (
      <PageShell
        title="设置"
        description="在这里控制模块总开关、验证码能力和转发行为。"
        badge="Settings"
        actions={actions}
      >
        <LoadingCard title="正在加载设置" message="正在同步当前运行时设置。" />
      </PageShell>
    )
  }

  return (
    <PageShell
      title="设置"
      description="按功能分区管理模块、验证码、通知和恢复行为，开关变更会立即生效。"
      badge="Settings"
      actions={actions}
    >
      <ErrorBanner message={error} />

      <div className="grid gap-4 xl:grid-cols-2">
        <SurfaceCard title="模块级开关" subtitle="决定整个 Relay 运行时是否继续处理事件。">
          <div className="space-y-3">
            <Toggle
              label="模块总开关"
              hint="关闭后将停止验证码处理和转发链路。"
              checked={data.moduleEnabled}
              onChange={(value) => void update({ moduleEnabled: value })}
            />
            <Toggle
              label="详细日志"
              hint="开启后会记录更多运行时细节，适合排障。"
              checked={data.verboseLogMode}
              onChange={(value) => void update({ verboseLogMode: value })}
            />
            <Toggle
              label="保活恢复"
              hint="用于应用被系统终止后的恢复流程。"
              checked={data.forceStopRecoveryEnabled}
              onChange={(value) => void update({ forceStopRecoveryEnabled: value })}
            />
          </div>
        </SurfaceCard>

        <SurfaceCard title="验证码能力" subtitle="控制验证码通知、复制和自动输入行为。">
          <div className="space-y-3">
            <Toggle
              label="验证码功能"
              checked={data.verificationFeaturesEnabled}
              onChange={(value) => void update({ verificationFeaturesEnabled: value })}
            />
            <Toggle
              label="复制验证码"
              checked={data.copyToClipboard}
              onChange={(value) => void update({ copyToClipboard: value })}
            />
            <Toggle
              label="验证码通知"
              checked={data.showCodeNotification}
              onChange={(value) => void update({ showCodeNotification: value })}
            />
            <Toggle
              label="自动输入"
              checked={data.enableAutoInputCode}
              onChange={(value) => void update({ enableAutoInputCode: value })}
            />
            <Toggle
              label="自动回车"
              checked={data.enableAutoEnterCode}
              onChange={(value) => void update({ enableAutoEnterCode: value })}
            />
          </div>
        </SurfaceCard>

        <SurfaceCard title="转发与提醒" subtitle="影响转发链和提示方式。">
          <div className="space-y-3">
            <Toggle
              label="转发功能"
              checked={data.relayFeaturesEnabled}
              onChange={(value) => void update({ relayFeaturesEnabled: value })}
            />
            <Toggle
              label="Toast 提示"
              checked={data.showToast}
              onChange={(value) => void update({ showToast: value })}
            />
            <Toggle
              label="短信黑名单"
              checked={data.smsBlacklistEnabled}
              onChange={(value) => void update({ smsBlacklistEnabled: value })}
            />
          </div>
        </SurfaceCard>

        <SurfaceCard title="当前状态" subtitle="用于快速确认几个关键能力是否已经开启。">
          <div className="flex flex-wrap gap-2">
            <Badge color={data.moduleEnabled ? 'success' : 'failure'}>模块 {data.moduleEnabled ? '已开启' : '已关闭'}</Badge>
            <Badge color={data.verificationFeaturesEnabled ? 'info' : 'failure'}>验证码 {data.verificationFeaturesEnabled ? '已开启' : '已关闭'}</Badge>
            <Badge color={data.relayFeaturesEnabled ? 'success' : 'failure'}>转发 {data.relayFeaturesEnabled ? '已开启' : '已关闭'}</Badge>
          </div>
        </SurfaceCard>
      </div>
    </PageShell>
  )
}

function Toggle({
  label,
  hint,
  checked,
  onChange
}: {
  label: string
  hint?: string
  checked: boolean
  onChange: (value: boolean) => void
}) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-2xl border border-slate-200/80 bg-slate-50/80 px-4 py-3">
      <div>
        <div className="text-sm font-medium text-slate-900">{label}</div>
        {hint && <div className="mt-1 text-xs leading-5 text-slate-500">{hint}</div>}
      </div>
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
    </div>
  )
}
