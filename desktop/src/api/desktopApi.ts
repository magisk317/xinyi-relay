import { invoke } from '@tauri-apps/api/core'
import type {
  BindCodeResponse,
  ConfigAuditLogsResponse,
  ConfigSnapshotState,
  DesktopAuthExchangeResponse,
  DesktopAuthStart,
  DesktopBackendProbe,
  DesktopBootstrapState,
  DesktopDiagnosticsExport,
  DesktopNotificationPreferences,
  DevicesResponse,
  RecordsResponse,
  SystemInfoState
} from '../../../shared/contracts/console'

export type SaveProfileInput = {
  id?: string
  name: string
  baseUrl: string
  allowSelfSigned: boolean
  note?: string
}

export const desktopApi = {
  bootstrap: () => invoke<DesktopBootstrapState>('desktop_bootstrap'),
  saveProfile: (profile: SaveProfileInput) => invoke<DesktopBootstrapState>('desktop_save_profile', { profile }),
  deleteProfile: (profileId: string) => invoke<DesktopBootstrapState>('desktop_delete_profile', { profileId }),
  setActiveProfile: (profileId: string) => invoke<DesktopBootstrapState>('desktop_set_active_profile', { profileId }),
  probeBackend: (profileId: string) => invoke<DesktopBackendProbe>('desktop_probe_backend', { profileId }),
  startBrowserLogin: (profileId: string) =>
    invoke<DesktopAuthStart>('desktop_start_browser_login', { profileId }),
  openExternalUrl: (url: string) => invoke('desktop_open_external_url', { url }),
  exchangeBrowserLogin: (code: string, state: string) =>
    invoke<DesktopAuthExchangeResponse>('desktop_exchange_browser_login', { code, state }),
  logout: () => invoke<DesktopBootstrapState>('desktop_logout'),
  fetchSystemInfo: () => invoke<SystemInfoState>('desktop_fetch_system_info'),
  fetchDevices: () => invoke<DevicesResponse>('desktop_fetch_devices'),
  createBindCode: () => invoke<BindCodeResponse>('desktop_create_bind_code'),
  patchDevice: (deviceId: number, payload: { displayName?: string; enabled?: boolean }) =>
    invoke('desktop_patch_device', { deviceId, payload }),
  revokeDevice: (deviceId: number) => invoke('desktop_revoke_device', { deviceId }),
  fetchConfigSnapshot: () => invoke<ConfigSnapshotState>('desktop_fetch_config_snapshot'),
  putConfigSnapshot: (baseRevision: number, snapshot: Record<string, unknown>) =>
    invoke<ConfigSnapshotState>('desktop_put_config_snapshot', { baseRevision, snapshot }),
  fetchConfigAuditLogs: (limit = 50, offset = 0) =>
    invoke<ConfigAuditLogsResponse>('desktop_fetch_config_audit_logs', { limit, offset }),
  fetchRecords: (limit = 80, deviceId?: number) =>
    invoke<RecordsResponse>('desktop_fetch_records', { limit, deviceId }),
  exportDiagnostics: () => invoke<DesktopDiagnosticsExport>('desktop_export_diagnostics'),
  updateNotifications: (preferences: DesktopNotificationPreferences) =>
    invoke<DesktopBootstrapState>('desktop_update_notifications', { preferences }),
  sendTestNotification: () => invoke('desktop_send_test_notification')
}
