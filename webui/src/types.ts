export interface ErrorResponse {
  error: string
}

export interface LoginResponse {
  authenticated: boolean
  username: string
  csrfToken: string
}

export interface MeResponse {
  authenticated: boolean
  username?: string
  csrfToken?: string
}

export interface VersionState {
  localVersionName: string
  localVersionCode: number
  latestVersionName?: string
  latestVersionCode?: number
  releaseUrl?: string
  updateAvailable?: boolean
  status: string
  message?: string
  checkedAt: number
}

export interface OverviewState {
  appCount: number
  blockedCount: number
  forwardingCount: number
  recordCount: number
  senderTotal: number
  senderEnabled: number
  senderAppNotifyEnabled: number
  version: VersionState
}

export interface AppItem {
  packageName: string
  label: string
  blocked: boolean
  forwarding: boolean
  notifyTemplate: string
}

export interface RecordItem {
  id: number
  date: number
  sender: string
  body: string
  smsCode: string
  packageName: string
  msgType: number
  callType: number
  forwardStatus: number
  forwardTarget: string
  forwardMessage: string
}

export interface SenderItem {
  id: number
  name: string
  type: number
  typeLabel: string
  jsonSetting: string
  status: boolean
  receiveCode: boolean
  receiveNonCode: boolean
  receiveAppNotify: boolean
  receiveCallNotify: boolean
}

export interface SettingsState {
  enable: boolean
  copyToClipboard: boolean
  showToast: boolean
  showCodeNotification: boolean
  enableAutoInputCode: boolean
  enableAutoEnterCode: boolean
  verboseLogMode: boolean
  blockSms: boolean
  forceStopRecovery: boolean
}

export interface AdvancedState {
  enableSmsBlacklist: boolean
  webUiLanAccess: boolean
  senderTotal: number
  senderEnabled: number
  senderAppNotifyEnabled: number
}

export interface InterceptState {
  smsBlacklistNumbers: string
  smsBlacklistPrefixes: string
  smsBlacklistRegex: string
  smsBlacklistContent: string
  smsBlacklistActionDelete: boolean
  smsBlacklistActionBlock: boolean
}
