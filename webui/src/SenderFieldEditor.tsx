import { useEffect, useMemo, useState } from 'react'
import type { SupportedLocale } from './i18n'
import { ActionButton, RelaySelect, RelaySwitch } from './template'
import {
  buildSenderJsonFromFormState,
  getSenderFieldSchemas,
  parseSenderFormState,
  prettySenderJson,
  resolveSenderText,
  type SenderFieldSchema
} from './senderDefaults'

type JsonRecord = Record<string, unknown>

type SenderFieldEditorProps = {
  type: number
  jsonSetting: string
  locale: SupportedLocale
  onLiveChange?: (jsonSetting: string) => void
  onCommit?: (jsonSetting: string) => void
}

const EDITOR_TEXT = {
  structured: {
    en: 'Structured config',
    'zh-CN': '结构化配置',
    'zh-TW': '結構化配置'
  },
  advanced: {
    en: 'Advanced JSON',
    'zh-CN': '高级 JSON',
    'zh-TW': '高級 JSON'
  },
  reset: {
    en: 'Reset template',
    'zh-CN': '重置模板',
    'zh-TW': '重置模板'
  },
  apply: {
    en: 'Apply JSON',
    'zh-CN': '应用 JSON',
    'zh-TW': '套用 JSON'
  },
  invalidJson: {
    en: 'JSON must be a valid object.',
    'zh-CN': 'JSON 必须是合法对象。',
    'zh-TW': 'JSON 必須是合法物件。'
  },
  syncHint: {
    en: 'These fields mirror the sender defaults used on Android. You can still fall back to JSON below.',
    'zh-CN': '这些字段会直接套用 Android 端同类通道的默认模板；下方仍可回退到 JSON 编辑。',
    'zh-TW': '這些欄位會直接套用 Android 端同類通道的預設模板；下方仍可回退到 JSON 編輯。'
  }
} as const

export function SenderFieldEditor({
  type,
  jsonSetting,
  locale,
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
        className="relay-input min-h-[10rem] font-mono text-xs"
        value={rawJson}
        onChange={(event) => {
          const next = event.target.value
          setRawJson(next)
          onLiveChange?.(next)
        }}
        onBlur={() => onCommit?.(rawJson)}
      />
    )
  }

  function updateField(field: SenderFieldSchema, value: unknown, commit = false) {
    setFormState((prev) => {
      const next = {
        ...prev,
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

  function commitCurrentForm() {
    const nextJson = buildSenderJsonFromFormState(type, formState)
    setRawJson(prettySenderJson(type, nextJson))
    onCommit?.(nextJson)
  }

  function resetTemplate() {
    const nextForm = parseSenderFormState(type, '')
    const nextJson = buildSenderJsonFromFormState(type, nextForm)
    setFormState(nextForm)
    setRawJson(prettySenderJson(type, nextJson))
    setRawError('')
    onLiveChange?.(nextJson)
    onCommit?.(nextJson)
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
      setRawError(resolveEditorText(locale, EDITOR_TEXT.invalidJson))
    }
  }

  return (
    <div className="space-y-4">
      <div className="rounded-[24px] border border-[#e2ebb8] bg-[#fbfef2] px-4 py-4">
        <div className="mb-3 text-sm font-medium text-[#435722]">{resolveEditorText(locale, EDITOR_TEXT.structured)}</div>
        <p className="mb-4 text-xs leading-6 text-[#6b775b]">{resolveEditorText(locale, EDITOR_TEXT.syncHint)}</p>
        <div className="grid gap-3 md:grid-cols-2">
          {fields.map((field) => (
            <div key={field.key} className={field.fullWidth ? 'md:col-span-2' : undefined}>
              <label className="mb-2 block text-sm font-medium text-[#31411c]">
                {resolveSenderText(locale, field.label)}
              </label>
              {renderField(field, formState[field.key], locale, (value, commit) => updateField(field, value, commit), commitCurrentForm)}
            </div>
          ))}
        </div>
      </div>

      <details className="rounded-[24px] border border-[#dde7b6] bg-white/90 px-4 py-4">
        <summary className="cursor-pointer select-none text-sm font-medium text-[#435722]">
          {resolveEditorText(locale, EDITOR_TEXT.advanced)}
        </summary>
        <div className="mt-4 space-y-3">
          <textarea
            className="relay-input min-h-[12rem] font-mono text-xs"
            value={rawJson}
            onChange={(event) => {
              setRawJson(event.target.value)
              setRawError('')
            }}
          />
          {rawError ? (
            <div className="rounded-[18px] border border-[#f7c9bf] bg-[#fff8f3] px-4 py-3 text-sm text-[#b24a24]">
              {rawError}
            </div>
          ) : null}
          <div className="flex flex-wrap gap-3">
            <ActionButton onClick={resetTemplate}>{resolveEditorText(locale, EDITOR_TEXT.reset)}</ActionButton>
            <ActionButton tone="primary" onClick={applyRawJson}>
              {resolveEditorText(locale, EDITOR_TEXT.apply)}
            </ActionButton>
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
      <div className="pt-2">
        <RelaySwitch checked={value === true} onChange={(next) => onChange(next, true)} />
      </div>
    )
  }

  if (field.kind === 'select') {
    return (
      <RelaySelect
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
        className="relay-input"
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
      className="relay-input"
      type={field.kind === 'number' ? 'number' : 'text'}
      placeholder={field.placeholder ? resolveSenderText(locale, field.placeholder) : undefined}
      value={field.kind === 'number' ? String(value ?? '') : (typeof value === 'string' ? value : '')}
      onChange={(event) => onChange(field.kind === 'number' ? event.target.value : event.target.value)}
      onBlur={onCommit}
    />
  )
}

function resolveEditorText(locale: SupportedLocale, text: { en: string; 'zh-CN': string; 'zh-TW': string }) {
  if (locale === 'zh-TW') return text['zh-TW']
  if (locale === 'zh-CN') return text['zh-CN']
  return text.en
}
