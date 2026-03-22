import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../api/client'
import type { AdvancedState, InterceptState, SettingsState } from '../types'
import { trackEvent } from '../analytics'
import { ActionButton, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard, ToggleRow, cx } from '../template'

type AdvancedSectionKey = 'relay' | 'alerts' | 'intercept' | 'webui'

export function AdvancedPage() {
  const [advanced, setAdvanced] = useState<AdvancedState | null>(null)
  const [intercept, setIntercept] = useState<InterceptState | null>(null)
  const [settings, setSettings] = useState<SettingsState | null>(null)
  const [error, setError] = useState('')
  const [activeSection, setActiveSection] = useState<AdvancedSectionKey>('relay')
  const navigate = useNavigate()

  const load = async () => {
    try {
      setError('')
      const [adv, itc, stg] = await Promise.all([apiClient.getAdvanced(), apiClient.getIntercept(), apiClient.getSettings()])
      setAdvanced(adv)
      setIntercept(itc)
      setSettings(stg)
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const patchAdvanced = async (patch: Partial<AdvancedState>) => {
    try {
      const next = await apiClient.patchAdvanced(patch)
      setAdvanced(next)
      setSettings((prev) => (prev ? { ...prev, smsBlacklistEnabled: next.enableSmsBlacklist } : prev))
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败')
    }
  }

  const patchIntercept = async (patch: Partial<InterceptState>) => {
    try {
      const next = await apiClient.patchIntercept(patch)
      setIntercept(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败')
    }
  }

  const patchSettings = async (patch: Partial<SettingsState>) => {
    try {
      const next = await apiClient.patchSettings(patch)
      setSettings(next)
      setAdvanced((prev) => (prev ? { ...prev, enableSmsBlacklist: next.smsBlacklistEnabled } : prev))
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败')
    }
  }

  const actions = (
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'advanced' })
        void load()
      }}
    >
      刷新高级配置
    </ActionButton>
  )

  if (!advanced || !intercept || !settings) {
    return (
      <PageShell
        title="高级"
        description="按主应用的高级入口方式组织转发配置、提醒、拦截和 WebUI 配置。"
        badge="Advanced"
        actions={actions}
      >
        <LoadingCard title="正在加载高级配置" message="正在同步高级入口、提醒、拦截和 WebUI 配置。" />
      </PageShell>
    )
  }

  return (
    <PageShell
      title="高级"
      description="和 app 一样，先按入口分组，再进入具体配置。"
      badge="Advanced"
      actions={actions}
    >
      <ErrorBanner message={error} />

      <SurfaceCard title="高级入口" subtitle="按主应用里的入口方式组织，不再把不同能力揉成一页。">
        <div className="grid gap-3 lg:grid-cols-2">
          <EntryCard
            title="转发配置"
            subtitle="通道、应用通知转发、过滤规则与记录设置"
            active={activeSection === 'relay'}
            onClick={() => setActiveSection('relay')}
          />
          <EntryCard
            title="特殊提醒"
            subtitle="Toast、状态栏通知与恢复提醒"
            active={activeSection === 'alerts'}
            onClick={() => setActiveSection('alerts')}
          />
          <EntryCard
            title="拦截与过滤"
            subtitle="短信黑名单、删除与阻断规则"
            active={activeSection === 'intercept'}
            onClick={() => setActiveSection('intercept')}
          />
          <EntryCard
            title="WebUI 配置"
            subtitle="局域网访问与当前 WebUI 服务能力"
            active={activeSection === 'webui'}
            onClick={() => setActiveSection('webui')}
          />
        </div>
      </SurfaceCard>

      {activeSection === 'relay' && (
        <SurfaceCard title="转发配置" subtitle="对应 app 里的转发配置入口，这里直接跳到相关工作区。">
          <div className="grid gap-3 md:grid-cols-3">
            <InfoTile label="通道总数" value={String(advanced.senderTotal)} />
            <InfoTile label="启用通道" value={String(advanced.senderEnabled)} />
            <InfoTile label="应用通知通道" value={String(advanced.senderAppNotifyEnabled)} />
          </div>
          <div className="mt-4 flex flex-wrap gap-3">
            <ActionButton tone="primary" onClick={() => navigate('/senders')}>
              打开通道管理
            </ActionButton>
            <ActionButton onClick={() => navigate('/apps')}>打开应用控制</ActionButton>
            <ActionButton onClick={() => navigate('/records')}>打开记录页面</ActionButton>
          </div>
        </SurfaceCard>
      )}

      {activeSection === 'alerts' && (
        <SurfaceCard title="特殊提醒" subtitle="把提醒相关开关收在一起，更接近 app 端的高级入口。">
          <div className="space-y-3">
            <ToggleRow
              label="Toast 提示"
              hint="处理成功或异常时显示本机提示。"
              checked={settings.showToast}
              onChange={(value) => void patchSettings({ showToast: value })}
            />
            <ToggleRow
              label="状态栏通知"
              hint="提取到验证码后显示状态栏通知。"
              checked={settings.showCodeNotification}
              onChange={(value) => void patchSettings({ showCodeNotification: value })}
            />
            <ToggleRow
              label="保活恢复"
              hint="用于应用被系统终止后的恢复流程。"
              checked={settings.forceStopRecoveryEnabled}
              onChange={(value) => void patchSettings({ forceStopRecoveryEnabled: value })}
            />
          </div>
        </SurfaceCard>
      )}

      {activeSection === 'intercept' && (
        <SurfaceCard title="拦截与过滤" subtitle="和 app 一样，先控制总开关，再编辑号码、号段、正则和内容规则。">
          <div className="space-y-4">
            <ToggleRow
              label="启用短信黑名单"
              hint="支持号码、号段、正则和内容匹配。"
              checked={advanced.enableSmsBlacklist}
              onChange={(value) => void patchAdvanced({ enableSmsBlacklist: value })}
            />
            <div className="grid gap-4 lg:grid-cols-2">
              <ToggleRow
                label="匹配后删除短信"
                checked={intercept.smsBlacklistActionDelete}
                onChange={(value) => void patchIntercept({ smsBlacklistActionDelete: value })}
              />
              <ToggleRow
                label="匹配后阻断处理"
                checked={intercept.smsBlacklistActionBlock}
                onChange={(value) => void patchIntercept({ smsBlacklistActionBlock: value })}
              />
            </div>
            <TextAreaField
              label="黑名单号码"
              value={intercept.smsBlacklistNumbers}
              hint="示例：10086, 10010"
              onBlur={(value) => void patchIntercept({ smsBlacklistNumbers: value })}
            />
            <TextAreaField
              label="黑名单前缀"
              value={intercept.smsBlacklistPrefixes}
              hint="示例：1069, 1065"
              onBlur={(value) => void patchIntercept({ smsBlacklistPrefixes: value })}
            />
            <TextAreaField
              label="黑名单正则"
              value={intercept.smsBlacklistRegex}
              hint="示例：(?i)\\b(退订|黑名单)\\b"
              onBlur={(value) => void patchIntercept({ smsBlacklistRegex: value })}
            />
            <TextAreaField
              label="黑名单内容"
              value={intercept.smsBlacklistContent}
              hint="示例：退订回T, 回复TD"
              onBlur={(value) => void patchIntercept({ smsBlacklistContent: value })}
            />
          </div>
        </SurfaceCard>
      )}

      {activeSection === 'webui' && (
        <SurfaceCard title="WebUI 配置" subtitle="保留 WebUI 自己的配置，不再和拦截规则混在一起。">
          <div className="space-y-4">
            <ToggleRow
              label="允许局域网访问"
              hint="开启后可使用当前局域网地址访问 WebUI。"
              checked={advanced.webUiLanAccess}
              onChange={(value) => void patchAdvanced({ webUiLanAccess: value })}
            />
            <div className="flex flex-wrap gap-2">
              <RelayBadge tone={advanced.webUiLanAccess ? 'success' : 'muted'}>
                {advanced.webUiLanAccess ? '已开启局域网访问' : '仅本机访问'}
              </RelayBadge>
              <RelayBadge tone="accent">默认用户名：relay</RelayBadge>
              <RelayBadge>HTTPS WebUI</RelayBadge>
            </div>
          </div>
        </SurfaceCard>
      )}
    </PageShell>
  )
}

