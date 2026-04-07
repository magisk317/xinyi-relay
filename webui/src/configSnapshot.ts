import type {
  ConfigSnapshotState,
  RemoteConfigRoot,
  SnapshotAppInfo,
  SnapshotForwardFilterRule,
  SnapshotNotifyRouteRule,
  SnapshotRule,
  SnapshotSender,
  SnapshotSmsCodeRule
} from './types'

export class ConfigConflictError extends Error {
  latest: ConfigSnapshotState

  constructor(message: string, latest: ConfigSnapshotState) {
    super(message)
    this.name = 'ConfigConflictError'
    this.latest = latest
  }
}

export function cloneSnapshot(snapshot: Record<string, unknown>): RemoteConfigRoot {
  return JSON.parse(JSON.stringify(snapshot)) as RemoteConfigRoot
}

export function normalizeConfigRoot(snapshot: Record<string, unknown>): RemoteConfigRoot {
  const root = cloneSnapshot(snapshot)
  root.senders = normalizeArray<SnapshotSender>(root.senders)
  root.rules = normalizeArray<SnapshotRule>(root.rules)
  root.appInfos = normalizeArray<SnapshotAppInfo>(root.appInfos)
  root.smsCodeRules = normalizeArray<SnapshotSmsCodeRule>(root.smsCodeRules)
  root.notifyRoutes = normalizeArray<SnapshotNotifyRouteRule>(root.notifyRoutes)
  root.forwardFilters = normalizeArray<SnapshotForwardFilterRule>(root.forwardFilters)
  return root
}

function normalizeArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : []
}
