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
    await desktopApi.getConfigAuditLogs(30, 4)

    expect(invoke).toHaveBeenNthCalledWith(1, 'desktop_fetch_records', { limit: 20, deviceId: 5 })
    expect(invoke).toHaveBeenNthCalledWith(2, 'desktop_fetch_record', { recordId: 12 })
    expect(invoke).toHaveBeenNthCalledWith(3, 'desktop_fetch_config_audit_logs', { limit: 30, offset: 4 })
  })

  it('routes shared console mutation methods through Tauri commands', async () => {
    const { desktopApi } = await import('../api/desktopApi')
    invoke.mockResolvedValue({})

    await desktopApi.patchDevice(7, { displayName: 'Desk', enabled: false })
    await desktopApi.revokeDevice(7)
    await desktopApi.putConfigSnapshot(9, { senders: [] })

    expect(invoke).toHaveBeenNthCalledWith(1, 'desktop_patch_device', {
      deviceId: 7,
      payload: { displayName: 'Desk', enabled: false }
    })
    expect(invoke).toHaveBeenNthCalledWith(2, 'desktop_revoke_device', { deviceId: 7 })
    expect(invoke).toHaveBeenNthCalledWith(3, 'desktop_put_config_snapshot', {
      baseRevision: 9,
      snapshot: { senders: [] }
    })
  })
})
