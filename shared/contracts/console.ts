export interface ErrorResponse {
  error: string
}

export interface LoginResponse {
  authenticated: boolean
  username: string
  csrfToken: string
  languageTag?: string
}

export interface MeResponse {
  authenticated: boolean
  username?: string
  csrfToken?: string
  languageTag?: string
}

export interface SystemInfoState {
  service: string
  appEnv: string
  localBaseUrl: string
  publicBaseUrl: string
  databaseReady: boolean
  userCount: number
  time: string
}

export interface DeviceItem {
  id: number
  userId: number
  deviceName: string
  deviceModel: string
  platform: string
  appVersion: string
  displayName: string
  enabled: boolean
  revokedAt?: string | null
  lastSeenAt?: string | null
  localAddresses: unknown
  capabilities: unknown
  createdAt: string
  updatedAt: string
}

export interface DevicesResponse {
  devices: DeviceItem[]
}

export interface BindCodeResponse {
  code: string
  expiresAt: string
}

export interface ConfigSnapshotState {
  revision: number
  snapshot: Record<string, unknown>
}

export interface ConfigAuditLogItem {
  id: number
  revision: number
  actorType: string
  actorId: number
  summary: string
  createdAt: string
}

export interface ConfigAuditLogsResponse {
  logs: ConfigAuditLogItem[]
  limit: number
  offset: number
}

export interface SnapshotSender {
  id: number
  type: number
  name: string
  jsonSetting: string
  status: number
  receiveCode: number
  receiveNonCode: number
  receiveAppNotify: number
  receiveCallNotify: number
}

export interface SnapshotRule {
  id: number
  type: string
  filed: string
  check: string
  value: string
  senderId: number
  title: string
  status: number
}

export interface SnapshotAppInfo {
  packageName: string
  label?: string | null
  blocked: boolean
  forwarding: boolean
  forwardingConfigured: boolean
  notifyTemplate: string
}

export interface SnapshotSmsCodeRule {
  id: number
  company?: string | null
  codeKeyword: string
  codeRegex: string
}

export interface SnapshotNotifyRouteRule {
  id: number
  scope: number
  packageName: string
  senderId: number
  updateTime: number
}

export interface SnapshotForwardFilterRule {
  id: number
  msgType: string
  scopeType: string
  scopeKey: string
  senderId: number
  policy: string
  matchMode: string
  pattern: string
  enabled: number
  updateTime: number
}

export interface SnapshotOverviewSettings {
  cardOrder: string
  enabledCardIds: string
  chartType: string
  chartWindow: string
}

export interface RemoteConfigRoot extends Record<string, unknown> {
  senders?: SnapshotSender[]
  rules?: SnapshotRule[]
  appInfos?: SnapshotAppInfo[]
  smsCodeRules?: SnapshotSmsCodeRule[]
  notifyRoutes?: SnapshotNotifyRouteRule[]
  forwardFilters?: SnapshotForwardFilterRule[]
  overview?: SnapshotOverviewSettings
}

export interface RecordItem {
  id: number
  deviceId: number
  eventId?: string
  recordType: string
  sender: string
  body: string
  smsCode: string
  packageName: string
  metadata: unknown
  msgType: number
  callType: number
  occurredAt: string
  uploadedAt: string
}

export interface RecordsResponse {
  records: RecordItem[]
  limit: number
  offset: number
}

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'degraded'

export interface DesktopProfile {
  id: string
  name: string
  baseUrl: string
  allowSelfSigned: boolean
  trustedFingerprint?: string | null
  trustedIssuer?: string | null
  active: boolean
  lastConnectedAt?: string | null
  note?: string | null
}

export interface DesktopNotificationPreferences {
  enabled: boolean
  connection: boolean
  records: boolean
  devices: boolean
}

export interface DesktopSessionState {
  authenticated: boolean
  username?: string
  expiresAt?: string | null
  refreshExpiresAt?: string | null
}

export interface DesktopConnectionSnapshot {
  state: ConnectionState
  message: string
  lastChangedAt?: string | null
}

export interface DesktopBootstrapState {
  appVersion: string
  platform: string
  languageTag?: string
  profiles: DesktopProfile[]
  session: DesktopSessionState
  notifications: DesktopNotificationPreferences
  connection: DesktopConnectionSnapshot
}

export interface DesktopBackendProbe {
  reachable: boolean
  allowSelfSigned: boolean
  certificateStatus: 'verified' | 'local_override' | 'untrusted' | 'unavailable'
  message: string
  systemInfo?: SystemInfoState | null
}

export interface DesktopAuthStart {
  authUrl: string
  state: string
  callbackUrl: string
}

export interface DesktopAuthCallbackPayload {
  state: string
  code?: string
  error?: string
}

export interface DesktopAuthExchangeResponse {
  authenticated: boolean
  username: string
  accessToken: string
  refreshToken: string
  expiresAt: string
  refreshExpiresAt: string
}

export interface DesktopDiagnosticsExport {
  path: string
  createdAt: string
}

export type RealtimeEventType =
  | 'device.registered'
  | 'device.updated'
  | 'device.revoked'
  | 'device.heartbeat'
  | 'records.ingested'
  | 'config.updated'

export interface RealtimeEvent {
  type: RealtimeEventType
  time: string
  data: Record<string, unknown> | null
}
