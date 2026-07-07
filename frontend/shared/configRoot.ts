import type {
  RemoteConfigRoot,
  SnapshotAppInfo,
  SnapshotForwardFilterRule,
  SnapshotNotifyRouteRule,
  SnapshotRule,
  SnapshotSender,
  SnapshotSmsCodeRule
} from './contracts/console'

export function cloneConfigRoot(snapshot: Record<string, unknown>): RemoteConfigRoot {
  return JSON.parse(JSON.stringify(snapshot)) as RemoteConfigRoot
}

export function normalizeConfigRoot(snapshot: Record<string, unknown>): RemoteConfigRoot {
  const root = cloneConfigRoot(snapshot)
  root.senders = normalizeArray<SnapshotSender>(root.senders)
  root.rules = normalizeArray<SnapshotRule>(root.rules)
  root.deviceAppInfos = normalizeDeviceAppInfos(root.deviceAppInfos)
  root.smsCodeRules = normalizeArray<SnapshotSmsCodeRule>(root.smsCodeRules)
  root.notifyRoutes = normalizeArray<SnapshotNotifyRouteRule>(root.notifyRoutes)
  root.forwardFilters = normalizeArray<SnapshotForwardFilterRule>(root.forwardFilters)
  return root
}

function normalizeArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : []
}

function normalizeDeviceAppInfos(value: unknown): Record<string, SnapshotAppInfo[]> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return {}
  }
  const result: Record<string, SnapshotAppInfo[]> = {}
  for (const [key, val] of Object.entries(value)) {
    result[key] = normalizeArray<SnapshotAppInfo>(val)
  }
  return result
}
