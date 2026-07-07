import type {
  DesktopSessionResponse,
  DeviceConfigCommandItem,
  DeviceConfigStateResponse,
  RealtimeEvent as OpenApiRealtimeEvent,
  RelayRecord,
  SystemInfoResponse
} from './console.generated'
import type {
  SnapshotAppInfo,
  SnapshotSender,
} from './configRoot.generated'

export type {
  AgentConfigCommandsAckRequest,
  AgentConfigCommandsPullRequest,
  AgentConfigCommandsPullResponse,
  AgentConfigMirrorRequest,
  AgentRegisterRequest,
  AgentRegisterResponse,
  BindCodeResponse,
  BootstrapAdminRequest,
  BootstrapAdminResponse,
  ChangePasswordRequest,
  DesktopExchangeRequest,
  DesktopLoginPageRequest,
  DesktopLogoutRequest,
  DesktopRefreshRequest,
  DesktopSessionResponse,
  DeviceConfigAuditLogItem,
  DeviceConfigAuditLogsResponse,
  DeviceConfigCommandItem,
  DeviceConfigCommandRequest,
  DeviceConfigStateResponse,
  DeviceItem,
  DevicesResponse,
  ErrorResponse,
  HealthResponse,
  HeartbeatRequest,
  JsonObject,
  LoginRequest,
  LoginResponse,
  MeResponse,
  PatchDeviceRequest,
  RecordsResponse,
  RelayRecord,
  RelayRecordWire,
  RelayRecordsBatchRequest,
  RelayRecordsBatchResponse,
  SimpleOKResponse,
  SystemInfoResponse
} from './console.generated'

export type {
  ForwardCommonConfig,
  ForwardSilentPeriodConfig,
  RemoteConfigRoot,
  SnapshotAppInfo,
  SnapshotForwardFilterRule,
  SnapshotNotifyRouteRule,
  SnapshotOverviewSettings,
  SnapshotRule,
  SnapshotSender,
  SnapshotSmsCodeRule,
} from './configRoot.generated'

export type SystemInfoState = SystemInfoResponse

export interface ReplaceSendersConfigMutationOperation {
  type: 'replace_senders'
  senders: SnapshotSender[]
  removedSenderIds?: number[]
}

export interface ReplaceDeviceAppsConfigMutationOperation {
  type: 'replace_device_apps'
  deviceId: number
  apps: SnapshotAppInfo[]
}

export type ConfigMutationOperation =
  | ReplaceSendersConfigMutationOperation
  | ReplaceDeviceAppsConfigMutationOperation

export interface ConfigMutationBatch {
  operations: ConfigMutationOperation[]
}

export interface DeviceConfigCommandState extends Omit<DeviceConfigCommandItem, 'mutation'> {
  mutation: ConfigMutationBatch
}

export interface DeviceConfigState extends Omit<DeviceConfigStateResponse, 'mirrorContent' | 'pendingCommands'> {
  mirrorContent: Record<string, unknown>
  pendingCommands: DeviceConfigCommandState[]
}

export type RecordItem = RelayRecord

export type RunMode = 'local' | 'remote' | 'hybrid'

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'degraded' | 'local' | 'hybrid'

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

export type DesktopAuthExchangeResponse = DesktopSessionResponse

export interface DesktopDiagnosticsExport {
  path: string
  createdAt: string
}

export type SyncResultType = 'upToDate' | 'pulled' | 'pushed' | 'conflict'

export interface SyncResult {
  UpToDate?: {}
  Pulled?: { newRevision: number }
  Pushed?: { newRevision: number }
  Conflict?: { localRevision: number; remoteRevision: number }
}

export interface SyncReport {
  config: SyncResult
  devicesSynced: number
  recordsSynced: number
  error?: string | null
}

export type RealtimeEventType = OpenApiRealtimeEvent['type'] | 'sync.completed'

export interface RealtimeEvent extends Omit<OpenApiRealtimeEvent, 'type'> {
  type: RealtimeEventType
}
