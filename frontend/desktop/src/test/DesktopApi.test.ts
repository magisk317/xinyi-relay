import { describe, expect, it, vi } from 'vitest'

const invoke = vi.hoisted(() => vi.fn())

vi.mock('@tauri-apps/api/core', () => ({
  invoke
}))

describe('desktop API console transport', () => {
  it('routes shared console read methods through Tauri commands', async () => {
    const { desktopApi } = await import('../api/desktopApi')
    invoke.mockResolvedValue({})

    await desktopApi.getRecords(20, 5)
    await desktopApi.getRecord(12)

    expect(invoke).toHaveBeenNthCalledWith(1, 'desktop_fetch_records', { limit: 20, deviceId: 5 })
    expect(invoke).toHaveBeenNthCalledWith(2, 'desktop_fetch_record', { recordId: 12 })
  })

  it('routes shared console mutation methods through Tauri commands', async () => {
    const { desktopApi } = await import('../api/desktopApi')
    invoke.mockResolvedValue({})

    await desktopApi.patchDevice(7, { displayName: 'Desk', enabled: false })
    await desktopApi.revokeDevice(7)

    expect(invoke).toHaveBeenNthCalledWith(1, 'desktop_patch_device', {
      deviceId: 7,
      payload: { displayName: 'Desk', enabled: false }
    })
    expect(invoke).toHaveBeenNthCalledWith(2, 'desktop_revoke_device', { deviceId: 7 })
  })

  it('routes device-centered config methods through Tauri commands', async () => {
    const { desktopApi } = await import('../api/desktopApi')
    invoke.mockResolvedValue({})

    await desktopApi.getDeviceConfig(8)
    await desktopApi.queueDeviceConfigCommand(8, 3, {
      operations: [{ type: 'replace_senders', senders: [] }]
    }, 'senders:update')
    await desktopApi.getDeviceConfigAuditLogs(8, 20, 2)

    expect(invoke).toHaveBeenNthCalledWith(1, 'desktop_fetch_device_config', { deviceId: 8 })
    expect(invoke).toHaveBeenNthCalledWith(2, 'desktop_queue_device_config_command', {
      deviceId: 8,
      baseRevision: 3,
      summary: 'senders:update',
      mutation: {
        operations: [{ type: 'replace_senders', senders: [] }]
      }
    })
    expect(invoke).toHaveBeenNthCalledWith(3, 'desktop_fetch_device_config_audit_logs', {
      deviceId: 8,
      limit: 20,
      offset: 2
    })
  })
})
