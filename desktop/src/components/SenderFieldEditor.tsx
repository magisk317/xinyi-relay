import { useEffect, useMemo, useState } from 'react'
import type { SupportedLocale } from '../i18n'
import { DesktopSelect } from '../ui'
import {
  buildSenderJsonFromFormState,
  getSenderFieldSchemas,
  parseSenderFormState,
  prettySenderJson,
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
  const [rawJson, setRawJson] = useState(() => prettySenderJson(type, jsonSetting))
  const [rawError, setRawError] = useState('')

  useEffect(() => {
    setFormState(parseSenderFormState(type, jsonSetting))
    setRawJson(prettySenderJson(type, jsonSetting))
    setRawError('')
  }, [type, jsonSetting])

  if (!fields.length) {
    return (
      <textarea
        className="code-editor sender-editor-code"
        value={rawJson}
        onChange={(event) => {
          const nextValue = event.target.value
          setRawJson(nextValue)
          onLiveChange?.(nextValue)
        }}
        onBlur={() => onCommit?.(rawJson)}
      />
    )
  }

  function updateField(field: SenderFieldSchema, value: unknown, commit = false) {
    setFormState((previous) => {
      const next = {
        ...previous,
        [field.key]: value
      }
      const nextJson = buildSenderJsonFromFormState(type, next)
      setRawJson(prettySenderJson(type, nextJson))
      onLiveChange?.(nextJson)
      if (commit) {
        onCommit?.(nextJson)
      }
      return next
    })
  }

  function applyRawJson() {
    try {
      const parsed = JSON.parse(rawJson)
      if (typeof parsed !== 'object' || parsed == null || Array.isArray(parsed)) {
        throw new Error('invalid object')
      }
      const nextJson = buildSenderJsonFromFormState(type, parsed as JsonRecord)
      const nextForm = parseSenderFormState(type, nextJson)
      setFormState(nextForm)
      setRawJson(prettySenderJson(type, nextJson))
      setRawError('')
      onLiveChange?.(nextJson)
      onCommit?.(nextJson)
    } catch {
      setRawError(resolveEditorText(locale, 'invalidJson'))
    }
  }

  function commitCurrentForm() {
    const nextJson = buildSenderJsonFromFormState(type, formState)
    setRawJson(prettySenderJson(type, nextJson))
    onCommit?.(nextJson)
  }

  return (
    <div className="sender-editor">
      <div className="sender-editor-grid">
        {fields.map((field) => (
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
        ))}
      </div>

      <details className="sender-editor-advanced">
        <summary>{resolveEditorText(locale, 'advancedJson')}</summary>
        <div className="stack">
          <textarea
            className="code-editor sender-editor-code"
            value={rawJson}
            onChange={(event) => {
              setRawJson(event.target.value)
              setRawError('')
            }}
          />
          {rawError ? <div className="banner banner--danger">{rawError}</div> : null}
          <div className="button-row">
            <button type="button" className="ghost-button" onClick={applyRawJson}>
              {resolveEditorText(locale, 'applyJson')}
            </button>
          </div>
        </div>
      </details>
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

function resolveEditorText(locale: SupportedLocale, key: 'advancedJson' | 'applyJson' | 'invalidJson' | 'enabled' | 'disabled') {
  const zh = {
    advancedJson: '高级 JSON',
    applyJson: '应用 JSON',
    invalidJson: 'JSON 必须是合法对象。',
    enabled: '已启用',
    disabled: '已关闭'
  }

  if (locale === 'zh-CN' || locale === 'zh-TW') {
    return zh[key]
  }

  const en = {
    advancedJson: 'Advanced JSON',
    applyJson: 'Apply JSON',
    invalidJson: 'JSON must be a valid object.',
    enabled: 'Enabled',
    disabled: 'Disabled'
  }

  return en[key]
}
