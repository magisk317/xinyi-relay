import { act, renderHook, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { useDesktopDeviceConfig } from '../hooks/useDesktopDeviceConfig'
import type { ConfigMutationBatch, DeviceConfigCommandState, DeviceConfigState } from '../../../shared/contracts/console'

const { getDevices, getDeviceConfig, queueDeviceConfigCommand } = vi.hoisted(() => ({
  getDevices: vi.fn(),
  getDeviceConfig: vi.fn(),
  queueDeviceConfigCommand: vi.fn()
}))

vi.mock('../api/desktopApi', () => ({
  desktopApi: {
    getDevices,
    getDeviceConfig,
    queueDeviceConfigCommand
  }
}))

vi.mock('../state/DesktopContext', () => ({
  useDesktop: () => ({
    runMode: 'local',
    lastRealtimeEvent: null
  })
}))

vi.mock('../hooks/useDesktopRealtimeRefresh', () => ({
  useDesktopRealtimeRefresh: () => undefined
}))

const mutation: ConfigMutationBatch = {
  operations: [{ type: 'replace_senders', senders: [] }]
}

describe('useDesktopDeviceConfig', () => {
  beforeEach(() => {
    window.localStorage?.clear()
    getDevices.mockReset()
    getDeviceConfig.mockReset()
    queueDeviceConfigCommand.mockReset()
    getDevices.mockResolvedValue(defaultDevicesResponse())
    getDeviceConfig.mockResolvedValue(deviceConfig())
    queueDeviceConfigCommand.mockResolvedValue(pendingCommand({ baseRevision: 2, targetRevision: 3 }))
  })

  it('loads device config through Tauri in local mode too', async () => {
    const { result } = renderHook(() => useDesktopDeviceConfig())

    await waitFor(() => {
      expect(getDeviceConfig).toHaveBeenCalledWith(1)
    })

    expect(result.current.config?.deviceId).toBe(1)
    expect(result.current.root?.senders).toEqual([])
  })

  it('queues mutations from the highest pending target revision even when pending commands are unsorted', async () => {
    getDeviceConfig.mockResolvedValue(
      deviceConfig({
        revision: 2,
        pendingCommands: [
          pendingCommand({ id: 11, baseRevision: 2, targetRevision: 5 }),
          pendingCommand({ id: 12, baseRevision: 5, targetRevision: 3 })
        ]
      })
    )
    queueDeviceConfigCommand.mockResolvedValue(pendingCommand({ id: 13, baseRevision: 5, targetRevision: 6 }))

    const { result } = renderHook(() => useDesktopDeviceConfig())

    await waitFor(() => {
      expect(result.current.config?.pendingCommands).toHaveLength(2)
    })

    await act(async () => {
      await result.current.queueMutation(mutation, 'senders:update')
    })

    expect(queueDeviceConfigCommand).toHaveBeenCalledWith(1, 5, mutation, 'senders:update')
  })

  it('previews pending commands in target revision order', async () => {
    getDeviceConfig.mockResolvedValue(
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

    const { result } = renderHook(() => useDesktopDeviceConfig())

    await waitFor(() => {
      expect(result.current.root?.senders.map((item) => item.id)).toEqual([5])
    })
  })

  it('skips unsupported pending mutation previews instead of throwing', async () => {
    getDeviceConfig.mockResolvedValue(
      deviceConfig({
        mirrorContent: {
          senders: [sender(1, 'current')],
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
            mutation: { operations: [{ type: 'unknown_operation' }] } as unknown as ConfigMutationBatch
          }),
          pendingCommand({
            id: 12,
            targetRevision: 4,
            mutation: { operations: [{ type: 'replace_senders', senders: [sender(2, 'next')] }] }
          })
        ]
      })
    )

    const { result } = renderHook(() => useDesktopDeviceConfig())

    await waitFor(() => {
      expect(result.current.error).toBe('')
      expect(result.current.root?.senders.map((item) => item.id)).toEqual([2])
    })
  })
})

function defaultDevicesResponse() {
  return {
    devices: [
      {
        id: 1,
        userId: 1,
        deviceName: 'Pixel',
        deviceModel: 'Pixel 9',
        platform: 'android',
        appVersion: '1.0.0',
        displayName: 'Primary Phone',
        enabled: true,
        revokedAt: null,
        lastSeenAt: null,
        localAddresses: [],
        capabilities: {},
        createdAt: '2026-04-09T09:00:00Z',
        updatedAt: '2026-04-09T09:58:00Z'
      }
    ]
  }
}

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
    mutation,
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
