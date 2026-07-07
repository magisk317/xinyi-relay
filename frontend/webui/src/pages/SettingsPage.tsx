import { useState } from 'react'
import { useAuth } from '../auth'
import { apiClient } from '../api/client'
import { useDeviceConfig } from '../deviceConfig'
import { useRealtimeFeed } from '../realtime'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard } from '../template'

export function SettingsPage() {
  const { t } = useI18n()
  const { username } = useAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [notice, setNotice] = useState('')
  const { connected } = useRealtimeFeed()
  const { config, devices, loading, saving, error, setError, refreshConfig, selectedDeviceId } = useDeviceConfig()

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'settings' })
          void refreshConfig()
        }}
      >
              {t('settings.refresh')}
      </ActionButton>
    </div>
  )

  if (loading && !config && !error) {
    return (
      <PageShell title={t('settings.title')} description={t('settings.description')} badge={t('settings.title')} actions={actions}>
        <LoadingCard title={t('settings.loadingTitle')} message={t('settings.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('settings.title')} description={t('settings.remoteDescription')} badge={t('settings.title')} actions={actions}>
      <ErrorBanner message={error} />
      {notice ? (
        <SurfaceCard className="border-[#d7e6a6] bg-[#f9fce9]">
          <div className="text-sm text-[#5f6f47]">{notice}</div>
        </SurfaceCard>
      ) : null}

      <SurfaceCard title={t('settings.cloudTitle')} subtitle={t('settings.cloudSubtitle', { revision: config?.revision ?? 0 })}>
        <div className="space-y-3 text-sm text-[#4f6038]">
          <div>
            {selectedDeviceId
              ? `${t('records.deviceBadge', { deviceId: selectedDeviceId })} · ${devices.find((device) => device.id === selectedDeviceId)?.deviceName ?? t('common.unknown')}`
              : t('common.none')}
          </div>
          <div>{config ? `pending commands: ${config.pendingCommands.length}` : t('common.none')}</div>
          <div>{config ? `updated: ${new Date(config.updatedAt).toLocaleString()}` : t('common.none')}</div>
          <div>{t('settings.remoteDescription')}</div>
        </div>
      </SurfaceCard>

      <SurfaceCard title={t('settings.accountTitle')} subtitle={`${t('settings.accountSubtitle')} (${username})`}>
        <div className="grid gap-3 md:grid-cols-2">
          <input
            className="relay-input"
            type="password"
            placeholder={t('settings.accountCurrentPassword')}
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
          <input
            className="relay-input"
            type="password"
            placeholder={t('settings.accountNewPassword')}
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
        </div>
        <div className="mt-4">
          <ActionButton
            tone="primary"
            disabled={saving || !currentPassword || !newPassword}
            onClick={() => {
              setNotice('')
              void apiClient.changePassword(currentPassword, newPassword)
                .then(() => {
                  setCurrentPassword('')
                  setNewPassword('')
                  setNotice(t('settings.accountPasswordUpdated'))
                })
                .catch((err) => {
                  setError(err instanceof Error ? err.message : t('common.saveFailed'))
                })
            }}
          >
            {t('settings.accountSavePassword')}
          </ActionButton>
        </div>
      </SurfaceCard>
    </PageShell>
  )
}
