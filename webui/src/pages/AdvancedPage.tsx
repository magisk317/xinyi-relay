import { useCallback, useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import { useRealtimeFeed } from '../realtime'
import type { BindCodeResponse, DeviceItem } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { QRCodeSVG } from 'qrcode.react'
import { ActionButton, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard } from '../template'

export function AdvancedPage() {
  const { t } = useI18n()
  const [devices, setDevices] = useState<DeviceItem[]>([])
  const [bindCode, setBindCode] = useState<BindCodeResponse | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const { connected, lastEvent } = useRealtimeFeed()
  const bindQrValue = bindCode
    ? `xinyi-relay://bind?code=${encodeURIComponent(bindCode.code)}&base_url=${encodeURIComponent(window.location.origin)}`
    : ''

  const load = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      const response = await apiClient.getDevices()
      setDevices(response.devices)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [t])

  const removeDeviceLocally = useCallback((deviceId: number) => {
    setDevices((current) => current.filter((device) => device.id !== deviceId))
  }, [])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  useEffect(() => {
    if (!lastEvent) return
    if (['device.registered', 'device.updated', 'device.heartbeat', 'device.revoked'].includes(lastEvent.type)) {
      void load()
    }
  }, [lastEvent, load])

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'advanced' })
          void load()
        }}
      >
        {t('advanced.refresh')}
      </ActionButton>
    </div>
  )

  if (loading && !devices.length && !error && !bindCode) {
    return (
      <PageShell title={t('advanced.title')} description={t('advanced.description')} badge={t('advanced.title')} actions={actions}>
        <LoadingCard title={t('advanced.loadingTitle')} message={t('advanced.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('advanced.title')} description={t('advanced.remoteDescription')} badge={t('advanced.title')} actions={actions}>
      <ErrorBanner message={error} />

      <SurfaceCard title={t('advanced.bindTitle')} subtitle={t('advanced.bindSubtitle')}>
        <div className="flex flex-wrap items-center gap-3">
          <ActionButton
            tone="primary"
            onClick={() => {
              void apiClient.createBindCode()
                .then((value) => setBindCode(value))
                .catch((err) => setError(err instanceof Error ? err.message : t('common.loadFailed')))
            }}
          >
            {t('advanced.bindGenerate')}
          </ActionButton>
          {bindCode && (
            <>
              <RelayBadge tone="accent">{bindCode.code}</RelayBadge>
              <RelayBadge>{new Date(bindCode.expiresAt).toLocaleString()}</RelayBadge>
            </>
          )}
        </div>
        {bindCode ? (
          <div className="mt-4 flex flex-col items-start gap-3">
            <div className="rounded-[24px] border border-[#d9e6b1] bg-white p-4 shadow-[0_12px_30px_-24px_rgba(98,122,28,0.2)]">
              <QRCodeSVG value={bindQrValue} size={180} includeMargin />
            </div>
            <div className="text-sm text-[#6c785d]">{t('advanced.bindQrHint')}</div>
          </div>
        ) : null}
      </SurfaceCard>

      <div className="space-y-4">
        {!devices.length ? (
          <SurfaceCard title={t('advanced.noAgentsTitle')} subtitle={t('advanced.noAgentsSubtitle')}>
            <div className="text-sm text-[#6c785d]">{t('advanced.noAgentsBody')}</div>
          </SurfaceCard>
        ) : devices.map((device) => (
            <SurfaceCard key={device.id} className="bg-white/96">
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-2">
                  <div className="text-lg font-semibold text-[#243115]">{device.displayName || device.deviceName}</div>
                  <div className="text-sm text-[#6c785d]">{device.platform} / {device.deviceModel || t('common.unknownModel')} / {device.appVersion || t('common.unknownVersion')}</div>
                  <div className="flex flex-wrap gap-2">
                    <RelayBadge tone={device.enabled ? 'success' : 'warning'}>
                      {device.enabled ? t('common.enabled') : t('common.disabled')}
                    </RelayBadge>
                    <RelayBadge>{device.lastSeenAt ? t('common.lastSeen', { time: new Date(device.lastSeenAt).toLocaleString() }) : t('common.neverSeen')}</RelayBadge>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <ActionButton
                    onClick={() => {
                      const nextName = window.prompt(t('advanced.deviceRenamePrompt'), device.displayName || device.deviceName)
                      if (nextName == null) return
                      void apiClient.patchDevice(device.id, { displayName: nextName })
                        .then(() => load())
                        .catch((err) => setError(err instanceof Error ? err.message : t('common.saveFailed')))
                    }}
                  >
                    {t('advanced.deviceRename')}
                  </ActionButton>
                  <ActionButton
                    tone={device.enabled ? 'warning' : 'primary'}
                    onClick={() => {
                      void apiClient.patchDevice(device.id, { enabled: !device.enabled })
                        .then((updated) => {
                          setDevices((current) => current.map((item) => (item.id === updated.id ? updated : item)))
                        })
                        .catch((err) => setError(err instanceof Error ? err.message : t('common.saveFailed')))
                    }}
                  >
                    {device.enabled ? t('advanced.deviceDisable') : t('advanced.deviceEnable')}
                  </ActionButton>
                  <ActionButton
                    tone="danger"
                    onClick={() => {
                      if (!window.confirm(t('advanced.deviceRevokeConfirm'))) return
                      void apiClient.revokeDevice(device.id)
                        .then(() => {
                          removeDeviceLocally(device.id)
                          return load()
                        })
                        .catch((err) => setError(err instanceof Error ? err.message : t('common.saveFailed')))
                    }}
                  >
                    {t('advanced.deviceRevoke')}
                  </ActionButton>
                </div>
              </div>
            </SurfaceCard>
          ))}
      </div>
    </PageShell>
  )
}
