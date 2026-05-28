import { useCallback, useEffect, useMemo, useState } from 'react'
import { desktopApi } from '../api/desktopApi'
import { useDesktopRealtimeRefresh } from '../hooks/useDesktopRealtimeRefresh'
import { useDesktopI18n } from '../i18n'
import type { DeviceItem, RecordItem } from '../../../shared/contracts/console'
import { DesktopSelect, EmptyState, Panel, Tag } from '../ui'

type RecordTab = 'sms_code' | 'sms_plain' | 'app_notify' | 'call'

const RECORD_REFRESH_EVENTS = [
  'records.ingested',
  'device.registered',
  'device.updated',
  'device.revoked',
  'device.heartbeat'
] as const

export function RecordsPage() {
  const { t } = useDesktopI18n()
  const [records, setRecords] = useState<RecordItem[]>([])
  const [devices, setDevices] = useState<DeviceItem[]>([])
  const [selectedDeviceId, setSelectedDeviceId] = useState<number | ''>('')
  const [selectedTab, setSelectedTab] = useState<RecordTab>('sms_code')
  const [loading, setLoading] = useState(true)
  const [copiedRecordId, setCopiedRecordId] = useState<number | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      const [recordsPayload, devicesPayload] = await Promise.all([
        desktopApi.getRecords(80, selectedDeviceId === '' ? undefined : selectedDeviceId),
        desktopApi.getDevices()
      ])
      setRecords(recordsPayload.records)
      setDevices(devicesPayload.devices)
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : t('error.loadRecords'))
    } finally {
      setLoading(false)
    }
  }, [selectedDeviceId, t])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  useDesktopRealtimeRefresh(() => {
    void load()
  }, RECORD_REFRESH_EVENTS)

  const deviceNames = useMemo(
    () => new Map(devices.map((device) => [device.id, device.displayName || device.deviceName])),
    [devices]
  )
  const filteredRecords = useMemo(
    () => records.filter((record) => record.recordType === selectedTab),
    [records, selectedTab]
  )
  const tabs: Array<{ id: RecordTab; label: string }> = [
    { id: 'sms_code', label: t('records.tab.code') },
    { id: 'sms_plain', label: t('records.tab.plain') },
    { id: 'app_notify', label: t('records.tab.app') },
    { id: 'call', label: t('records.tab.call') }
  ]

  return (
    <Panel
      title={t('records.title')}
      actions={
        <div className="button-row">
          <DesktopSelect
            value={selectedDeviceId}
            placeholder={t('common.allDevices')}
            options={devices.map((device) => ({
              value: device.id,
              label: device.displayName || device.deviceName
            }))}
            onChange={(value) => setSelectedDeviceId(value === '' ? '' : Number(value))}
          />
          <button type="button" className="ghost-button" onClick={() => void load()}>
            {t('common.refresh')}
          </button>
        </div>
      }
    >
      {error ? <div className="banner banner--danger">{t(error)}</div> : null}
      <div className="metrics-grid">
        <div className="metric-card">
          <div className="metric-label">{t('common.visibleRecords')}</div>
          <div className="metric-value">{records.length}</div>
        </div>
        <div className="metric-card">
          <div className="metric-label">{t('common.verificationCodes')}</div>
          <div className="metric-value">{records.filter((record) => record.smsCode).length}</div>
        </div>
      </div>

      <div className="classification-tabs">
        <div className="classification-head">
          <div className="callout-title">{t('records.classificationTitle')}</div>
        </div>
        <div className="button-row">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={`ghost-button${selectedTab === tab.id ? ' ghost-button--active' : ''}`}
              onClick={() => setSelectedTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {!records.length && !loading ? (
        <EmptyState title={t('records.emptyTitle')} />
      ) : !filteredRecords.length && !loading ? (
        <EmptyState title={t('records.emptyTabTitle')} />
      ) : (
        <div className="list-grid">
          {filteredRecords.map((record) => (
            <article key={record.id} className="list-card">
              <div className="list-card-head">
                <div>
                  <h3>{record.sender || record.packageName || t('common.record')}</h3>
                  <p>{deviceNames.get(record.deviceId) ?? t('records.deviceFallback').replace('{id}', String(record.deviceId))}</p>
                </div>
                <Tag tone={record.recordType === 'sms_code' ? 'success' : record.recordType === 'call' ? 'warning' : 'neutral'}>
                  {record.recordType}
                </Tag>
              </div>
              <div className="record-body">{record.body || t('common.emptyBodyValue')}</div>
              <div className="list-card-body">
                <div>{t('common.code')}: {record.smsCode || t('common.none')}</div>
                <div>{t('common.occurredAt')}: {new Date(record.occurredAt).toLocaleString()}</div>
              </div>
              {record.smsCode ? (
                <div className="button-row">
                  <button
                    type="button"
                    className="ghost-button"
                    onClick={() => {
                      void navigator.clipboard.writeText(record.smsCode).then(() => {
                        setCopiedRecordId(record.id)
                        window.setTimeout(() => {
                          setCopiedRecordId((current) => (current === record.id ? null : current))
                        }, 1500)
                      }).catch((nextError) => {
                        setError(nextError instanceof Error ? nextError.message : t('error.copyRecordCode'))
                      })
                    }}
                  >
                    {copiedRecordId === record.id ? t('common.copied') : t('common.copyCode')}
                  </button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}
    </Panel>
  )
}
