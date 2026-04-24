import { useCallback, useEffect, useEffectEvent, useMemo, useRef, useState } from 'react'
import { apiClient } from '../api/client'
import { useRealtimeFeed } from '../realtime'
import type { DeviceItem, RecordItem } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, EmptyCard, ErrorBanner, LoadingCard, PageShell, RelayBadge, RelaySelect, SurfaceCard, cx } from '../template'

type RecordTab = 'sms_code' | 'sms_plain' | 'app_notify' | 'call'
type ParsedRecordMetadata = {
  company?: string
  notifyChannelId?: string
  simSlot?: number
  subId?: number
  contactName?: string
  phoneArea?: string
}
type EnrichedRecord = RecordItem & {
  appLabel: string
  deviceLabel: string
  lineLabel: string
  simLabel: string
}

export function RecordsPage() {
  const { t } = useI18n()
  const [records, setRecords] = useState<RecordItem[]>([])
  const [devices, setDevices] = useState<DeviceItem[]>([])
  const [selectedDeviceId, setSelectedDeviceId] = useState<number | ''>('')
  const [selectedTab, setSelectedTab] = useState<RecordTab>('sms_code')
  const [selectedAppLabel, setSelectedAppLabel] = useState('')
  const [selectedPackageName, setSelectedPackageName] = useState('')
  const [selectedLineLabel, setSelectedLineLabel] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [copiedRecordId, setCopiedRecordId] = useState<number | null>(null)
  const { connected, lastEvent } = useRealtimeFeed()
  const copiedResetTimerRef = useRef<number | null>(null)

  const load = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      const [recordsResp, devicesResp] = await Promise.all([
        apiClient.getRecords(80, selectedDeviceId === '' ? undefined : selectedDeviceId),
        apiClient.getDevices()
      ])
      setRecords(recordsResp.records)
      setDevices(devicesResp.devices)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [selectedDeviceId, t])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  const handleRealtimeEvent = useEffectEvent((eventType: string) => {
    if (['records.ingested', 'device.heartbeat', 'device.revoked'].includes(eventType)) {
      void load()
    }
  })

  useEffect(() => {
    if (!lastEvent) return
    handleRealtimeEvent(lastEvent.type)
  }, [lastEvent])

  useEffect(() => {
    return () => {
      if (copiedResetTimerRef.current != null) {
        window.clearTimeout(copiedResetTimerRef.current)
      }
    }
  }, [])

  const tabItems: Array<{ id: RecordTab; label: string }> = [
    { id: 'sms_code', label: t('records.tab.code') },
    { id: 'sms_plain', label: t('records.tab.plain') },
    { id: 'app_notify', label: t('records.tab.app') },
    { id: 'call', label: t('records.tab.call') }
  ]

  const deviceNameById = useMemo(
    () =>
      new Map(
        devices.map((device) => [
          device.id,
          device.displayName || device.deviceName || t('common.unknown')
        ])
      ),
    [devices, t]
  )

  const enrichedRecords = useMemo<EnrichedRecord[]>(
    () =>
      records.map((item) => {
        const metadata = parseRecordMetadata(item.metadata)
        const appLabel = metadata.company?.trim() || ''
        const deviceLabel = deviceNameById.get(item.deviceId) ?? t('records.deviceBadge', { deviceId: item.deviceId })
        const lineLabel = resolveLineLabel(item, metadata)
        const simLabel = resolveSimLabel(metadata, t)
        return {
          ...item,
          appLabel,
          deviceLabel,
          lineLabel,
          simLabel
        }
      }),
    [deviceNameById, records, t]
  )

  const baseFilteredRecords = enrichedRecords.filter((item) => {
    if (selectedDeviceId !== '' && item.deviceId !== selectedDeviceId) return false
    if (selectedAppLabel && item.appLabel !== selectedAppLabel) return false
    if (selectedPackageName && item.packageName !== selectedPackageName) return false
    if (selectedLineLabel && item.lineLabel !== selectedLineLabel && item.simLabel !== selectedLineLabel) return false
    return true
  })

  const filteredRecords = baseFilteredRecords.filter((item) => item.recordType === selectedTab)

  const deviceOptions = [
    { value: '' as const, label: t('records.allDevices') },
    ...devices.map((device) => ({
      value: device.id,
      label: device.displayName || device.deviceName,
      description: `${device.platform} / ${device.deviceModel || t('common.unknownModel')}`
    }))
  ]

  const hasActiveFacetFilters =
    selectedDeviceId !== '' || selectedAppLabel || selectedPackageName || selectedLineLabel

  const copyCode = useCallback(async (recordId: number, smsCode: string) => {
    const text = smsCode.trim()
    if (!text) return
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text)
      } else {
        const textarea = document.createElement('textarea')
        textarea.value = text
        textarea.setAttribute('readonly', 'true')
        textarea.style.position = 'fixed'
        textarea.style.opacity = '0'
        document.body.appendChild(textarea)
        textarea.select()
        document.execCommand('copy')
        document.body.removeChild(textarea)
      }
      setCopiedRecordId(recordId)
      if (copiedResetTimerRef.current != null) {
        window.clearTimeout(copiedResetTimerRef.current)
      }
      copiedResetTimerRef.current = window.setTimeout(() => {
        setCopiedRecordId(null)
      }, 1600)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.saveFailed'))
    }
  }, [t])

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      <RelaySelect
        className="min-w-0 flex-1 sm:min-w-[16rem] sm:flex-none"
        value={selectedDeviceId}
        options={deviceOptions}
        onChange={(value) => {
          setSelectedDeviceId(value === '' ? '' : Number(value))
        }}
      />
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'records' })
          void load()
        }}
      >
        {t('records.refresh')}
      </ActionButton>
    </div>
  )

  if (loading && !records.length && !error) {
    return (
      <PageShell title={t('records.title')} description={t('records.description')} badge={t('records.title')} actions={actions}>
        <LoadingCard title={t('records.loadingTitle')} message={t('records.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('records.title')} description={t('records.remoteDescription')} badge={t('records.title')} actions={actions}>
      <ErrorBanner message={error} />

      <SurfaceCard title={t('records.classificationTitle')} subtitle={t('records.classificationSubtitle')}>
        <div className="flex flex-wrap gap-3">
          {tabItems.map((tab) => (
            <ActionButton
              key={tab.id}
              tone={selectedTab === tab.id ? 'primary' : 'neutral'}
              onClick={() => setSelectedTab(tab.id)}
            >
              {tab.label}
            </ActionButton>
          ))}
        </div>
      </SurfaceCard>

      {hasActiveFacetFilters ? (
        <SurfaceCard title={t('records.filtersTitle')}>
          <div className="flex flex-wrap items-center gap-3">
            {selectedAppLabel ? (
              <FilterChip label={t('records.appBadge', { name: selectedAppLabel })} onClear={() => setSelectedAppLabel('')} />
            ) : null}
            {selectedPackageName ? (
              <FilterChip label={selectedPackageName} onClear={() => setSelectedPackageName('')} />
            ) : null}
            {selectedDeviceId !== '' ? (
              <FilterChip
                label={deviceNameById.get(selectedDeviceId) ?? t('records.deviceBadge', { deviceId: selectedDeviceId })}
                onClear={() => setSelectedDeviceId('')}
              />
            ) : null}
            {selectedLineLabel ? (
              <FilterChip label={selectedLineLabel} onClear={() => setSelectedLineLabel('')} />
            ) : null}
            <ActionButton
              onClick={() => {
                setSelectedAppLabel('')
                setSelectedPackageName('')
                setSelectedDeviceId('')
                setSelectedLineLabel('')
              }}
            >
              {t('records.filtersClear')}
            </ActionButton>
          </div>
        </SurfaceCard>
      ) : null}

      {!records.length ? (
        <EmptyCard title={t('records.empty')} message={t('records.emptyMessage')} />
      ) : !filteredRecords.length ? (
        <EmptyCard
          title={t('records.emptyTab', { label: tabItems.find((tab) => tab.id === selectedTab)?.label ?? '' })}
          message={t('records.emptyTabMessage')}
        />
      ) : (
        <div className="space-y-4">
          {filteredRecords.map((item) => (
            <SurfaceCard key={item.id} className="bg-white/96">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="font-medium text-[#243115]">{item.sender || item.packageName || item.recordType}</div>
                    <RelayBadge tone={item.smsCode ? 'accent' : item.recordType === 'call' ? 'warning' : 'muted'}>
                      {selectedTab === 'sms_code'
                        ? t('records.badge.code')
                        : selectedTab === 'sms_plain'
                          ? t('records.badge.plain')
                          : selectedTab === 'app_notify'
                            ? t('records.badge.app')
                            : t('records.badge.call')}
                    </RelayBadge>
                  </div>
                  <div className="mt-1 text-xs text-[#73805d]">{new Date(item.occurredAt).toLocaleString()}</div>
                </div>
              </div>

              <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-[#465533]">{item.body || '-'}</p>

              <div className="mt-3 flex flex-wrap gap-2">
                {item.appLabel ? (
                  <button
                    type="button"
                    onClick={() => setSelectedAppLabel((current) => current === item.appLabel ? '' : item.appLabel)}
                    className={badgeButtonClass(selectedAppLabel === item.appLabel, 'muted')}
                  >
                    {t('records.appBadge', { name: item.appLabel })}
                  </button>
                ) : null}

                {item.smsCode ? (
                  <button
                    type="button"
                    title={t('settings.copyToClipboard')}
                    onClick={() => void copyCode(item.id, item.smsCode)}
                    className={cx(
                      'inline-flex items-center rounded-xl px-3 py-1.5 text-sm font-medium leading-none transition',
                      copiedRecordId === item.id
                        ? 'bg-[#dff4b8] text-[#4b6517]'
                        : 'bg-[#d8f0a5] text-[#476018] hover:bg-[#cce995]'
                    )}
                  >
                    {copiedRecordId === item.id ? t('records.codeCopied') : t('records.codeBadge', { code: item.smsCode })}
                  </button>
                ) : null}

                {item.packageName ? (
                  <button
                    type="button"
                    onClick={() => setSelectedPackageName((current) => current === item.packageName ? '' : item.packageName)}
                    className={badgeButtonClass(selectedPackageName === item.packageName, 'muted')}
                  >
                    {item.packageName}
                  </button>
                ) : null}

                <button
                  type="button"
                  onClick={() => setSelectedDeviceId((current) => current === item.deviceId ? '' : item.deviceId)}
                  className={badgeButtonClass(selectedDeviceId === item.deviceId, 'muted')}
                >
                  {item.deviceLabel}
                </button>

                {item.simLabel ? (
                  <button
                    type="button"
                    onClick={() => setSelectedLineLabel((current) => current === item.simLabel ? '' : item.simLabel)}
                    className={badgeButtonClass(selectedLineLabel === item.simLabel, 'warning')}
                  >
                    {item.simLabel}
                  </button>
                ) : null}

                {!item.simLabel && item.lineLabel && item.recordType !== 'app_notify' ? (
                  <button
                    type="button"
                    onClick={() => setSelectedLineLabel((current) => current === item.lineLabel ? '' : item.lineLabel)}
                    className={badgeButtonClass(selectedLineLabel === item.lineLabel, 'warning')}
                  >
                    {t('records.lineBadge', { value: item.lineLabel })}
                  </button>
                ) : null}
              </div>
            </SurfaceCard>
          ))}
        </div>
      )}
    </PageShell>
  )
}

function parseRecordMetadata(metadata: unknown): ParsedRecordMetadata {
  if (typeof metadata !== 'object' || metadata == null || Array.isArray(metadata)) {
    return {}
  }
  const record = metadata as Record<string, unknown>
  return {
    company: typeof record.company === 'string' ? record.company : undefined,
    notifyChannelId: typeof record.notifyChannelId === 'string' ? record.notifyChannelId : undefined,
    simSlot: typeof record.simSlot === 'number' ? record.simSlot : undefined,
    subId: typeof record.subId === 'number' ? record.subId : undefined,
    contactName: typeof record.contactName === 'string' ? record.contactName : undefined,
    phoneArea: typeof record.phoneArea === 'string' ? record.phoneArea : undefined
  }
}

function resolveLineLabel(item: RecordItem, metadata: ParsedRecordMetadata): string {
  if (item.recordType === 'app_notify') return ''
  if (item.recordType === 'sms_code' || item.recordType === 'sms_plain') return ''
  const sender = item.sender.trim()
  const contactName = metadata.contactName?.trim() ?? ''
  if (contactName && contactName !== sender) return contactName
  if (sender && sender !== metadata.company) return sender
  return ''
}

function resolveSimLabel(
  metadata: ParsedRecordMetadata,
  t: (key: string, params?: Record<string, string | number>) => string
): string {
  if (typeof metadata.simSlot === 'number' && metadata.simSlot >= 0) {
    return t('records.simBadge', { slot: metadata.simSlot + 1 })
  }
  return ''
}

function badgeButtonClass(active: boolean, tone: 'muted' | 'warning') {
  return cx(
    'inline-flex items-center rounded-xl px-3 py-1.5 text-sm font-medium leading-none transition',
    tone === 'warning'
      ? active
        ? 'bg-[#fff0d9] text-[#8a5518] ring-1 ring-[#e6c36f]'
        : 'bg-[#fff7de] text-[#9a6412] hover:bg-[#fff0d9]'
      : active
        ? 'bg-[#d8f0a5] text-[#476018] ring-1 ring-[#b8d86b]'
        : 'bg-[#eef4df] text-[#566347] hover:bg-[#e5efcf]'
  )
}

function FilterChip({ label, onClear }: { label: string; onClear: () => void }) {
  return (
    <button
      type="button"
      onClick={onClear}
      className="inline-flex items-center gap-2 rounded-full bg-[linear-gradient(135deg,#87ad1e,#6f8e18)] px-3 py-2 text-sm font-medium text-[#1f2a10] shadow-[0_18px_32px_-24px_rgba(111,142,24,0.48)]"
    >
      <span>{label}</span>
      <span>×</span>
    </button>
  )
}
