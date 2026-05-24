import type {
  ConfigSnapshotState,
  RemoteConfigRoot,
  SnapshotAppInfo,
  SnapshotForwardFilterRule,
  SnapshotNotifyRouteRule,
  SnapshotRule,
  SnapshotSender,
  SnapshotSmsCodeRule
} from './contracts/console'

export class ConfigConflictError extends Error {
  latest: ConfigSnapshotState

  constructor(message: string, latest: ConfigSnapshotState) {
    super(message)
    this.name = 'ConfigConflictError'
    this.latest = latest
  }
}

export type NormalizedConfigSnapshot = {
  config: ConfigSnapshotState
  root: RemoteConfigRoot
}

export type ConfigSnapshotLoader = () => Promise<ConfigSnapshotState>

export type ConfigSnapshotSaver = (
  baseRevision: number,
  snapshot: RemoteConfigRoot
) => Promise<ConfigSnapshotState>

export function cloneSnapshot(snapshot: Record<string, unknown>): RemoteConfigRoot {
  return JSON.parse(JSON.stringify(snapshot)) as RemoteConfigRoot
}

export async function loadNormalizedConfigSnapshot(
  loadSnapshot: ConfigSnapshotLoader
): Promise<NormalizedConfigSnapshot> {
  return normalizeConfigSnapshotState(await loadSnapshot())
}

export async function saveNormalizedConfigSnapshot(
  current: ConfigSnapshotState | null,
  nextRoot: RemoteConfigRoot,
  saveSnapshot: ConfigSnapshotSaver
): Promise<NormalizedConfigSnapshot> {
  if (!current) {
    throw new Error('Cloud snapshot is not loaded yet.')
  }
  return normalizeConfigSnapshotState(await saveSnapshot(current.revision, nextRoot))
}

export function normalizeConfigSnapshotState(config: ConfigSnapshotState): NormalizedConfigSnapshot {
  return {
    config,
    root: normalizeConfigRoot(config.snapshot)
  }
}

export function normalizeConfigSnapshotError(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

export function latestConfigSnapshotFromError(error: unknown): NormalizedConfigSnapshot | null {
  return error instanceof ConfigConflictError ? normalizeConfigSnapshotState(error.latest) : null
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
