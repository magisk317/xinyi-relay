import { describe, expect, it } from 'vitest'
import { appendPendingCommand, deriveEffectiveConfigRoot, latestEffectiveRevision } from '../../../shared/deviceConfigCommands'
import type { DeviceConfigCommandState, DeviceConfigState } from '../../../shared/contracts/console'

describe('shared device config command helpers', () => {
  it('uses the highest pending target revision as the next command base', () => {
    expect(
      latestEffectiveRevision(
        deviceConfig({
          revision: 2,
          pendingCommands: [
            pendingCommand({ id: 11, baseRevision: 2, targetRevision: 5 }),
            pendingCommand({ id: 12, baseRevision: 5, targetRevision: 3 })
          ]
        })
      )
    ).toBe(5)
  })

  it('falls back to the mirrored revision when no commands are pending', () => {
    expect(latestEffectiveRevision(deviceConfig({ revision: 7, pendingCommands: [] }))).toBe(7)
  })

  it('normalizes appended pending commands by target revision for preview order', () => {
    expect(
      appendPendingCommand(
        [
          pendingCommand({ id: 11, targetRevision: 5 }),
          pendingCommand({ id: 12, targetRevision: 3 })
        ],
        pendingCommand({ id: 13, targetRevision: 4 }),
      ).map((command) => command.targetRevision)
    ).toEqual([3, 4, 5])
  })

  it('derives effective root by applying pending commands in target revision order', () => {
    const root = deriveEffectiveConfigRoot(
      deviceConfig({
        pendingCommands: [
          pendingCommand({
            id: 11,
            targetRevision: 5,
            mutation: { operations: [{ type: 'replace_senders', senders: [sender(5, 'newer')] }] }
          }),
          pendingCommand({
            id: 12,
            targetRevision: 3,
            mutation: { operations: [{ type: 'replace_senders', senders: [sender(3, 'older')] }] }
          })
        ]
      })
    )

    expect(root.senders.map((item) => item.id)).toEqual([5])
  })

  it('skips unsupported pending mutation previews instead of throwing', () => {
    const root = deriveEffectiveConfigRoot(
      deviceConfig({
        mirrorContent: {
          senders: [sender(1, 'base')],
          rules: [],
          notifyRoutes: [],
          forwardFilters: [],
          smsCodeRules: [],
          deviceAppInfos: {}
        },
        pendingCommands: [
          pendingCommand({
            id: 11,
            targetRevision: 3,
            mutation: { operations: [{ type: 'replace_root', snapshot: { senders: [] } }] } as never
          }),
          pendingCommand({
            id: 12,
            targetRevision: 4,
            mutation: { operations: [{ type: 'replace_senders', senders: [sender(4, 'valid')] }] }
          })
        ]
      })
    )

    expect(root.senders.map((item) => item.id)).toEqual([4])
  })
})

function deviceConfig(overrides: Partial<DeviceConfigState> = {}): DeviceConfigState {
  return {
    deviceId: 1,
    revision: 2,
    mirrorContent: {
      senders: [],
      rules: [],
      notifyRoutes: [],
      forwardFilters: [],
      smsCodeRules: [],
      deviceAppInfos: {}
    },
    pendingCommands: [],
    updatedAt: '2026-04-09T09:58:00Z',
    ...overrides
  }
}

function pendingCommand(overrides: Partial<DeviceConfigCommandState> = {}): DeviceConfigCommandState {
  return {
    id: 1,
    baseRevision: 2,
    targetRevision: 3,
    mutation: { operations: [{ type: 'replace_senders', senders: [] }] },
    summary: 'senders:update',
    actorType: 'user',
    actorId: 1,
    status: 'pending',
    failureReason: null,
    createdAt: '2026-04-09T09:58:00Z',
    updatedAt: '2026-04-09T09:58:00Z',
    appliedAt: null,
    ...overrides
  }
}

function sender(id: number, name: string) {
  return {
    id,
    type: 4,
    name,
    jsonSetting: '{}',
    status: 1,
    receiveCode: 1,
    receiveNonCode: 1,
    receiveAppNotify: 1,
    receiveCallNotify: 0
  }
}
