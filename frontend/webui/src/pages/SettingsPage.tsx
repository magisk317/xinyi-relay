import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../auth'
import { apiClient } from '../api/client'
import { useRealtimeFeed } from '../realtime'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard } from '../template'
import { useConfigSnapshotEditor } from '../useConfigSnapshotEditor'

export function SettingsPage() {
  const { t } = useI18n()
  const { username } = useAuth()
  const [draft, setDraft] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [notice, setNotice] = useState('')
  const { connected, lastEvent } = useRealtimeFeed()
  const { config, loading, saving, error, setError, load, saveRoot } = useConfigSnapshotEditor()

  useEffect(() => {
    queueMicrotask(() => {
      void load().then((next) => setDraft(JSON.stringify(next.snapshot, null, 2)))
    })
  }, [load])

  useEffect(() => {
    if (lastEvent?.type === 'config.updated') {
      void load().then((next) => setDraft(JSON.stringify(next.snapshot, null, 2)))
    }
  }, [lastEvent, load])

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      <ActionButton
        onClick={() => {
          trackEvent('refresh', { page: 'settings' })
          void load()
        }}
      >
              {t('settings.refresh')}
      </ActionButton>
    </div>
  )

  const parsed = useMemo(() => {
    try {
      return JSON.parse(draft) as Record<string, unknown>
    } catch {
      return null
    }
  }, [draft])

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
        <textarea
          className="relay-input min-h-[420px] w-full font-mono text-xs"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
        />
        <div className="mt-4 flex flex-wrap gap-3">
          <ActionButton
            tone="primary"
            disabled={!parsed || !config || saving}
            onClick={() => {
              if (!parsed || !config) return
              setNotice('')
              void saveRoot(parsed)
                .then((next) => {
                  setDraft(JSON.stringify(next.snapshot, null, 2))
                })
                .catch(() => {})
            }}
          >
            {saving ? t('common.saving') : t('settings.saveCloudSnapshot')}
          </ActionButton>
          <ActionButton onClick={() => setDraft(JSON.stringify(config?.snapshot ?? {}, null, 2))}>
            {t('settings.resetEditor')}
          </ActionButton>
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
