import { useEffect, useMemo, useState } from 'react'
import type { SupportedLocale } from '../i18n'
import { DesktopSelect } from '../ui'
import {
  buildSenderJsonFromFormState,
  getSenderFieldSchemas,
  parseSenderFormState,
  resolveSenderText,
  type SenderFieldSchema
} from '../../../shared/senderDefaults'

type JsonRecord = Record<string, unknown>

type SenderFieldEditorProps = {
  type: number
  jsonSetting: string
  locale?: SupportedLocale
  onLiveChange?: (jsonSetting: string) => void
  onCommit?: (jsonSetting: string) => void
}

export function SenderFieldEditor({
  type,
  jsonSetting,
  locale = 'en',
  onLiveChange,
  onCommit
}: SenderFieldEditorProps) {
  const fields = useMemo(() => getSenderFieldSchemas(type), [type])
  const [formState, setFormState] = useState<JsonRecord>(() => parseSenderFormState(type, jsonSetting))

  useEffect(() => {
    setFormState(parseSenderFormState(type, jsonSetting))
  }, [type, jsonSetting])

  if (!fields.length) {
    return (
      <div className="banner">
        {resolveEditorText(locale, 'unsupported')}
      </div>
    )
  }

  function updateField(field: SenderFieldSchema, value: unknown, commit = false) {
    setFormState((previous) => {
      const next = {
        ...previous,
        [field.key]: value
      }
      const nextJson = buildSenderJsonFromFormState(type, next)
      onLiveChange?.(nextJson)
      if (commit) {
        onCommit?.(nextJson)
      }
      return next
    })
  }

  function commitCurrentForm() {
    const nextJson = buildSenderJsonFromFormState(type, formState)
    onCommit?.(nextJson)
  }

  return (
    <div className="sender-editor">
      <div className="sender-editor-grid">
        {fields.map((field) => {
          if (field.showIf) {
            const depValue = formState[field.showIf.field]
            if (String(depValue ?? '') !== field.showIf.equals) {
              return null
            }
          }
          return (
            <div key={field.key} className={field.fullWidth ? 'sender-editor-field sender-editor-field--full' : 'sender-editor-field'}>
              <label className="field">
                <span>{resolveSenderText(locale, field.label)}</span>
                {renderField(
                  field,
                  formState[field.key],
                  locale,
                  (value, commit) => updateField(field, value, commit),
                  commitCurrentForm
                )}
              </label>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function renderField(
  field: SenderFieldSchema,
  value: unknown,
  locale: SupportedLocale,
  onChange: (value: unknown, commit?: boolean) => void,
  onCommit: () => void
) {
  if (field.kind === 'boolean') {
    return (
      <label className="checkbox-row">
        <input type="checkbox" checked={value === true} onChange={(event) => onChange(event.target.checked, true)} />
        <span>{resolveEditorText(locale, value === true ? 'enabled' : 'disabled')}</span>
      </label>
    )
  }

  if (field.kind === 'select') {
    return (
      <DesktopSelect
        value={typeof value === 'string' ? value : ''}
        options={(field.options ?? []).map((option) => ({
          value: option.value,
          label: resolveSenderText(locale, option.label)
        }))}
        onChange={(nextValue) => onChange(nextValue, true)}
      />
    )
  }

  if (field.kind === 'textarea' || field.kind === 'json') {
    return (
      <textarea
        className="text-area-field"
        rows={field.rows ?? 4}
        placeholder={field.placeholder ? resolveSenderText(locale, field.placeholder) : undefined}
        value={typeof value === 'string' ? value : ''}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onCommit}
      />
    )
  }

  return (
    <input
      className="text-input"
      type={field.kind === 'number' ? 'number' : 'text'}
      placeholder={field.placeholder ? resolveSenderText(locale, field.placeholder) : undefined}
      value={field.kind === 'number' ? String(value ?? '') : (typeof value === 'string' ? value : '')}
      onChange={(event) => onChange(field.kind === 'number' ? event.target.value : event.target.value)}
      onBlur={onCommit}
    />
  )
}

function resolveEditorText(
  locale: SupportedLocale,
  key: 'unsupported' | 'enabled' | 'disabled',
) {
  const zh = {
    unsupported: '该发送通道暂未在控制台暴露结构化字段，请改在 Android 设备端编辑，而不是在这里回退到原始 JSON。',
    enabled: '已启用',
    disabled: '已关闭'
  }

  if (locale === 'zh-CN' || locale === 'zh-TW') {
    return zh[key]
  }

  const en = {
    unsupported: 'This sender type is not exposed as typed fields in the console yet. Edit it on the Android device instead of using raw JSON here.',
    enabled: 'Enabled',
    disabled: 'Disabled'
  }

  return en[key]
}
