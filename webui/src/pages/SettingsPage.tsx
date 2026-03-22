import { useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import type { SettingsState } from '../types'
import { trackEvent } from '../analytics'
import { ActionButton, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard, ToggleRow } from '../template'

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
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'settings' })
        void load()
      }}
    >
      刷新设置
    </ActionButton>
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
            <ToggleRow
              label="模块总开关"
              hint="关闭后将停止验证码处理和转发链路。"
              checked={data.moduleEnabled}
              onChange={(value) => void update({ moduleEnabled: value })}
            />
            <ToggleRow
              label="详细日志"
              hint="开启后会记录更多运行时细节，适合排障。"
              checked={data.verboseLogMode}
              onChange={(value) => void update({ verboseLogMode: value })}
            />
            <ToggleRow
              label="保活恢复"
              hint="用于应用被系统终止后的恢复流程。"
              checked={data.forceStopRecoveryEnabled}
              onChange={(value) => void update({ forceStopRecoveryEnabled: value })}
            />
          </div>
        </SurfaceCard>

        <SurfaceCard title="验证码能力" subtitle="控制验证码通知、复制和自动输入行为。">
          <div className="space-y-3">
            <ToggleRow
              label="验证码功能"
              checked={data.verificationFeaturesEnabled}
              onChange={(value) => void update({ verificationFeaturesEnabled: value })}
            />
            <ToggleRow
              label="复制验证码"
              checked={data.copyToClipboard}
              onChange={(value) => void update({ copyToClipboard: value })}
            />
            <ToggleRow
              label="拦截验证码短信"
              hint="警告！提取成功后短信应用将无法收到"
              checked={data.blockSmsEnabled}
              onChange={(value) => void update({ blockSmsEnabled: value })}
            />
            <ToggleRow
              label="自动输入"
              checked={data.enableAutoInputCode}
              onChange={(value) => void update({ enableAutoInputCode: value })}
            />
            <ToggleRow
              label="自动回车"
              checked={data.enableAutoEnterCode}
              onChange={(value) => void update({ enableAutoEnterCode: value })}
            />
          </div>
        </SurfaceCard>

        <SurfaceCard title="转发与提醒" subtitle="影响转发链和提示方式。">
          <div className="space-y-3">
            <ToggleRow
              label="转发功能"
              checked={data.relayFeaturesEnabled}
              onChange={(value) => void update({ relayFeaturesEnabled: value })}
            />
            <ToggleRow
              label="Toast 提示"
              checked={data.showToast}
              onChange={(value) => void update({ showToast: value })}
            />
            <ToggleRow
              label="短信黑名单"
              checked={data.smsBlacklistEnabled}
              onChange={(value) => void update({ smsBlacklistEnabled: value })}
            />
          </div>
        </SurfaceCard>

        <SurfaceCard title="当前状态" subtitle="用于快速确认几个关键能力是否已经开启。">
          <div className="flex flex-wrap gap-2">
            <RelayBadge tone={data.moduleEnabled ? 'success' : 'danger'}>模块 {data.moduleEnabled ? '已开启' : '已关闭'}</RelayBadge>
            <RelayBadge tone={data.verificationFeaturesEnabled ? 'accent' : 'danger'}>验证码 {data.verificationFeaturesEnabled ? '已开启' : '已关闭'}</RelayBadge>
            <RelayBadge tone={data.relayFeaturesEnabled ? 'success' : 'danger'}>转发 {data.relayFeaturesEnabled ? '已开启' : '已关闭'}</RelayBadge>
          </div>
        </SurfaceCard>
      </div>
    </PageShell>
  )
}