function EntryCard({
  title,
  subtitle,
  active,
  onClick
}: {
  title: string
  subtitle: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        'flex w-full items-start justify-between gap-4 rounded-[24px] border px-4 py-4 text-left transition',
        active
          ? 'border-[#95bc29] bg-[linear-gradient(135deg,#eef8cb,#dcefab)] shadow-[0_18px_38px_-28px_rgba(111,142,24,0.42)]'
          : 'border-[#d6e5a6] bg-[linear-gradient(180deg,#ffffff_0%,#f7fbe9_100%)] hover:bg-white'
      )}
    >
      <div>
        <div className="text-base font-semibold text-[#243115]">{title}</div>
        <div className="mt-1 text-sm leading-6 text-[#6c785d]">{subtitle}</div>
      </div>
      <span className="pt-1 text-sm text-[#708b23]">→</span>
    </button>
  )
}

function InfoTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[22px] border border-[#d6e5a6] bg-[linear-gradient(180deg,#ffffff_0%,#f7fbe9_100%)] px-4 py-4">
      <div className="text-xs uppercase tracking-[0.18em] text-[#708b23]">{label}</div>
      <div className="mt-2 text-2xl font-semibold tracking-[-0.04em] text-[#243115]">{value}</div>
    </div>
  )
}

function TextAreaField({
  label,
  value,
  hint,
  onBlur
}: {
  label: string
  value: string
  hint?: string
  onBlur: (value: string) => void
}) {
  const [text, setText] = useState(value)
  useEffect(() => {
    setText(value)
  }, [value])

  return (
    <div className="space-y-2">
      <div>
        <p className="text-sm font-medium text-[#34461b]">{label}</p>
        {hint ? <p className="mt-1 text-xs leading-5 text-[#70805d]">{hint}</p> : null}
      </div>
      <textarea
        className="relay-input"
        rows={4}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onBlur={() => onBlur(text)}
      />
    </div>
  )
}
