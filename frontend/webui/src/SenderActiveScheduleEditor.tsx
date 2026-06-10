import type { SupportedLocale } from './i18n'
import {
  buildDefaultSenderActiveSchedule,
  buildStarterSenderActiveScheduleRule,
  normalizeSenderActiveSchedule,
  type SenderActiveSchedule,
  type SenderActiveScheduleRule,
  type SenderScheduleMode
} from '../../shared/senderActiveSchedule'
import { ActionButton, RelaySelect, RelaySwitch } from './template'

type SenderActiveScheduleEditorProps = {
  schedule?: SenderActiveSchedule
  locale: SupportedLocale
  onChange: (schedule: SenderActiveSchedule) => void
}

const DAYS = [
  { value: 1, en: 'Mon', zh: '周一' },
  { value: 2, en: 'Tue', zh: '周二' },
  { value: 3, en: 'Wed', zh: '周三' },
  { value: 4, en: 'Thu', zh: '周四' },
  { value: 5, en: 'Fri', zh: '周五' },
  { value: 6, en: 'Sat', zh: '周六' },
  { value: 7, en: 'Sun', zh: '周日' }
] as const

const TEXT = {
  title: { en: 'Active time', 'zh-CN': '生效时间', 'zh-TW': '生效時間' },
  noRestrictions: { en: 'No schedule restrictions', 'zh-CN': '未限制时间段', 'zh-TW': '未限制時間段' },
  sms: { en: 'SMS', 'zh-CN': '短信', 'zh-TW': '簡訊' },
  appNotify: { en: 'App Notify', 'zh-CN': '应用通知', 'zh-TW': '應用通知' },
  callNotify: { en: 'Call Notify', 'zh-CN': '通话通知', 'zh-TW': '通話通知' },
  blacklist: { en: 'Blacklist', 'zh-CN': '黑名单', 'zh-TW': '黑名單' },
  whitelist: { en: 'Whitelist', 'zh-CN': '白名单', 'zh-TW': '白名單' },
  weekdays: { en: 'Weekdays', 'zh-CN': '星期', 'zh-TW': '星期' },
  ranges: { en: 'Time ranges', 'zh-CN': '时间段', 'zh-TW': '時間段' },
  addRange: { en: 'Add range', 'zh-CN': '添加时间段', 'zh-TW': '新增時間段' },
  remove: { en: 'Remove', 'zh-CN': '删除', 'zh-TW': '刪除' }
} as const

type ScheduleBucket = 'sms' | 'appNotify' | 'callNotify'

export function SenderActiveScheduleEditor({
  schedule,
  locale,
  onChange
}: SenderActiveScheduleEditorProps) {
  const normalized = normalizeSenderActiveSchedule(schedule ?? buildDefaultSenderActiveSchedule())

  function updateRule(bucket: ScheduleBucket, nextRule: SenderActiveScheduleRule) {
    onChange(
      normalizeSenderActiveSchedule({
        ...normalized,
        [bucket]: nextRule
      })
    )
  }

  return (
    <div className="space-y-4 rounded-[24px] border border-[#dde7b6] bg-white/90 px-4 py-4">
      <div className="text-sm font-medium text-[#435722]">{resolveText(locale, TEXT.title)}</div>
      <div className="text-xs leading-6 text-[#6b775b]">{buildSummary(normalized, locale)}</div>
      <div className="grid gap-4 xl:grid-cols-3">
        <ScheduleRuleCard locale={locale} title={resolveText(locale, TEXT.sms)} rule={normalized.sms} onChange={(rule) => updateRule('sms', rule)} />
        <ScheduleRuleCard locale={locale} title={resolveText(locale, TEXT.appNotify)} rule={normalized.appNotify} onChange={(rule) => updateRule('appNotify', rule)} />
        <ScheduleRuleCard locale={locale} title={resolveText(locale, TEXT.callNotify)} rule={normalized.callNotify} onChange={(rule) => updateRule('callNotify', rule)} />
      </div>
    </div>
  )
}

