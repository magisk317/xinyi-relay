import { useMemo, useState } from 'react'
import type { SupportedLocale } from './i18n'
import { RelaySelect, RelaySwitch } from './template'
import {
  buildSenderJsonFromFormState,
  getSenderFieldSchemas,
  parseSenderFormState,
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
  unsupported: {
    en: 'This sender type is not exposed as typed fields in the console yet. Edit it on the Android device instead of using raw JSON here.',
    'zh-CN': '该发送通道暂未在控制台暴露结构化字段，请改在 Android 设备端编辑，而不是在这里回退到原始 JSON。',
    'zh-TW': '該傳送通道暫未在控制台暴露結構化欄位，請改在 Android 裝置端編輯，而不是在這裡回退到原始 JSON。'
  },
  syncHint: {
    en: 'These fields mirror the sender defaults used on Android and are now the primary editing surface.',
    'zh-CN': '这些字段会直接套用 Android 端同类通道的默认模板，并作为当前唯一的主要编辑入口。',
    'zh-TW': '這些欄位會直接套用 Android 端同類通道的預設模板，並作為目前唯一的主要編輯入口。'
  }
} as const

export function SenderFieldEditor(props: SenderFieldEditorProps) {
  const resetKey = `${props.type}::${props.jsonSetting}`
  return <SenderFieldEditorInner key={resetKey} {...props} />
}

function SenderFieldEditorInner({
  type,
  jsonSetting,
  locale,
  onLiveChange,
  onCommit
}: SenderFieldEditorProps) {
  const fields = useMemo(() => getSenderFieldSchemas(type), [type])
  const [formState, setFormState] = useState<JsonRecord>(() => parseSenderFormState(type, jsonSetting))

  if (!fields.length) {
    return (
      <div className="rounded-[18px] border border-[#e4d8ae] bg-[#fffaf0] px-4 py-3 text-sm leading-6 text-[#7a6540]">
        {resolveEditorText(locale, EDITOR_TEXT.unsupported)}
      </div>
    )
  }

  function updateField(field: SenderFieldSchema, value: unknown, commit = false) {
    setFormState((prev) => {
      const next = {
        ...prev,
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
    <div className="space-y-4">
      <div className="rounded-[24px] border border-[#e2ebb8] bg-[#fbfef2] px-4 py-4">
        <div className="mb-3 text-sm font-medium text-[#435722]">{resolveEditorText(locale, EDITOR_TEXT.structured)}</div>
        <p className="mb-4 text-xs leading-6 text-[#6b775b]">{resolveEditorText(locale, EDITOR_TEXT.syncHint)}</p>
        <div className="grid gap-3 md:grid-cols-2">
          {fields.map((field) => {
            if (field.showIf) {
              const depValue = formState[field.showIf.field]
              if (String(depValue ?? '') !== field.showIf.equals) {
                return null
              }
            }
            return (
              <div key={field.key} className={field.fullWidth ? 'md:col-span-2' : undefined}>
                <label className="mb-2 block text-sm font-medium text-[#31411c]">
                  {resolveSenderText(locale, field.label)}
                </label>
                {renderField(field, formState[field.key], locale, (value, commit) => updateField(field, value, commit), commitCurrentForm)}
              </div>
            )
          })}
        </div>
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
