export type SenderScheduleMode = 'blacklist' | 'whitelist'

export type SenderActiveScheduleRange = {
  start: string
  end: string
}

export type SenderActiveScheduleRule = {
  enabled: boolean
  mode: SenderScheduleMode
  weekdays: number[]
  ranges: SenderActiveScheduleRange[]
}

export type SenderActiveSchedule = {
  sms: SenderActiveScheduleRule
  appNotify: SenderActiveScheduleRule
  callNotify: SenderActiveScheduleRule
}

const ALL_WEEKDAYS = [1, 2, 3, 4, 5, 6, 7] as const
const TIME_PATTERN = /^([01]\d|2[0-3]):([0-5]\d)$/

export function buildDefaultSenderActiveScheduleRule(): SenderActiveScheduleRule {
  return {
    enabled: false,
    mode: 'blacklist',
    weekdays: [...ALL_WEEKDAYS],
    ranges: []
  }
}

export function buildStarterSenderActiveScheduleRule(): SenderActiveScheduleRule {
  return {
    enabled: true,
    mode: 'blacklist',
    weekdays: [...ALL_WEEKDAYS],
    ranges: [{ start: '09:00', end: '18:00' }]
  }
}

export function buildDefaultSenderActiveSchedule(): SenderActiveSchedule {
  return {
    sms: buildDefaultSenderActiveScheduleRule(),
    appNotify: buildDefaultSenderActiveScheduleRule(),
    callNotify: buildDefaultSenderActiveScheduleRule()
  }
}

export function normalizeSenderActiveSchedule(value: unknown): SenderActiveSchedule {
  const raw = isPlainObject(value) ? value : {}
  return {
    sms: normalizeSenderActiveScheduleRule(raw.sms),
    appNotify: normalizeSenderActiveScheduleRule(raw.appNotify),
    callNotify: normalizeSenderActiveScheduleRule(raw.callNotify)
  }
}

export function normalizeSenderActiveScheduleRule(value: unknown): SenderActiveScheduleRule {
  const raw = isPlainObject(value) ? value : {}
  const ranges = normalizeRanges(raw.ranges)
  return {
    enabled: raw.enabled === true && ranges.length > 0,
    mode: raw.mode === 'whitelist' ? 'whitelist' : 'blacklist',
    weekdays: normalizeWeekdays(raw.weekdays),
    ranges
  }
}

export function countEnabledScheduleRanges(rule: SenderActiveScheduleRule): number {
  return rule.enabled ? rule.ranges.length : 0
}

function normalizeWeekdays(value: unknown): number[] {
  if (!Array.isArray(value)) return [...ALL_WEEKDAYS]
  const normalized = value
    .map((item) => (typeof item === 'number' ? item : Number(item)))
    .filter((item) => Number.isInteger(item) && item >= 1 && item <= 7)
    .filter((item, index, array) => array.indexOf(item) === index)
  return normalized.length ? normalized : [...ALL_WEEKDAYS]
}

function normalizeRanges(value: unknown): SenderActiveScheduleRange[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!isPlainObject(item)) return []
    const start = typeof item.start === 'string' ? item.start.trim() : ''
    const end = typeof item.end === 'string' ? item.end.trim() : ''
    if (!TIME_PATTERN.test(start) || !TIME_PATTERN.test(end) || start === end) return []
    return [{ start, end }]
  })
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