function ScheduleRuleCard({
  locale,
  title,
  rule,
  onChange
}: {
  locale: SupportedLocale
  title: string
  rule: SenderActiveScheduleRule
  onChange: (rule: SenderActiveScheduleRule) => void
}) {
  return (
    <div className="rounded-[20px] border border-[#e7efc7] bg-[#fbfef2] px-4 py-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="text-sm font-medium text-[#31411c]">{title}</div>
        <RelaySwitch
          checked={rule.enabled}
          onChange={(enabled) => {
            if (enabled && !rule.ranges.length) {
              onChange(buildStarterSenderActiveScheduleRule())
              return
            }
            onChange({ ...rule, enabled })
          }}
        />
      </div>

      <label className="mb-2 block text-xs font-medium uppercase tracking-[0.18em] text-[#6b775b]">
        {resolveText(locale, TEXT.title)}
      </label>
      <RelaySelect
        value={rule.mode}
        options={[
          { value: 'blacklist', label: resolveText(locale, TEXT.blacklist) },
          { value: 'whitelist', label: resolveText(locale, TEXT.whitelist) }
        ]}
        onChange={(value) => onChange({ ...rule, mode: value as SenderScheduleMode })}
      />

      <label className="mb-2 mt-4 block text-xs font-medium uppercase tracking-[0.18em] text-[#6b775b]">
        {resolveText(locale, TEXT.weekdays)}
      </label>
      <div className="flex flex-wrap gap-2">
        {DAYS.map((day) => {
          const selected = rule.weekdays.includes(day.value)
          return (
            <button
              key={day.value}
              type="button"
              className={`rounded-full border px-3 py-1 text-xs ${
                selected
                  ? 'border-[#7aa21d] bg-[#edf6cf] text-[#31411c]'
                  : 'border-[#d6dfb2] bg-white text-[#667451]'
              }`}
              onClick={() => {
                const nextWeekdays = selected
                  ? rule.weekdays.filter((value) => value !== day.value)
                  : [...rule.weekdays, day.value].sort((left, right) => left - right)
                onChange({
                  ...rule,
                  weekdays: nextWeekdays.length ? nextWeekdays : [...rule.weekdays]
                })
              }}
            >
              {locale === 'en' ? day.en : day.zh}
            </button>
          )
        })}
      </div>

      <label className="mb-2 mt-4 block text-xs font-medium uppercase tracking-[0.18em] text-[#6b775b]">
        {resolveText(locale, TEXT.ranges)}
      </label>
      <div className="space-y-3">
        {rule.ranges.map((range, index) => (
          <div key={`${title}-${index}`} className="grid gap-2 md:grid-cols-[1fr_1fr_auto]">
            <input
              className="relay-input"
              type="time"
              aria-label={`${title} start ${index + 1}`}
              value={range.start}
              onChange={(event) => {
                const nextRanges = rule.ranges.map((current, currentIndex) =>
                  currentIndex === index ? { ...current, start: event.target.value } : current
                )
                onChange({ ...rule, ranges: nextRanges })
              }}
            />
            <input
              className="relay-input"
              type="time"
              aria-label={`${title} end ${index + 1}`}
              value={range.end}
              onChange={(event) => {
                const nextRanges = rule.ranges.map((current, currentIndex) =>
                  currentIndex === index ? { ...current, end: event.target.value } : current
                )
                onChange({ ...rule, ranges: nextRanges })
              }}
            />
            <ActionButton
              onClick={() => {
                onChange({
                  ...rule,
                  ranges: rule.ranges.filter((_, currentIndex) => currentIndex !== index)
                })
              }}
            >
              {resolveText(locale, TEXT.remove)}
            </ActionButton>
          </div>
        ))}
      </div>

      <div className="mt-3">
        <ActionButton
          onClick={() =>
            onChange({
              ...rule,
              ranges: [...rule.ranges, { start: '09:00', end: '18:00' }]
            })
          }
        >
          {resolveText(locale, TEXT.addRange)}
        </ActionButton>
      </div>
    </div>
  )
}

function buildSummary(schedule: SenderActiveSchedule, locale: SupportedLocale) {
  const sms = schedule.sms.enabled ? schedule.sms.ranges.length : 0
  const appNotify = schedule.appNotify.enabled ? schedule.appNotify.ranges.length : 0
  const callNotify = schedule.callNotify.enabled ? schedule.callNotify.ranges.length : 0
  if (sms === 0 && appNotify === 0 && callNotify === 0) {
    return resolveText(locale, TEXT.noRestrictions)
  }
  return `${resolveText(locale, TEXT.sms)} ${sms} · ${resolveText(locale, TEXT.appNotify)} ${appNotify} · ${resolveText(locale, TEXT.callNotify)} ${callNotify}`
}

function resolveText(
  locale: SupportedLocale,
  text: { en: string; 'zh-CN': string; 'zh-TW': string }
) {
  if (locale === 'zh-TW') return text['zh-TW']
  if (locale === 'zh-CN') return text['zh-CN']
  return text.en
}
