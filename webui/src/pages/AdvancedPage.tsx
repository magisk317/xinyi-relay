import { useEffect, useState } from 'react'
import { Button, TabItem, Tabs, Textarea, ToggleSwitch } from 'flowbite-react'
import { apiClient } from '../api/client'
import type { AdvancedState, InterceptState } from '../types'
import { trackEvent } from '../analytics'
import { ErrorBanner, LoadingCard, PageShell, SurfaceCard } from '../template'

export function AdvancedPage() {
  const [advanced, setAdvanced] = useState<AdvancedState | null>(null)
  const [intercept, setIntercept] = useState<InterceptState | null>(null)
  const [error, setError] = useState('')

  const load = async () => {
    try {
      setError('')
      const [adv, itc] = await Promise.all([apiClient.getAdvanced(), apiClient.getIntercept()])
      setAdvanced(adv)
      setIntercept(itc)
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

  const actions = (
    <Button
      color="alternative"
      size="sm"
      onClick={() => {
        trackEvent('refresh', { page: 'advanced' })
        void load()
      }}
    >
      刷新高级配置
    </Button>
  )

  if (!advanced || !intercept) {
    return (
      <PageShell
        title="高级"
        description="集中查看短信黑名单、WebUI 局域网访问和拦截规则等高级能力。"
        badge="Advanced"
        actions={actions}
      >
        <LoadingCard title="正在加载高级配置" message="正在同步高级和拦截规则配置。" />
      </PageShell>
    )
  }

  return (
    <PageShell
      title="高级"
      description="适合集中处理局域网访问、短信黑名单和高级拦截规则。"
      badge="Advanced"
      actions={actions}
    >
      <ErrorBanner message={error} />

      <SurfaceCard title="配置分组" subtitle="在高级设置和拦截规则之间切换。">
        <Tabs variant="underline">
          <TabItem title="高级配置">
            <div className="grid gap-4 lg:grid-cols-2">
              <Toggle
                label="启用短信黑名单"
                checked={advanced.enableSmsBlacklist}
                onChange={(value) => void patchAdvanced({ enableSmsBlacklist: value })}
              />
              <Toggle
                label="允许 WebUI 局域网访问"
                checked={advanced.webUiLanAccess}
                onChange={(value) => void patchAdvanced({ webUiLanAccess: value })}
              />
              <div className="rounded-2xl border border-slate-200/80 bg-slate-50/80 px-4 py-3 text-sm text-slate-600">
                通道总数：{advanced.senderTotal}
              </div>
              <div className="rounded-2xl border border-slate-200/80 bg-slate-50/80 px-4 py-3 text-sm text-slate-600">
                启用通道：{advanced.senderEnabled}
              </div>
            </div>
          </TabItem>

          <TabItem title="拦截规则">
            <div className="space-y-4">
              <TextAreaField
                label="黑名单号码"
                value={intercept.smsBlacklistNumbers}
                onBlur={(value) => void patchIntercept({ smsBlacklistNumbers: value })}
              />
              <TextAreaField
                label="黑名单前缀"
                value={intercept.smsBlacklistPrefixes}
                onBlur={(value) => void patchIntercept({ smsBlacklistPrefixes: value })}
              />
              <TextAreaField
                label="黑名单正则"
                value={intercept.smsBlacklistRegex}
                onBlur={(value) => void patchIntercept({ smsBlacklistRegex: value })}
              />
              <TextAreaField
                label="黑名单内容"
                value={intercept.smsBlacklistContent}
                onBlur={(value) => void patchIntercept({ smsBlacklistContent: value })}
              />
              <div className="grid gap-4 lg:grid-cols-2">
                <Toggle
                  label="匹配后删除短信"
                  checked={intercept.smsBlacklistActionDelete}
                  onChange={(value) => void patchIntercept({ smsBlacklistActionDelete: value })}
                />
                <Toggle
                  label="匹配后阻断处理"
                  checked={intercept.smsBlacklistActionBlock}
                  onChange={(value) => void patchIntercept({ smsBlacklistActionBlock: value })}
                />
              </div>
            </div>
          </TabItem>
        </Tabs>
      </SurfaceCard>
    </PageShell>
  )
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-2xl border border-slate-200/80 bg-slate-50/80 px-4 py-3">
      <div className="text-sm font-medium text-slate-900">{label}</div>
      <ToggleSwitch checked={checked} onChange={onChange} />
    </div>
  )
}

function TextAreaField({ label, value, onBlur }: { label: string; value: string; onBlur: (value: string) => void }) {
  const [text, setText] = useState(value)
  useEffect(() => {
    setText(value)
  }, [value])

  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-slate-900">{label}</p>
      <Textarea
        rows={2}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onBlur={() => onBlur(text)}
      />
    </div>
  )
}
