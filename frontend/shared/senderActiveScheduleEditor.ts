import {
  normalizeSenderActiveSchedule,
  type SenderActiveSchedule,
  type SenderActiveScheduleRule
} from './senderActiveSchedule'

export type ScheduleBucket = 'sms' | 'appNotify' | 'callNotify'

export const SCHEDULE_DAYS = [
  { value: 1, en: 'Mon', zh: '周一' },
  { value: 2, en: 'Tue', zh: '周二' },
  { value: 3, en: 'Wed', zh: '周三' },
  { value: 4, en: 'Thu', zh: '周四' },
  { value: 5, en: 'Fri', zh: '周五' },
  { value: 6, en: 'Sat', zh: '周六' },
  { value: 7, en: 'Sun', zh: '周日' }
] as const

export const SCHEDULE_TEXT = {
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

export function updateScheduleRule(
  normalized: SenderActiveSchedule,
  bucket: ScheduleBucket,
  nextRule: SenderActiveScheduleRule,
  onChange: (schedule: SenderActiveSchedule) => void
) {
  onChange(
    normalizeSenderActiveSchedule({
      ...normalized,
      [bucket]: nextRule
    })
  )
}

export function buildScheduleSummary(
  rule: SenderActiveScheduleRule | undefined,
  locale: string
): string {
  if (!rule || (!rule.weekdays?.length && !rule.ranges?.length)) {
    return ''
  }
  const dayNames = (rule.weekdays ?? [])
    .map((d) => SCHEDULE_DAYS.find((day) => day.value === d))
    .filter(Boolean)
    .map((d) => locale.startsWith('zh') ? d!.zh : d!.en)
  const rangeCount = rule.ranges?.length ?? 0
  const parts: string[] = []
  if (dayNames.length) parts.push(dayNames.join(', '))
  if (rangeCount) parts.push(`${rangeCount} ${locale.startsWith('zh') ? '个时间段' : 'time range(s)'}`)
  return parts.join(' · ')
}

export function resolveScheduleText(
  locale: string,
  text: { en: string; 'zh-CN': string; 'zh-TW': string }
): string {
  if (locale === 'zh-TW') return text['zh-TW']
  if (locale === 'zh-CN') return text['zh-CN']
  return text.en
}
