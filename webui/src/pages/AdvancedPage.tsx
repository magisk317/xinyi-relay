import * as Tabs from '@radix-ui/react-tabs'
import { useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import type { AdvancedState, InterceptState } from '../types'

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

  if (!advanced || !intercept) {
    return <p className="text-sm text-slate-500">加载中...</p>
  }

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold">高级</h2>
        <button className="rounded border px-3 py-1.5 text-sm" onClick={() => void load()}>
          刷新
        </button>
      </div>
      {error && <p className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      <Tabs.Root defaultValue="advanced">
        <Tabs.List className="mb-3 flex gap-2">
          <Tabs.Trigger
            className="rounded border px-3 py-1.5 text-sm data-[state=active]:bg-slate-900 data-[state=active]:text-white"
            value="advanced"
          >
            高级配置
          </Tabs.Trigger>
          <Tabs.Trigger
            className="rounded border px-3 py-1.5 text-sm data-[state=active]:bg-slate-900 data-[state=active]:text-white"
            value="intercept"
          >
            拦截规则
          </Tabs.Trigger>
        </Tabs.List>

        <Tabs.Content value="advanced" className="space-y-2">
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
          <p className="text-sm text-slate-500">通道总数：{advanced.senderTotal}</p>
          <p className="text-sm text-slate-500">启用通道：{advanced.senderEnabled}</p>
        </Tabs.Content>

        <Tabs.Content value="intercept" className="space-y-2">
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
        </Tabs.Content>
      </Tabs.Root>
    </div>
  )
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return (
    <label className="flex items-center justify-between rounded border px-3 py-2 text-sm">
      {label}
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
    </label>
  )
}

function TextAreaField({ label, value, onBlur }: { label: string; value: string; onBlur: (value: string) => void }) {
  const [text, setText] = useState(value)
  useEffect(() => {
    setText(value)
  }, [value])

  return (
    <div>
      <p className="mb-1 text-sm font-medium">{label}</p>
      <textarea
        className="w-full rounded border px-2 py-1"
        rows={2}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onBlur={() => onBlur(text)}
      />
    </div>
  )
}
