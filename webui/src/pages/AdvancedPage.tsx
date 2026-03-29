import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../api/client'
import type { AdvancedState, InterceptState, SettingsState } from '../types'
import { trackEvent } from '../analytics'
import { useI18n } from '../i18n'
import { ActionButton, ErrorBanner, LoadingCard, PageShell, RelayBadge, SurfaceCard, ToggleRow, cx } from '../template'

type AdvancedSectionKey = 'relay' | 'alerts' | 'intercept' | 'webui'

export function AdvancedPage() {
  const { t } = useI18n()
  const [advanced, setAdvanced] = useState<AdvancedState | null>(null)
  const [intercept, setIntercept] = useState<InterceptState | null>(null)
  const [settings, setSettings] = useState<SettingsState | null>(null)
  const [error, setError] = useState('')
  const [activeSection, setActiveSection] = useState<AdvancedSectionKey>('relay')
  const navigate = useNavigate()

  const load = async () => {
    try {
      setError('')
      const [adv, itc, stg] = await Promise.all([apiClient.getAdvanced(), apiClient.getIntercept(), apiClient.getSettings()])
      setAdvanced(adv)
      setIntercept(itc)
      setSettings(stg)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.loadFailed'))
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      void load()
    })
  }, [])

  const patchAdvanced = async (patch: Partial<AdvancedState>) => {
    try {
      const next = await apiClient.patchAdvanced(patch)
      setAdvanced(next)
      setSettings((prev) => (prev ? { ...prev, smsBlacklistEnabled: next.enableSmsBlacklist } : prev))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.saveFailed'))
    }
  }

  const patchIntercept = async (patch: Partial<InterceptState>) => {
    try {
      const next = await apiClient.patchIntercept(patch)
      setIntercept(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.saveFailed'))
    }
  }

  const patchSettings = async (patch: Partial<SettingsState>) => {
    try {
      const next = await apiClient.patchSettings(patch)
      setSettings(next)
      setAdvanced((prev) => (prev ? { ...prev, enableSmsBlacklist: next.smsBlacklistEnabled } : prev))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.saveFailed'))
    }
  }

  const actions = (
    <ActionButton
      onClick={() => {
        trackEvent('refresh', { page: 'advanced' })
        void load()
      }}
    >
      {t('advanced.refresh')}
    </ActionButton>
  )

  if (!advanced || !intercept || !settings) {
    return (
      <PageShell title={t('advanced.title')} description={t('advanced.description')} badge="Advanced" actions={actions}>
        <LoadingCard title={t('advanced.loadingTitle')} message={t('advanced.loadingMessage')} />
      </PageShell>
    )
  }

  return (
    <PageShell title={t('advanced.title')} description={t('advanced.description')} badge="Advanced" actions={actions}>
      <ErrorBanner message={error} />

      <SurfaceCard title={t('advanced.entryTitle')} subtitle={t('advanced.entrySubtitle')}>
        <div className="grid gap-3 lg:grid-cols-2">
          <EntryCard
            title={t('advanced.entry.relayTitle')}
            subtitle={t('advanced.entry.relaySubtitle')}
            active={activeSection === 'relay'}
            onClick={() => setActiveSection('relay')}
          />
          <EntryCard
            title={t('advanced.entry.alertsTitle')}
            subtitle={t('advanced.entry.alertsSubtitle')}
            active={activeSection === 'alerts'}
            onClick={() => setActiveSection('alerts')}
          />
          <EntryCard
            title={t('advanced.entry.interceptTitle')}
            subtitle={t('advanced.entry.interceptSubtitle')}
            active={activeSection === 'intercept'}
            onClick={() => setActiveSection('intercept')}
          />
          <EntryCard
            title={t('advanced.entry.webuiTitle')}
            subtitle={t('advanced.entry.webuiSubtitle')}
            active={activeSection === 'webui'}
            onClick={() => setActiveSection('webui')}
          />
        </div>
      </SurfaceCard>

      {activeSection === 'relay' && (
        <SurfaceCard title={t('advanced.relayTitle')} subtitle={t('advanced.relaySubtitle')}>
          <div className="grid gap-3 md:grid-cols-3">
            <InfoTile label={t('advanced.relay.senderTotal')} value={String(advanced.senderTotal)} />
            <InfoTile label={t('advanced.relay.senderEnabled')} value={String(advanced.senderEnabled)} />
            <InfoTile label={t('advanced.relay.senderAppNotifyEnabled')} value={String(advanced.senderAppNotifyEnabled)} />
          </div>
          <div className="mt-4 flex flex-wrap gap-3">
            <ActionButton tone="primary" onClick={() => navigate('/senders')}>
              {t('advanced.relay.openSenders')}
            </ActionButton>
            <ActionButton onClick={() => navigate('/apps')}>{t('advanced.relay.openApps')}</ActionButton>
            <ActionButton onClick={() => navigate('/records')}>{t('advanced.relay.openRecords')}</ActionButton>
          </div>
        </SurfaceCard>
      )}

      {activeSection === 'alerts' && (
        <SurfaceCard title={t('advanced.alertsTitle')} subtitle={t('advanced.alertsSubtitle')}>
          <div className="space-y-3">
            <ToggleRow
              label={t('advanced.alerts.toast')}
              hint={t('advanced.alerts.toastHint')}
              checked={settings.showToast}
              onChange={(value) => void patchSettings({ showToast: value })}
            />
            <ToggleRow
              label={t('advanced.alerts.notification')}
              hint={t('advanced.alerts.notificationHint')}
              checked={settings.showCodeNotification}
              onChange={(value) => void patchSettings({ showCodeNotification: value })}
            />
            <ToggleRow
              label={t('advanced.alerts.recovery')}
              hint={t('advanced.alerts.recoveryHint')}
              checked={settings.forceStopRecoveryEnabled}
              onChange={(value) => void patchSettings({ forceStopRecoveryEnabled: value })}
            />
          </div>
        </SurfaceCard>
      )}

      {activeSection === 'intercept' && (
        <SurfaceCard title={t('advanced.interceptTitle')} subtitle={t('advanced.interceptSubtitle')}>
          <div className="space-y-4">
            <ToggleRow
              label={t('advanced.intercept.enable')}
              hint={t('advanced.intercept.enableHint')}
              checked={advanced.enableSmsBlacklist}
              onChange={(value) => void patchAdvanced({ enableSmsBlacklist: value })}
            />
            <div className="grid gap-4 lg:grid-cols-2">
              <ToggleRow
                label={t('advanced.intercept.delete')}
                checked={intercept.smsBlacklistActionDelete}
                onChange={(value) => void patchIntercept({ smsBlacklistActionDelete: value })}
              />
              <ToggleRow
                label={t('advanced.intercept.block')}
                checked={intercept.smsBlacklistActionBlock}
                onChange={(value) => void patchIntercept({ smsBlacklistActionBlock: value })}
              />
            </div>
            <TextAreaField
              label={t('advanced.intercept.numbers')}
              value={intercept.smsBlacklistNumbers}
              hint={t('advanced.intercept.numbersHint')}
              onBlur={(value) => void patchIntercept({ smsBlacklistNumbers: value })}
            />
            <TextAreaField
              label={t('advanced.intercept.prefixes')}
              value={intercept.smsBlacklistPrefixes}
              hint={t('advanced.intercept.prefixesHint')}
              onBlur={(value) => void patchIntercept({ smsBlacklistPrefixes: value })}
            />
            <TextAreaField
              label={t('advanced.intercept.regex')}
              value={intercept.smsBlacklistRegex}
              hint={t('advanced.intercept.regexHint')}
              onBlur={(value) => void patchIntercept({ smsBlacklistRegex: value })}
            />
            <TextAreaField
              label={t('advanced.intercept.content')}
              value={intercept.smsBlacklistContent}
              hint={t('advanced.intercept.contentHint')}
              onBlur={(value) => void patchIntercept({ smsBlacklistContent: value })}
            />
          </div>
        </SurfaceCard>
      )}

      {activeSection === 'webui' && (
        <SurfaceCard title={t('advanced.webuiTitle')} subtitle={t('advanced.webuiSubtitle')}>
          <div className="space-y-4">
            <ToggleRow
              label={t('advanced.webui.allowLan')}
              hint={t('advanced.webui.allowLanHint')}
              checked={advanced.webUiLanAccess}
              onChange={(value) => void patchAdvanced({ webUiLanAccess: value })}
            />
            <div className="flex flex-wrap gap-2">
              <RelayBadge tone={advanced.webUiLanAccess ? 'success' : 'muted'}>
                {advanced.webUiLanAccess ? t('common.lanEnabled') : t('common.systemOnly')}
              </RelayBadge>
              <RelayBadge tone="accent">{t('common.defaultUsername')}</RelayBadge>
              <RelayBadge>{t('common.httpsWebui')}</RelayBadge>
            </div>
          </div>
        </SurfaceCard>
      )}
    </PageShell>
  )
}

