import type { DeviceActionCommandItem, DeviceActionRequest } from './contracts/console'

/**
 * Wire vocabulary for one-shot device actions.
 *
 * Mirrors the shared Kotlin contract (DeviceActionConst) so the webui, the
 * Android app and sibling projects (xsc / mipush) stay aligned on action
 * names, param keys and values without duplicating literals.
 */
export const DEVICE_ACTIONS = {
  SET_RINGER_MODE: 'SET_RINGER_MODE',
  SET_INTERRUPTION_FILTER: 'SET_INTERRUPTION_FILTER'
} as const

export const DEVICE_ACTION_PARAM_KEYS = {
  MODE: 'mode',
  FILTER: 'filter'
} as const

export const RINGER_MODES = {
  NORMAL: 'normal',
  SILENT: 'silent',
  VIBRATE: 'vibrate'
} as const

export const INTERRUPTION_FILTERS = {
  ALL: 'all',
  PRIORITY: 'priority'
} as const

export const DEVICE_ACTION_STATUSES = {
  PENDING: 'pending',
  DONE: 'done',
  FAILED: 'failed'
} as const

export type DeviceActionTone = 'accent' | 'muted' | 'success' | 'warning' | 'danger'

export type DeviceActionParams = Record<string, string>

/** Quick actions offered on the device control page, in display order. */
export const DEVICE_CONTROL_ACTIONS: ReadonlyArray<{
  action: string
  params: DeviceActionParams
}> = [
  { action: DEVICE_ACTIONS.SET_RINGER_MODE, params: { [DEVICE_ACTION_PARAM_KEYS.MODE]: RINGER_MODES.NORMAL } },
  { action: DEVICE_ACTIONS.SET_RINGER_MODE, params: { [DEVICE_ACTION_PARAM_KEYS.MODE]: RINGER_MODES.SILENT } },
  { action: DEVICE_ACTIONS.SET_RINGER_MODE, params: { [DEVICE_ACTION_PARAM_KEYS.MODE]: RINGER_MODES.VIBRATE } },
  {
    action: DEVICE_ACTIONS.SET_INTERRUPTION_FILTER,
    params: { [DEVICE_ACTION_PARAM_KEYS.FILTER]: INTERRUPTION_FILTERS.PRIORITY }
  },
  {
    action: DEVICE_ACTIONS.SET_INTERRUPTION_FILTER,
    params: { [DEVICE_ACTION_PARAM_KEYS.FILTER]: INTERRUPTION_FILTERS.ALL }
  }
]

export function buildDeviceActionRequest(action: string, params: DeviceActionParams): DeviceActionRequest {
  return { action, params }
}

export function deviceActionStatusTone(status: string): DeviceActionTone {
  switch (status) {
    case DEVICE_ACTION_STATUSES.PENDING:
      return 'accent'
    case DEVICE_ACTION_STATUSES.DONE:
      return 'success'
    case DEVICE_ACTION_STATUSES.FAILED:
      return 'danger'
    default:
      return 'muted'
  }
}

export function isDeviceActionPending(status: string): boolean {
  return status === DEVICE_ACTION_STATUSES.PENDING
}

export function summarizeDeviceAction(item: Pick<DeviceActionCommandItem, 'action' | 'params'>): string {
  const params = Object.entries(item.params)
    .map(([key, value]) => `${key}=${String(value)}`)
    .join(', ')
  return params ? `${item.action ?? ''} (${params})` : (item.action ?? '')
}
