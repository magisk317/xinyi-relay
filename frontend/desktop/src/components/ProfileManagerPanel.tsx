import { useState, type ReactNode } from 'react'
import { useDesktopI18n } from '../i18n'
import { useDesktop } from '../state/DesktopContext'
import { Panel, Tag } from '../ui'

type ProfileManagerPanelProps = {
  title: string
  subtitle?: string
  actions?: ReactNode
}

const DEFAULT_PROFILE_FORM = {
  id: '',
  name: '',
  baseUrl: 'https://localhost:8443',
  allowSelfSigned: true,
  note: ''
}

export function ProfileManagerPanel({
  title,
  subtitle,
  actions
}: ProfileManagerPanelProps) {
  const { bootstrap, activeProfile, saveProfile, deleteProfile, setActiveProfile } = useDesktop()
  const { t } = useDesktopI18n()
  const [form, setForm] = useState(DEFAULT_PROFILE_FORM)

  const resetForm = () => {
    setForm(DEFAULT_PROFILE_FORM)
  }

  return (
    <Panel title={title} subtitle={subtitle} actions={actions}>
      <div className="profile-editor-grid">
        <label className="field">
          <span>{t('profile.field.name')}</span>
          <input
            className="text-input"
            value={form.name}
            onChange={(event) => setForm((value) => ({ ...value, name: event.target.value }))}
          />
        </label>
        <label className="field">
          <span>{t('profile.field.baseUrl')}</span>
          <input
            className="text-input"
            value={form.baseUrl}
            onChange={(event) => setForm((value) => ({ ...value, baseUrl: event.target.value }))}
          />
        </label>
        <label className="field field--checkbox">
          <input
            type="checkbox"
            checked={form.allowSelfSigned}
            onChange={(event) => setForm((value) => ({ ...value, allowSelfSigned: event.target.checked }))}
          />
          <span>{t('profile.field.allowSelfSigned')}</span>
        </label>
        <label className="field">
          <span>{t('profile.field.note')}</span>
          <input
            className="text-input"
            value={form.note}
            onChange={(event) => setForm((value) => ({ ...value, note: event.target.value }))}
          />
        </label>
      </div>

      <div className="button-row">
        <button
          type="button"
          className="primary-button"
          onClick={() => {
            void saveProfile({
              id: form.id || undefined,
              name: form.name,
              baseUrl: form.baseUrl,
              allowSelfSigned: form.allowSelfSigned,
              note: form.note
            }).then(resetForm).catch(() => {})
          }}
        >
          {form.id ? t('profile.button.update') : t('profile.button.save')}
        </button>
        {form.id ? (
          <button type="button" className="ghost-button" onClick={resetForm}>
            {t('profile.button.cancelEdit')}
          </button>
        ) : null}
      </div>

      <div className="list-grid">
        {bootstrap?.profiles.map((profile) => (
          <article key={profile.id} className="list-card">
            <div className="list-card-head">
              <div>
                <h3>{profile.name}</h3>
                <p>{profile.baseUrl}</p>
              </div>
              <div className="button-row">
                {profile.active ? <Tag tone="success">{t('profile.badge.active')}</Tag> : null}
                {activeProfile?.id === profile.id ? <Tag tone="neutral">{t('profile.badge.currentTarget')}</Tag> : null}
              </div>
            </div>
            <div className="list-card-body">
              <div>{t('profile.selfSigned')}: {profile.allowSelfSigned ? t('profile.enabled') : t('profile.disabled')}</div>
              <div>{t('profile.note')}: {profile.note || t('common.none')}</div>
            </div>
            <div className="button-row">
              <button
                type="button"
                className="ghost-button"
                disabled={profile.active}
                onClick={() => void setActiveProfile(profile.id).catch(() => {})}
              >
                {profile.active ? t('profile.button.active') : t('profile.button.makeActive')}
              </button>
              <button
                type="button"
                className="ghost-button"
                onClick={() => setForm({
                  id: profile.id,
                  name: profile.name,
                  baseUrl: profile.baseUrl,
                  allowSelfSigned: profile.allowSelfSigned,
                  note: profile.note ?? ''
                })}
              >
                {t('profile.button.edit')}
              </button>
              <button
                type="button"
                className="danger-button"
                onClick={() => void deleteProfile(profile.id).catch(() => {})}
              >
                {t('profile.button.delete')}
              </button>
            </div>
          </article>
        ))}
      </div>
    </Panel>
  )
}
