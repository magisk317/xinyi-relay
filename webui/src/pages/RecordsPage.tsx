import { useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import type { RecordItem } from '../types'

export function RecordsPage() {
  const [records, setRecords] = useState<RecordItem[]>([])
  const [error, setError] = useState('')

  const load = async () => {
    try {
      setError('')
      setRecords(await apiClient.getRecords())
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const deleteRecord = async (recordId: number) => {
    if (!window.confirm('确认删除该记录？')) return
    try {
      await apiClient.deleteRecord(recordId)
      setRecords((prev) => prev.filter((item) => item.id !== recordId))
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败')
    }
  }

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold">记录</h2>
        <button className="rounded border px-3 py-1.5 text-sm" onClick={() => void load()}>
          刷新
        </button>
      </div>
      {error && <p className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      <div className="space-y-3">
        {records.map((item) => (
          <div key={item.id} className="rounded border bg-slate-50 p-3">
            <div className="flex items-center justify-between gap-2 max-md:flex-col max-md:items-start">
              <div>
                <div className="font-medium">{item.sender || item.packageName || '未知来源'}</div>
                <div className="text-xs text-slate-500">{new Date(item.date).toLocaleString()}</div>
              </div>
              <button className="rounded border border-red-200 px-2 py-1 text-xs text-red-600" onClick={() => void deleteRecord(item.id)}>
                删除
              </button>
            </div>
            <p className="mt-2 whitespace-pre-wrap text-sm">{item.body}</p>
            <div className="mt-2 flex flex-wrap gap-2 text-xs text-slate-500">
              <span className="rounded bg-white px-2 py-1">验证码: {item.smsCode || '-'}</span>
              <span className="rounded bg-white px-2 py-1">包名: {item.packageName || '-'}</span>
              <span className="rounded bg-white px-2 py-1">状态: {item.forwardStatus}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
