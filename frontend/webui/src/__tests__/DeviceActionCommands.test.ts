import { describe, expect, it } from 'vitest'
import {
  DEVICE_ACTIONS,
  DEVICE_ACTION_PARAM_KEYS,
  DEVICE_ACTION_STATUSES,
  DEVICE_CONTROL_ACTIONS,
  INTERRUPTION_FILTERS,
  RINGER_MODES,
  buildDeviceActionRequest,
  deviceActionStatusTone,
  isDeviceActionPending,
  summarizeDeviceAction
} from '../../../shared/deviceActionCommands'

describe('shared device action helpers', () => {
  it('mirrors the shared Kotlin contract vocabulary', () => {
    expect(DEVICE_ACTIONS.SET_RINGER_MODE).toBe('SET_RINGER_MODE')
    expect(DEVICE_ACTIONS.SET_INTERRUPTION_FILTER).toBe('SET_INTERRUPTION_FILTER')
    expect(DEVICE_ACTION_PARAM_KEYS.MODE).toBe('mode')
    expect(DEVICE_ACTION_PARAM_KEYS.FILTER).toBe('filter')
    expect(RINGER_MODES.NORMAL).toBe('normal')
    expect(RINGER_MODES.SILENT).toBe('silent')
    expect(RINGER_MODES.VIBRATE).toBe('vibrate')
    expect(INTERRUPTION_FILTERS.ALL).toBe('all')
    expect(INTERRUPTION_FILTERS.PRIORITY).toBe('priority')
    expect(DEVICE_ACTION_STATUSES.PENDING).toBe('pending')
    expect(DEVICE_ACTION_STATUSES.DONE).toBe('done')
    expect(DEVICE_ACTION_STATUSES.FAILED).toBe('failed')
  })

  it('offers ringer and interruption quick actions in display order', () => {
    expect(DEVICE_CONTROL_ACTIONS).toEqual([
      { action: 'SET_RINGER_MODE', params: { mode: 'normal' } },
      { action: 'SET_RINGER_MODE', params: { mode: 'silent' } },
      { action: 'SET_RINGER_MODE', params: { mode: 'vibrate' } },
      { action: 'SET_INTERRUPTION_FILTER', params: { filter: 'priority' } },
      { action: 'SET_INTERRUPTION_FILTER', params: { filter: 'all' } }
    ])
  })

  it('builds wire requests with action and params only', () => {
    expect(buildDeviceActionRequest('SET_RINGER_MODE', { mode: 'silent' })).toEqual({
      action: 'SET_RINGER_MODE',
      params: { mode: 'silent' }
    })
  })

  it('maps statuses to badge tones', () => {
    expect(deviceActionStatusTone('pending')).toBe('accent')
    expect(deviceActionStatusTone('done')).toBe('success')
    expect(deviceActionStatusTone('failed')).toBe('danger')
    expect(deviceActionStatusTone('mystery')).toBe('muted')
  })

  it('detects pending actions', () => {
    expect(isDeviceActionPending('pending')).toBe(true)
    expect(isDeviceActionPending('done')).toBe(false)
  })

  it('summarizes actions with and without params', () => {
    expect(
      summarizeDeviceAction({
        action: 'SET_RINGER_MODE',
        params: { mode: 'silent' }
      })
    ).toBe('SET_RINGER_MODE (mode=silent)')
    expect(summarizeDeviceAction({ action: 'SET_RINGER_MODE', params: {} })).toBe('SET_RINGER_MODE')
  })
})
