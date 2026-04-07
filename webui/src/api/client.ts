import type {
  BindCodeResponse,
  ConfigAuditLogsResponse,
  ConfigSnapshotState,
  DeviceItem,
  DevicesResponse,
  LoginResponse,
  MeResponse,
  RecordItem,
  RecordsResponse,
  SystemInfoState
} from '../types'
import { ConfigConflictError } from '../configSnapshot'
import { translateStatic } from '../i18n'

let csrfToken = ''
const DEFAULT_TIMEOUT_MS = 8_000

type RequestOptions = {
  requiresCsrf?: boolean
  timeoutMs?: number
}

export function setCsrfToken(nextToken: string): void {
  csrfToken = nextToken
}

function extractErrorMessage(text: string, status: number): string {
  const trimmed = text.trim()
  if (!trimmed) {
    return `${translateStatic('common.requestFailed')}${status}`
  }

  try {
    const parsed = JSON.parse(trimmed) as { error?: string }
    if (parsed.error?.trim()) {
      return parsed.error.trim()
    }
  } catch {
    // Fall back to raw text below when the payload is not JSON.
  }

  return trimmed
}

function normalizeRequestError(error: unknown): Error {
  if (error instanceof DOMException && error.name === 'AbortError') {
    return new Error('Connection timed out. Make sure the remote backend is still active.')
  }
  if (error instanceof TypeError) {
    return new Error('Unable to connect to the remote backend.')
  }
  return error instanceof Error ? error : new Error(translateStatic('common.requestFailed'))
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {}
): Promise<T> {
  const { requiresCsrf = false, timeoutMs = DEFAULT_TIMEOUT_MS } = options
  const headers = new Headers(init.headers ?? {})
  const method = (init.method ?? 'GET').toUpperCase()
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (requiresCsrf && method !== 'GET') {
    headers.set('X-CSRF-Token', csrfToken)
  }

  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const resp = await fetch(path, {
      ...init,
      headers,
      credentials: 'include',
      signal: controller.signal
    })

    if (!resp.ok) {
      const text = await resp.text()
      throw new Error(extractErrorMessage(text, resp.status))
    }

    return (await resp.json()) as T
  } catch (error) {
    throw normalizeRequestError(error)
  } finally {
    window.clearTimeout(timeoutId)
  }
}

export const apiClient = {
  bootstrapAdmin: (username: string, password: string) =>
    request<{ ok: boolean; userId: number; username: string }>('/api/v1/bootstrap/admin', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    }),

  login: (username: string, password: string) =>
    request<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    }, { timeoutMs: 6_000 }),

  changePassword: (currentPassword: string, newPassword: string) =>
    request('/api/v1/auth/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword })
    }, { requiresCsrf: true }),

  me: () => request<MeResponse>('/api/v1/auth/me', {}, { timeoutMs: 2_500 }),

  logout: () =>
    request('/api/v1/auth/logout', {
      method: 'POST'
    }, { requiresCsrf: true }),

  getSystemInfo: () => request<SystemInfoState>('/api/v1/system/info'),

  getDevices: () => request<DevicesResponse>('/api/v1/devices'),

  patchDevice: (deviceId: number, payload: Partial<Pick<DeviceItem, 'displayName' | 'enabled'>>) =>
    request<DeviceItem>(`/api/v1/devices/${deviceId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, { requiresCsrf: true }),

  revokeDevice: (deviceId: number) =>
    request('/api/v1/devices/' + deviceId + '/revoke', {
      method: 'POST'
    }, { requiresCsrf: true }),

  createBindCode: () =>
    request<BindCodeResponse>('/api/v1/devices/bind-codes', {
      method: 'POST'
    }, { requiresCsrf: true }),

  getConfigSnapshot: () => request<ConfigSnapshotState>('/api/v1/config/snapshot'),

  putConfigSnapshot: (baseRevision: number, snapshot: Record<string, unknown>) =>
    (async () => {
      const headers = new Headers({ 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken })
      const controller = new AbortController()
      const timeoutId = window.setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS)

      try {
        const resp = await fetch('/api/v1/config/snapshot', {
          method: 'PUT',
          credentials: 'include',
          headers,
          body: JSON.stringify({
            base_revision: baseRevision,
            snapshot
          }),
          signal: controller.signal
        })

        const text = await resp.text()
        if (resp.status === 409) {
          const latest = JSON.parse(text) as ConfigSnapshotState
          throw new ConfigConflictError('Cloud config changed on another client. Reloaded the latest revision.', latest)
        }
        if (!resp.ok) {
          throw new Error(extractErrorMessage(text, resp.status))
        }

        return JSON.parse(text) as ConfigSnapshotState
      } catch (error) {
        throw normalizeRequestError(error)
      } finally {
        window.clearTimeout(timeoutId)
      }
    })(),

  getConfigAuditLogs: (limit = 50, offset = 0) =>
    request<ConfigAuditLogsResponse>(`/api/v1/config/audit?limit=${limit}&offset=${offset}`),

  getRecords: (limit = 80, deviceId?: number) =>
    request<RecordsResponse>(`/api/v1/records?limit=${limit}${deviceId ? `&device_id=${deviceId}` : ''}`),

  getRecord: (recordId: number) => request<RecordItem>(`/api/v1/records/${recordId}`)
}
