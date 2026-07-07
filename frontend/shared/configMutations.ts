import {
  cloneConfigRoot,
  normalizeConfigRoot,
} from './configRoot'
import type {
  ConfigMutationBatch,
  ConfigMutationOperation,
  RemoteConfigRoot,
  SnapshotAppInfo,
  SnapshotSender,
} from './contracts/console'

export function buildReplaceSendersMutation(
  senders: SnapshotSender[],
  removedSenderIds: number[] = [],
): ConfigMutationBatch {
  return {
    operations: [
      {
        type: 'replace_senders',
        senders,
        removedSenderIds,
      },
    ],
  }
}

export function buildReplaceDeviceAppsMutation(
  deviceId: number,
  apps: SnapshotAppInfo[],
): ConfigMutationBatch {
  return {
    operations: [
      {
        type: 'replace_device_apps',
        deviceId,
        apps,
      },
    ],
  }
}

export function applyMutationBatchToConfigRoot(
  baseRoot: Record<string, unknown>,
  mutation: ConfigMutationBatch,
): RemoteConfigRoot {
  let nextRoot = normalizeConfigRoot(baseRoot)
  for (const operation of mutation.operations) {
    nextRoot = applyMutationOperation(nextRoot, operation)
  }
  return normalizeConfigRoot(nextRoot)
}

function applyMutationOperation(
  baseRoot: RemoteConfigRoot,
  operation: ConfigMutationOperation,
): RemoteConfigRoot {
  const nextRoot = cloneConfigRoot(baseRoot)
  if (operation.type === 'replace_senders') {
    nextRoot.senders = operation.senders
    const removedSenderIds = new Set((operation.removedSenderIds ?? []).map((value) => Math.trunc(value)))
    if (removedSenderIds.size > 0) {
      nextRoot.rules = nextRoot.rules.filter((rule) => !removedSenderIds.has(Math.trunc(rule.senderId)))
      nextRoot.notifyRoutes = nextRoot.notifyRoutes.filter((route) => !removedSenderIds.has(Math.trunc(route.senderId)))
      nextRoot.forwardFilters = nextRoot.forwardFilters.filter((rule) => !removedSenderIds.has(Math.trunc(rule.senderId)))
    }
    return nextRoot
  }

  if (operation.type === 'replace_device_apps') {
    if (!nextRoot.deviceAppInfos) {
      nextRoot.deviceAppInfos = {}
    }
    nextRoot.deviceAppInfos[String(operation.deviceId)] = operation.apps
    return nextRoot
  }

  const unreachable: never = operation
  throw new Error(`Unsupported config mutation operation: ${JSON.stringify(unreachable)}`)
}
