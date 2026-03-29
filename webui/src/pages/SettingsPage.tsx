import { useEffect, useState } from 'react'
import { apiClient } from '../api/client'
import type { SettingsState } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard, ToggleRow } from '../template'

export function SettingsPage() {
  const { t } = useI18n()
  const [data, setData] = useState<SettingsState | null>(null)
  const [error, setError] = useState('')

  const load = async () => {
    try {
      setError('')
      setData(await apiClient.getSettings())
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const update = async (patch: Partial<SettingsState>) => {
    if (!data) return
    try {
      const next = await apiClient.patchSettings(patch)
      setData(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.saveFailed'))
    }
  }

  const actions = (
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'settings' })
        void load()
      }}
    >
      {t('settings.refresh')}
    </ActionButton>
  )

  if (!data) {
    return (
      <PageShell
        title={t('settings.title')}
        description={t('settings.description')}
        badge="Settings"
        actions={actions}
      >
        <LoadingCard title={t('settings.loadingTitle')} message={t('settings.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell
      title={t('settings.title')}
      description={t('settings.description')}
      badge="Settings"
      actions={actions}
    >
      <ErrorBanner message={error} />

      <div className="grid gap-4 xl:grid-cols-2">
        <SurfaceCard title={t('settings.section.module')} subtitle={t('settings.section.moduleSubtitle')}>
          <div className="space-y-3">
            <ToggleRow
              label={t('settings.moduleEnabled')}
              hint={t('settings.moduleEnabledHint')}
              checked={data.moduleEnabled}
              onChange={(value) => void update({ moduleEnabled: value })}
            />
            <ToggleRow
              label={t('settings.verboseLog')}
              hint={t('settings.verboseLogHint')}
              checked={data.verboseLogMode}
              onChange={(value) => void update({ verboseLogMode: value })}
            />
            <ToggleRow
              label={t('settings.recovery')}
              hint={t('settings.recoveryHint')}
              checked={data.forceStopRecoveryEnabled}
              onChange={(value) => void update({ forceStopRecoveryEnabled: value })}
            />
          </div>
        </SurfaceCard>

        <SurfaceCard title={t('settings.section.verification')} subtitle={t('settings.section.verificationSubtitle')}>
          <div className="space-y-3">
            <ToggleRow
              label={t('settings.verificationEnabled')}
              checked={data.verificationFeaturesEnabled}
              onChange={(value) => void update({ verificationFeaturesEnabled: value })}
            />
            <ToggleRow
              label={t('settings.copyToClipboard')}
              checked={data.copyToClipboard}
              onChange={(value) => void update({ copyToClipboard: value })}
            />
            <ToggleRow
              label={t('settings.blockSms')}
              hint={t('settings.blockSmsHint')}
              checked={data.blockSmsEnabled}
              onChange={(value) => void update({ blockSmsEnabled: value })}
            />
            <ToggleRow
              label={t('settings.autoInput')}
              checked={data.enableAutoInputCode}
              onChange={(value) => void update({ enableAutoInputCode: value })}
            />
            <ToggleRow
              label={t('settings.autoEnter')}
              checked={data.enableAutoEnterCode}
              onChange={(value) => void update({ enableAutoEnterCode: value })}
            />
          </div>
        </SurfaceCard>

        <SurfaceCard title={t('settings.section.relay')} subtitle={t('settings.section.relaySubtitle')}>
          <div className="space-y-3">
            <ToggleRow
              label={t('settings.relayEnabled')}
              checked={data.relayFeaturesEnabled}
              onChange={(value) => void update({ relayFeaturesEnabled: value })}
            />
            <ToggleRow
              label={t('settings.showToast')}
              checked={data.showToast}
              onChange={(value) => void update({ showToast: value })}
            />
            <ToggleRow
              label={t('settings.smsBlacklist')}
              checked={data.smsBlacklistEnabled}
              onChange={(value) => void update({ smsBlacklistEnabled: value })}
            />
          </div>
        </SurfaceCard>

        <SurfaceCard title={t('settings.section.state')} subtitle={t('settings.section.stateSubtitle')}>
          <div className="flex flex-wrap gap-2">
            <RelayBadge tone={data.moduleEnabled ? 'success' : 'danger'}>{t('settings.badge.module', { state: data.moduleEnabled ? t('common.enabled') : t('common.disabled') })}</RelayBadge>
            <RelayBadge tone={data.verificationFeaturesEnabled ? 'accent' : 'danger'}>{t('settings.badge.verification', { state: data.verificationFeaturesEnabled ? t('common.enabled') : t('common.disabled') })}</RelayBadge>
            <RelayBadge tone={data.relayFeaturesEnabled ? 'success' : 'danger'}>{t('settings.badge.relay', { state: data.relayFeaturesEnabled ? t('common.enabled') : t('common.disabled') })}</RelayBadge>
          </div>
        </SurfaceCard>
      </div>
    </PageShell>
  )
}
