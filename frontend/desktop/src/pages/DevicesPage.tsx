import { useCallback, useEffect, useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { desktopApi } from '../api/desktopApi'
import { useDesktopDeviceConfig } from '../hooks/useDesktopDeviceConfig'
import { useDesktopRealtimeRefresh } from '../hooks/useDesktopRealtimeRefresh'
import { useDesktopI18n } from '../i18n'
import { useDesktop } from '../state/DesktopContext'
import type { BindCodeResponse } from '../../../shared/contracts/console'
import { EmptyState, Panel, Tag } from '../ui'

const DEVICE_REFRESH_EVENTS = [
  'device.registered',
  'device.updated',
  'device.revoked',
  'device.heartbeat'
] as const

export function DevicesPage() {
  const { activeProfile, runMode } = useDesktop()
  const { t } = useDesktopI18n()
  const { devices, refresh } = useDesktopDeviceConfig()
  const [bindCode, setBindCode] = useState<BindCodeResponse | null>(null)
  const [draftNames, setDraftNames] = useState<Record<number, string>>({})
  const [loading, setLoading] = useState(true)
  const [busyDeviceId, setBusyDeviceId] = useState<number | null>(null)
  const [pendingRevokeId, setPendingRevokeId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [localServerAddr, setLocalServerAddr] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      await refresh()
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : t('error.loadDevices'))
    } finally {
      setLoading(false)
    }
  }, [refresh, t])

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [load])

  useDesktopRealtimeRefresh(() => {
    void load()
  }, DEVICE_REFRESH_EVENTS)

  useEffect(() => {
    setDraftNames((previous) => {
      const next: Record<number, string> = {}
      for (const device of devices) {
        const fallbackName = device.displayName || device.deviceName
        next[device.id] = previous[device.id] ?? fallbackName
      }
      return next
    })
  }, [devices])

  useEffect(() => {
    if (runMode !== 'local') {
      setLocalServerAddr(null)
      return
    }
    void desktopApi.getLocalServerAddr()
      .then(setLocalServerAddr)
      .catch(() => setLocalServerAddr(null))
  }, [runMode])

  const bindBaseUrl = runMode === 'local'
    ? normalizeBindBaseUrl(localServerAddr ? `http://${localServerAddr}` : null)
    : normalizeBindBaseUrl(activeProfile?.baseUrl ?? null)
  const bindQrValue = bindCode && bindBaseUrl
    ? `xinyi-relay://bind?code=${encodeURIComponent(bindCode.code)}&base_url=${encodeURIComponent(bindBaseUrl)}`
    : ''

  return (
    <Panel
      title={t('devices.title')}
      actions={
        <div className="button-row">
          <button type="button" className="ghost-button" onClick={() => void load()}>
            {t('common.refresh')}
          </button>
          <button
            type="button"
            className="primary-button"
            onClick={() => {
              void Promise.all([
                desktopApi.createBindCode(),
                runMode === 'local' ? desktopApi.getLocalServerAddr() : Promise.resolve(null)
              ]).then(([nextBindCode, nextLocalServerAddr]) => {
                setBindCode(nextBindCode)
                if (runMode === 'local') setLocalServerAddr(nextLocalServerAddr)
              }).catch((nextError) => {
                setError(nextError instanceof Error ? nextError.message : t('error.createBindCode'))
              })
            }}
          >
            {t('devices.createBindCode')}
          </button>
        </div>
      }
    >
      {error ? <div className="banner banner--danger">{t(error)}</div> : null}
      <div className="metrics-grid">
        <div className="metric-card">
          <div className="metric-label">{t('devices.registered')}</div>
          <div className="metric-value">{devices.length}</div>
        </div>
        <div className="metric-card">
          <div className="metric-label">{t('devices.enabled')}</div>
          <div className="metric-value">{devices.filter((device) => device.enabled).length}</div>
        </div>
      </div>

      {bindCode ? (
        <div className="callout">
          <div className="callout-title">{t('common.latestBindCode')}</div>
          <div className="bind-code">{bindCode.code}</div>
          <div className="callout-meta">{new Date(bindCode.expiresAt).toLocaleString()}</div>
          {runMode === 'local' ? (
            <div className="callout-meta">{t('devices.localBindLoopbackOnly')}</div>
          ) : null}
          {bindQrValue ? (
            <div className="bind-qr-wrap">
              <div className="bind-qr-card">
                <QRCodeSVG value={bindQrValue} size={168} includeMargin />
              </div>
            </div>
          ) : null}
        </div>
      ) : null}

      {!devices.length && !loading ? (
        <EmptyState title={t('devices.emptyTitle')} />
      ) : (
        <div className="list-grid">
          {devices.map((device) => {
            const currentName = device.displayName || device.deviceName
            const draftName = draftNames[device.id] ?? currentName
            const nameChanged = draftName.trim() !== currentName
            const busy = busyDeviceId === device.id

            return (
              <article key={device.id} className="list-card list-card--editor">
                <div className="list-card-head">
                  <div>
                    <h3>{currentName}</h3>
                    <p>{device.platform} · {device.deviceModel || t('common.unknownModel')}</p>
                  </div>
                  <div className="button-row">
                    <Tag tone={device.revokedAt ? 'danger' : device.enabled ? 'success' : 'warning'}>
                      {device.revokedAt ? t('devices.revoked') : device.enabled ? t('profile.enabled') : t('devices.paused')}
                    </Tag>
                    <Tag tone="neutral">{device.lastSeenAt ? t('devices.heartbeatSeen') : t('common.noHeartbeatYet')}</Tag>
                  </div>
                </div>

                <div className="editor-grid">
                  <label className="field">
                    <span>{t('common.displayName')}</span>
                    <input
                      className="text-input"
                      value={draftName}
                      onChange={(event) => setDraftNames((previous) => ({ ...previous, [device.id]: event.target.value }))}
                    />
                  </label>
                  <div className="stack">
                    <div className="info-row">
                      <span>{t('common.appVersion')}</span>
                      <strong>{device.appVersion || '—'}</strong>
                    </div>
                    <div className="info-row">
                      <span>{t('common.lastSeen')}</span>
                      <strong>{device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString() : t('common.never')}</strong>
                    </div>
                  </div>
                </div>

                <div className="button-row">
                  <button
                    type="button"
                    className="ghost-button"
                    disabled={!nameChanged || busy}
                    onClick={() => {
                      setBusyDeviceId(device.id)
                      void desktopApi.patchDevice(device.id, { displayName: draftName.trim() }).then(() => {
                        setPendingRevokeId(null)
                        return load()
                      }).catch((nextError) => {
                        setError(nextError instanceof Error ? nextError.message : t('error.renameDevice'))
                      }).finally(() => {
                        setBusyDeviceId(null)
                      })
                    }}
                  >
                    {busy && nameChanged ? t('common.saving') : t('common.saveName')}
                  </button>
                  <button
                    type="button"
                    className="ghost-button"
                    disabled={busy}
                    onClick={() => {
                      setBusyDeviceId(device.id)
                      void desktopApi.patchDevice(device.id, { enabled: !device.enabled }).then(() => {
                        setPendingRevokeId(null)
                        return load()
                      }).catch((nextError) => {
                        setError(nextError instanceof Error ? nextError.message : t('error.updateDevice'))
                      }).finally(() => {
                        setBusyDeviceId(null)
                      })
                    }}
                  >
                    {busy && !nameChanged ? t('common.saving') : device.enabled ? t('common.pauseDevice') : t('common.enableDevice')}
                  </button>
                  {pendingRevokeId === device.id ? (
                    <>
                      <button
                        type="button"
                        className="danger-button"
                        disabled={busy}
                        onClick={() => {
                          setBusyDeviceId(device.id)
                          void desktopApi.revokeDevice(device.id).then(() => {
                            setPendingRevokeId(null)
                            return load()
                          }).catch((nextError) => {
                            setError(nextError instanceof Error ? nextError.message : t('error.revokeDevice'))
                          }).finally(() => {
                            setBusyDeviceId(null)
                          })
                        }}
                      >
                        {busy ? t('common.revoking') : t('common.confirmRevoke')}
                      </button>
                      <button
                        type="button"
                        className="ghost-button"
                        disabled={busy}
                        onClick={() => setPendingRevokeId(null)}
                      >
                        {t('common.cancel')}
                      </button>
                    </>
                  ) : (
                    <button
                      type="button"
                      className="danger-button"
                      disabled={busy || Boolean(device.revokedAt)}
                      onClick={() => setPendingRevokeId(device.id)}
                    >
                      {device.revokedAt ? t('common.alreadyRevoked') : t('common.revokeDevice')}
                    </button>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}
    </Panel>
  )
}

export function normalizeBindBaseUrl(value: string | null): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    return url.toString().replace(/\/$/, '')
  } catch {
    return null
  }
}
