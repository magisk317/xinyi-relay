import type {
  BindCodeResponse,
  ConfigMutationBatch,
  DeviceConfigAuditLogsResponse,
  DeviceConfigCommandState,
  DeviceConfigState,
  DeviceItem,
  DevicesResponse,
  LoginResponse,
  MeResponse,
  RecordItem,
  RecordsResponse,
  SystemInfoState
} from './contracts/console'

export type BootstrapAdminResponse = {
  ok: boolean
  userId: number
  username: string
}

export type ConsoleApiRequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT'
  body?: unknown
  requiresCsrf?: boolean
  timeoutMs?: number
}

export type ConsoleApiTransport = {
  request<T>(path: string, options?: ConsoleApiRequestOptions): Promise<T>
}

export type ConsoleApiClient = ReturnType<typeof createConsoleApiClient>

export function createConsoleApiClient(transport: ConsoleApiTransport) {
  return {
    bootstrapAdmin: (username: string, password: string) =>
      transport.request<BootstrapAdminResponse>('/api/v1/bootstrap/admin', {
        method: 'POST',
        body: { username, password }
      }),

    login: (username: string, password: string) =>
      transport.request<LoginResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: { username, password },
        timeoutMs: 6_000
      }),

    changePassword: (currentPassword: string, newPassword: string) =>
      transport.request('/api/v1/auth/password', {
        method: 'POST',
        body: { currentPassword, newPassword },
        requiresCsrf: true
      }),

    me: () => transport.request<MeResponse>('/api/v1/auth/me', { timeoutMs: 2_500 }),

    logout: () =>
      transport.request('/api/v1/auth/logout', {
        method: 'POST',
        requiresCsrf: true
      }),

    getSystemInfo: () => transport.request<SystemInfoState>('/api/v1/system/info'),

    getDevices: () => transport.request<DevicesResponse>('/api/v1/devices'),

    patchDevice: (deviceId: number, payload: Partial<Pick<DeviceItem, 'displayName' | 'enabled'>>) =>
      transport.request<DeviceItem>(`/api/v1/devices/${deviceId}`, {
        method: 'PATCH',
        body: payload,
        requiresCsrf: true
      }),

    revokeDevice: (deviceId: number) =>
      transport.request(`/api/v1/devices/${deviceId}/revoke`, {
        method: 'POST',
        requiresCsrf: true
      }),

    createBindCode: () =>
      transport.request<BindCodeResponse>('/api/v1/devices/bind-codes', {
        method: 'POST',
        requiresCsrf: true
      }),

    getDeviceConfig: (deviceId: number) =>
      transport.request<DeviceConfigState>(`/api/v1/devices/${deviceId}/config`),

    queueDeviceConfigCommand: (
      deviceId: number,
      baseRevision: number,
      mutation: ConfigMutationBatch,
      summary: string
    ) =>
      transport.request<DeviceConfigCommandState>(`/api/v1/devices/${deviceId}/config/commands`, {
        method: 'POST',
        body: {
          baseRevision,
          mutation,
          summary
        },
        requiresCsrf: true
      }),

    getDeviceConfigAuditLogs: (deviceId: number, limit = 50, offset = 0) =>
      transport.request<DeviceConfigAuditLogsResponse>(
        `/api/v1/devices/${deviceId}/config/audit?${buildQuery({ limit, offset })}`
      ),

    getRecords: (limit = 80, deviceId?: number) =>
      transport.request<RecordsResponse>(`/api/v1/records?${buildQuery({ limit, device_id: deviceId })}`),

    getRecord: (recordId: number) => transport.request<RecordItem>(`/api/v1/records/${recordId}`)
  }
}

function buildQuery(params: Record<string, string | number | undefined>): string {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
}
