import { useCallback, useEffect, useEffectEvent, useState } from 'react'
import { apiClient } from '../api/client'
import { useDeviceConfig } from '../deviceConfig'
import { useRealtimeFeed } from '../realtime'
import type { DeviceActionCommandItem } from '../types'
import { trackEvent } from '../analytics'
import { useI18n, type TranslationKey } from '../i18n'
import {
  DEVICE_ACTIONS,
  DEVICE_ACTION_PARAM_KEYS,
  DEVICE_ACTION_STATUSES,
  DEVICE_CONTROL_ACTIONS,
  INTERRUPTION_FILTERS,
  RINGER_MODES,
  type DeviceActionParams,
  deviceActionStatusTone,
  summarizeDeviceAction
} from '../../../shared/deviceActionCommands'
import {
  ActionButton,
  EmptyCard,
  ErrorBanner,
  LoadingCard,
  PageShell,
  RelayBadge,
  RelaySelect,
  SurfaceCard
} from '../template'

export function DeviceControlPage() {
  const { t } = useI18n()
  const { devices, selectedDeviceId, setSelectedDeviceId, refreshDevices } = useDeviceConfig()
  const [actions, setActions] = useState<DeviceActionCommandItem[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const { connected, lastEvent } = useRealtimeFeed()

  const load = useCallback(async () => {
    if (selectedDeviceId == null) {
      setActions([])
      setLoading(false)
      return
    }
    try {
      setLoading(true)
      setError('')
      const [response] = await Promise.all([apiClient.listDeviceActions(selectedDeviceId), refreshDevices()])
      setActions(response.actions)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [refreshDevices, selectedDeviceId, t])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  const handleRealtimeEvent = useEffectEvent((event: { type: string; data?: unknown }) => {
    if (event.type !== 'device.action.updated') return
    const deviceId = asNumber(asRecord(event.data)?.deviceId)
    if (deviceId != null && selectedDeviceId != null && deviceId !== selectedDeviceId) return
    queueMicrotask(() => {
      void load()
    })
  })

  useEffect(() => {
    if (!lastEvent) return
    handleRealtimeEvent(lastEvent)
  }, [lastEvent])

  const runAction = useCallback(
    async (action: string, params: DeviceActionParams) => {
      if (selectedDeviceId == null || submitting) return
      try {
        setSubmitting(true)
        setError('')
        const command = await apiClient.queueDeviceAction(selectedDeviceId, action, params)
        setActions((current) => [command, ...current.filter((item) => item.id !== command.id)])
        trackEvent('device_action_queue', { page: 'device-control', action })
      } catch (err) {
        setError(err instanceof Error ? err.message : t('common.createFailed'))
      } finally {
        setSubmitting(false)
      }
    },
    [selectedDeviceId, submitting, t]
  )

  const deviceOptions = devices.map((device) => ({
    value: device.id,
    label: device.displayName || device.deviceName,
    description: `${device.platform} / ${device.deviceModel || t('common.unknownModel')}`
  }))

  const headerActions = (
    <div className="flex flex-wrap items-center gap-3">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      <RelaySelect
        className="min-w-0 flex-1 sm:min-w-[16rem] sm:flex-none"
        value={selectedDeviceId ?? ''}
        options={deviceOptions}
        onChange={(value) => {
          if (value === '') return
          setSelectedDeviceId(Number(value))
        }}
      />
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'device-control' })
          void load()
        }}
      >
        {t('deviceControl.refresh')}
      </ActionButton>
    </div>
  )

  if (selectedDeviceId == null) {
    return (
      <PageShell
        title={t('deviceControl.title')}
        description={t('deviceControl.description')}
        badge={t('deviceControl.title')}
        actions={headerActions}
      >
        <EmptyCard title={t('deviceControl.noDevice')} message={t('deviceControl.noDeviceHint')} />
      </PageShell>
    )
  }

  return (
    <PageShell
      title={t('deviceControl.title')}
      description={t('deviceControl.description')}
      badge={t('deviceControl.title')}
      actions={headerActions}
    >
      <ErrorBanner message={error} />

      <SurfaceCard title={t('deviceControl.actionsTitle')} subtitle={t('deviceControl.actionsSubtitle')}>
        <div className="flex flex-wrap gap-3">
          {DEVICE_CONTROL_ACTIONS.map((item) => (
            <ActionButton
              key={deviceActionLabelKey(item.action, item.params)}
              disabled={submitting}
              onClick={() => {
                void runAction(item.action, item.params)
              }}
            >
              {t(deviceActionLabelKey(item.action, item.params))}
            </ActionButton>
          ))}
        </div>
      </SurfaceCard>

      <SurfaceCard title={t('deviceControl.historyTitle')} subtitle={t('deviceControl.historySubtitle')}>
        {loading && !actions.length ? (
          <LoadingCard title={t('deviceControl.historyTitle')} message={t('common.loadingMessage')} />
        ) : !actions.length ? (
          <EmptyCard title={t('deviceControl.historyEmpty')} message={t('deviceControl.historyEmptyMessage')} />
        ) : (
          <div className="space-y-4">
            {actions.map((item) => (
              <div
                key={item.id ?? `${item.action}-${item.createdAt}`}
                className="rounded-[24px] border border-[#d9e6b1] bg-[linear-gradient(180deg,#ffffff_0%,#f7fbe9_100%)] px-4 py-3.5 shadow-[0_12px_30px_-24px_rgba(98,122,28,0.2)]"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="text-sm font-medium text-[#243115]">{summarizeDeviceAction(item)}</div>
                  <RelayBadge tone={deviceActionStatusTone(item.status ?? '')}>
                    {deviceActionStatusLabel(item.status ?? '', t)}
                  </RelayBadge>
                </div>
                <div className="mt-1 text-xs text-[#73805d]">
                  {item.createdAt ? new Date(item.createdAt).toLocaleString() : ''}
                  {item.failureReason ? ` · ${t('deviceControl.failureHint', { reason: item.failureReason })}` : ''}
                </div>
              </div>
            ))}
          </div>
        )}
      </SurfaceCard>
    </PageShell>
  )
}

function deviceActionLabelKey(action: string, params: DeviceActionParams): TranslationKey {
  if (action === DEVICE_ACTIONS.SET_RINGER_MODE) {
    if (params[DEVICE_ACTION_PARAM_KEYS.MODE] === RINGER_MODES.SILENT) return 'deviceControl.action.silent'
    if (params[DEVICE_ACTION_PARAM_KEYS.MODE] === RINGER_MODES.VIBRATE) return 'deviceControl.action.vibrate'
    return 'deviceControl.action.ring'
  }
  if (action === DEVICE_ACTIONS.SET_INTERRUPTION_FILTER) {
    if (params[DEVICE_ACTION_PARAM_KEYS.FILTER] === INTERRUPTION_FILTERS.PRIORITY) return 'deviceControl.action.dnd'
    return 'deviceControl.action.allowAll'
  }
  return 'deviceControl.action.unknown'
}

function deviceActionStatusLabel(status: string, t: (key: TranslationKey) => string): string {
  if (status === DEVICE_ACTION_STATUSES.PENDING) return t('deviceControl.status.pending')
  if (status === DEVICE_ACTION_STATUSES.DONE) return t('deviceControl.status.done')
  if (status === DEVICE_ACTION_STATUSES.FAILED) return t('deviceControl.status.failed')
  return t('common.unknown')
}

function asNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null
}
