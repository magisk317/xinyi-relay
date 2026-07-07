import type {
  DeviceConfigCommandState,
  DeviceConfigState,
  RemoteConfigRoot,
} from './contracts/console'
import { applyMutationBatchToConfigRoot } from './configMutations'
import { normalizeConfigRoot } from './configRoot'

export function latestEffectiveRevision(
  config: Pick<DeviceConfigState, 'revision' | 'pendingCommands'>,
): number {
  return config.pendingCommands.reduce(
    (revision, command) => Math.max(revision, command.targetRevision),
    config.revision,
  )
}

export function appendPendingCommand<
  Command extends Pick<DeviceConfigCommandState, 'targetRevision'>,
>(pendingCommands: readonly Command[], command: Command): Command[] {
  return [...pendingCommands, command].sort((left, right) => left.targetRevision - right.targetRevision)
}

export function deriveEffectiveConfigRoot(
  config: Pick<DeviceConfigState, 'mirrorContent' | 'pendingCommands'>,
): RemoteConfigRoot {
  return sortedPendingCommands(config.pendingCommands).reduce<RemoteConfigRoot>(
    (current, command) => {
      try {
        return applyMutationBatchToConfigRoot(current, command.mutation)
      } catch {
        return current
      }
    },
    normalizeConfigRoot(config.mirrorContent),
  )
}

function sortedPendingCommands<
  Command extends Pick<DeviceConfigCommandState, 'targetRevision'>,
>(pendingCommands: readonly Command[]): Command[] {
  return [...pendingCommands].sort((left, right) => left.targetRevision - right.targetRevision)
}
