import { invoke } from '@tauri-apps/api/core'
import { createConsoleApiClient, type ConsoleApiRequestOptions } from '../../../shared/consoleApiClient'
import { ConfigConflictError } from '../../../shared/configSnapshot'
import type {
  ConfigSnapshotState,
  DesktopAuthExchangeResponse,
  DesktopAuthStart,
  DesktopBackendProbe,
  DesktopBootstrapState,
  DesktopDiagnosticsExport,
  DesktopNotificationPreferences,
  RunMode,
  SyncReport
} from '../../../shared/contracts/console'

export type SaveProfileInput = {
  id?: string
  name: string
  baseUrl: string
  allowSelfSigned: boolean
  note?: string
}

const desktopConsoleApi = createConsoleApiClient({
  request: requestDesktopConsoleApi
})

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
  getSystemInfo: desktopConsoleApi.getSystemInfo,
  getDevices: desktopConsoleApi.getDevices,
  createBindCode: desktopConsoleApi.createBindCode,
  patchDevice: desktopConsoleApi.patchDevice,
  revokeDevice: desktopConsoleApi.revokeDevice,
  getConfigSnapshot: desktopConsoleApi.getConfigSnapshot,
  putConfigSnapshot: desktopConsoleApi.putConfigSnapshot,
  getConfigAuditLogs: desktopConsoleApi.getConfigAuditLogs,
  getRecords: desktopConsoleApi.getRecords,
  getRecord: desktopConsoleApi.getRecord,
  getLocalServerAddr: () => invoke<string | null>('desktop_get_local_server_addr'),
  exportDiagnostics: () => invoke<DesktopDiagnosticsExport>('desktop_export_diagnostics'),
  exportDatabase: () => invoke<string>('desktop_export_database'),
  importDatabase: (sourcePath: string) => invoke<string>('desktop_import_database', { sourcePath }),
  updateNotifications: (preferences: DesktopNotificationPreferences) =>
    invoke<DesktopBootstrapState>('desktop_update_notifications', { preferences }),
  sendTestNotification: () => invoke('desktop_send_test_notification'),
  getRunMode: () => invoke<RunMode>('desktop_get_run_mode'),
  switchRunMode: (mode: RunMode) => invoke<DesktopBootstrapState>('desktop_switch_run_mode', { mode }),
  sync: (direction: 'pull' | 'push') => invoke<SyncReport>('desktop_sync', { direction })
}

async function requestDesktopConsoleApi<T>(
  path: string,
  options: ConsoleApiRequestOptions = {}
): Promise<T> {
  const method = options.method ?? 'GET'
  const { pathname, params } = parseConsolePath(path)

  if (method === 'GET' && pathname === '/api/v1/system/info') {
    return invoke<T>('desktop_fetch_system_info')
  }
  if (method === 'GET' && pathname === '/api/v1/devices') {
    return invoke<T>('desktop_fetch_devices')
  }
  if (method === 'POST' && pathname === '/api/v1/devices/bind-codes') {
    return invoke<T>('desktop_create_bind_code')
  }

  const deviceMatch = pathname.match(/^\/api\/v1\/devices\/(\d+)(\/revoke)?$/)
  if (deviceMatch && method === 'PATCH' && !deviceMatch[2]) {
    return invoke<T>('desktop_patch_device', {
      deviceId: Number(deviceMatch[1]),
      payload: options.body ?? {}
    })
  }
  if (deviceMatch && method === 'POST' && deviceMatch[2] === '/revoke') {
    return invoke<T>('desktop_revoke_device', { deviceId: Number(deviceMatch[1]) })
  }

  if (method === 'GET' && pathname === '/api/v1/config/snapshot') {
    return invoke<T>('desktop_fetch_config_snapshot')
  }
  if (method === 'PUT' && pathname === '/api/v1/config/snapshot') {
    const body = asRecord(options.body)
    try {
      return await invoke<T>('desktop_put_config_snapshot', {
        baseRevision: Number(body.base_revision),
        snapshot: asRecord(body.snapshot)
      })
    } catch (err: unknown) {
      throw mapConflictError(err, options.conflictMessage)
    }
  }
  if (method === 'GET' && pathname === '/api/v1/config/audit') {
    return invoke<T>('desktop_fetch_config_audit_logs', {
      limit: numericQuery(params, 'limit', 50),
      offset: numericQuery(params, 'offset', 0)
    })
  }
  if (method === 'GET' && pathname === '/api/v1/records') {
    return invoke<T>('desktop_fetch_records', {
      limit: numericQuery(params, 'limit', 80),
      deviceId: optionalNumericQuery(params, 'device_id')
    })
  }

  const recordMatch = pathname.match(/^\/api\/v1\/records\/(\d+)$/)
  if (recordMatch && method === 'GET') {
    return invoke<T>('desktop_fetch_record', { recordId: Number(recordMatch[1]) })
  }

  throw new Error(`Unsupported desktop console API request: ${method} ${pathname}`)
}

function parseConsolePath(path: string): { pathname: string; params: URLSearchParams } {
  const [pathname, query = ''] = path.split('?')
  return {
    pathname,
    params: new URLSearchParams(query)
  }
}

function numericQuery(params: URLSearchParams, name: string, fallback: number): number {
  return optionalNumericQuery(params, name) ?? fallback
}

function optionalNumericQuery(params: URLSearchParams, name: string): number | undefined {
  const value = params.get(name)
  if (value === null || value === '') return undefined
  return Number(value)
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

/**
 * Detect structured conflict errors returned by the Rust backend (409 responses)
 * and throw a ConfigConflictError so the UI can reload the editor with the cloud version.
 */
function mapConflictError(err: unknown, conflictMessage?: string): unknown {
  if (typeof err === 'string') {
    try {
      const parsed = JSON.parse(err)
      if (parsed.__config_conflict__ && parsed.latest) {
        throw new ConfigConflictError(
          conflictMessage ?? parsed.message ?? 'Config conflict',
          parsed.latest as ConfigSnapshotState
        )
      }
    } catch (parseErr) {
      if (parseErr instanceof ConfigConflictError) throw parseErr
      // not JSON, fall through
    }
  }
  throw err
}
