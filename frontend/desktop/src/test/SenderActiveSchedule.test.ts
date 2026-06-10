import { describe, expect, it } from 'vitest'
import {
  buildDefaultSenderActiveSchedule,
  normalizeSenderActiveSchedule
} from '../../../shared/senderActiveSchedule'

describe('sender active schedule helpers', () => {
  it('normalizes invalid rules to disabled defaults', () => {
    const schedule = normalizeSenderActiveSchedule({
      sms: {
        enabled: true,
        mode: 'unknown',
        weekdays: [9],
        ranges: [{ start: '09:00', end: '09:00' }]
      }
    })

    expect(schedule.sms.enabled).toBe(false)
    expect(schedule.sms.mode).toBe('blacklist')
    expect(schedule.sms.weekdays).toEqual([1, 2, 3, 4, 5, 6, 7])
    expect(schedule.sms.ranges).toEqual([])
  })

  it('keeps valid rules and fills missing buckets', () => {
    const schedule = normalizeSenderActiveSchedule({
      appNotify: {
        enabled: true,
        mode: 'whitelist',
        weekdays: [1, 2, 3, 4, 5],
        ranges: [{ start: '09:00', end: '18:00' }]
      }
    })

    expect(schedule).toEqual({
      ...buildDefaultSenderActiveSchedule(),
      appNotify: {
        enabled: true,
        mode: 'whitelist',
        weekdays: [1, 2, 3, 4, 5],
        ranges: [{ start: '09:00', end: '18:00' }]
      }
    })
  })
})