function EntryCard({
  title,
  subtitle,
  active,
  onClick
}: {
  title: string
  subtitle: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        'flex w-full items-start justify-between gap-4 rounded-[24px] border px-4 py-4 text-left transition',
        active
          ? 'border-[#95bc29] bg-[linear-gradient(135deg,#eef8cb,#dcefab)] shadow-[0_18px_38px_-28px_rgba(111,142,24,0.42)]'
          : 'border-[#d6e5a6] bg-[linear-gradient(180deg,#ffffff_0%,#f7fbe9_100%)] hover:bg-white'
      )}
    >
      <div>
        <div className="text-base font-semibold text-[#243115]">{title}</div>
        <div className="mt-1 text-sm leading-6 text-[#6c785d]">{subtitle}</div>
      </div>
      <span className="pt-1 text-sm text-[#708b23]">→</span>
    </button>
  )
}

function InfoTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[22px] border border-[#d6e5a6] bg-[linear-gradient(180deg,#ffffff_0%,#f7fbe9_100%)] px-4 py-4">
      <div className="text-xs uppercase tracking-[0.18em] text-[#708b23]">{label}</div>
      <div className="mt-2 text-2xl font-semibold tracking-[-0.04em] text-[#243115]">{value}</div>
    </div>
  )
}

function TextAreaField({
  label,
  value,
  hint,
  onBlur
}: {
  label: string
  value: string
  hint?: string
  onBlur: (value: string) => void
}) {
  const [text, setText] = useState(value)
  useEffect(() => {
    setText(value)
  }, [value])

  return (
    <div className="space-y-2">
      <div>
        <p className="text-sm font-medium text-[#34461b]">{label}</p>
        {hint ? <p className="mt-1 text-xs leading-5 text-[#70805d]">{hint}</p> : null}
      </div>
      <textarea
        className="relay-input"
        rows={4}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onBlur={() => onBlur(text)}
      />
    </div>
  )
}
