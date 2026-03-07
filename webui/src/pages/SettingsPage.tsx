import { useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import type { SettingsState } from '../types'

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
    void load()
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

  if (!data) {
    return <p className="text-sm text-slate-500">加载中...</p>
  }

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold">设置</h2>
        <button className="rounded border px-3 py-1.5 text-sm" onClick={() => void load()}>
          刷新
        </button>
      </div>
      {error && <p className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      <div className="grid gap-2">
        <Toggle label="总开关" checked={data.enable} onChange={(value) => void update({ enable: value })} />
        <Toggle
          label="复制验证码"
          checked={data.copyToClipboard}
          onChange={(value) => void update({ copyToClipboard: value })}
        />
        <Toggle label="Toast 提示" checked={data.showToast} onChange={(value) => void update({ showToast: value })} />
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
        <Toggle
          label="详细日志"
          checked={data.verboseLogMode}
          onChange={(value) => void update({ verboseLogMode: value })}
        />
        <Toggle label="拦截短信" checked={data.blockSms} onChange={(value) => void update({ blockSms: value })} />
        <Toggle
          label="保活恢复"
          checked={data.forceStopRecovery}
          onChange={(value) => void update({ forceStopRecovery: value })}
        />
      </div>
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
