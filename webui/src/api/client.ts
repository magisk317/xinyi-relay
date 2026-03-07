import type {
  AdvancedState,
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

let csrfToken = ''

export function setCsrfToken(nextToken: string): void {
  csrfToken = nextToken
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  requiresCsrf = false
): Promise<T> {
  const headers = new Headers(init.headers ?? {})
  const method = (init.method ?? 'GET').toUpperCase()
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (requiresCsrf && method !== 'GET') {
    headers.set('X-CSRF-Token', csrfToken)
  }

  const resp = await fetch(path, {
    ...init,
    headers,
    credentials: 'include'
  })

  if (!resp.ok) {
    const text = await resp.text()
    throw new Error(text || `HTTP ${resp.status}`)
  }
  return (await resp.json()) as T
}

export const apiClient = {
  login: (username: string, password: string) =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    }),

  me: () => request<MeResponse>('/auth/me'),

  logout: () =>
    request('/auth/logout', {
      method: 'POST'
    }, true),

  getOverview: () => request<OverviewState>('/api/v1/overview'),
  getApps: () => request<AppItem[]>('/api/v1/apps'),
  patchApp: (packageName: string, payload: Partial<AppItem>) =>
    request<AppItem>(`/api/v1/apps/${encodeURIComponent(packageName)}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, true),

  getRecords: (limit = 80) => request<RecordItem[]>(`/api/v1/records?limit=${limit}`),
  deleteRecord: (recordId: number) =>
    request(`/api/v1/records/${recordId}`, { method: 'DELETE' }, true),

  getSenders: () => request<SenderItem[]>('/api/v1/senders'),
  createSender: (payload: Partial<SenderItem>) =>
    request<SenderItem>('/api/v1/senders', {
      method: 'POST',
      body: JSON.stringify(payload)
    }, true),
  patchSender: (senderId: number, payload: Partial<SenderItem>) =>
    request<SenderItem>(`/api/v1/senders/${senderId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, true),
  deleteSender: (senderId: number) =>
    request(`/api/v1/senders/${senderId}`, { method: 'DELETE' }, true),

  getSettings: () => request<SettingsState>('/api/v1/settings'),
  patchSettings: (payload: Partial<SettingsState>) =>
    request<SettingsState>('/api/v1/settings', {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, true),

  getAdvanced: () => request<AdvancedState>('/api/v1/advanced'),
  patchAdvanced: (payload: Partial<AdvancedState>) =>
    request<AdvancedState>('/api/v1/advanced', {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, true),

  getIntercept: () => request<InterceptState>('/api/v1/intercept'),
  patchIntercept: (payload: Partial<InterceptState>) =>
    request<InterceptState>('/api/v1/intercept', {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }, true),

  getVersion: () => request<VersionState>('/api/v1/version')
}
