import type {
  AdvancedState,
  AnalyticsResponse,
  AppItem,
  InterceptState,
  LoginResponse,
  MeResponse,
  OverviewState,
  RecordItem,
  SenderItem,
  SettingsState,
  VersionState
} from '../types'
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
    return new Error('Connection timed out. Make sure the host app or WebUI service is still active.')
  }
  if (error instanceof TypeError) {
    return new Error('Unable to connect to WebUI. Make sure the app or WebUI service is still running.')
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
  login: (username: string, password: string) =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    }, { timeoutMs: 6_000 }),

  me: () => request<MeResponse>('/auth/me', {}, { timeoutMs: 2_500 }),

  logout: () =>
    request('/auth/logout', {
      method: 'POST'
    }, { requiresCsrf: true }),

  getOverview: () => request<OverviewState>('/api/v1/overview'),
  getApps: () => request<AppItem[]>('/api/v1/apps'),
  patchApp: (packageName: string, payload: Partial<AppItem>) =>
    request<AppItem>(`/api/v1/apps/${encodeURIComponent(packageName)}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, { requiresCsrf: true }),

  getRecords: (limit = 80) => request<RecordItem[]>(`/api/v1/records?limit=${limit}`),
  deleteRecord: (recordId: number) =>
    request(`/api/v1/records/${recordId}`, { method: 'DELETE' }, { requiresCsrf: true }),

  getSenders: () => request<SenderItem[]>('/api/v1/senders'),
  createSender: (payload: Partial<SenderItem>) =>
    request<SenderItem>('/api/v1/senders', {
      method: 'POST',
      body: JSON.stringify(payload)
    }, { requiresCsrf: true }),
  patchSender: (senderId: number, payload: Partial<SenderItem>) =>
    request<SenderItem>(`/api/v1/senders/${senderId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, { requiresCsrf: true }),
  deleteSender: (senderId: number) =>
    request(`/api/v1/senders/${senderId}`, { method: 'DELETE' }, { requiresCsrf: true }),

  getSettings: () => request<SettingsState>('/api/v1/settings'),
  patchSettings: (payload: Partial<SettingsState>) =>
    request<SettingsState>('/api/v1/settings', {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, { requiresCsrf: true }),

  getAdvanced: () => request<AdvancedState>('/api/v1/advanced'),
  patchAdvanced: (payload: Partial<AdvancedState>) =>
    request<AdvancedState>('/api/v1/advanced', {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, { requiresCsrf: true }),

  getAnalytics: () => request<AnalyticsResponse>('/api/v1/analytics'),

  getIntercept: () => request<InterceptState>('/api/v1/intercept'),
  patchIntercept: (payload: Partial<InterceptState>) =>
    request<InterceptState>('/api/v1/intercept', {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, { requiresCsrf: true }),

  getVersion: () => request<VersionState>('/api/v1/version')
}
