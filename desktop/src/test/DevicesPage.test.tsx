import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { DesktopI18nProvider } from '../i18n'
import { DevicesPage } from '../pages/DevicesPage'

const { fetchDevices, patchDevice } = vi.hoisted(() => ({
  fetchDevices: vi.fn().mockResolvedValue({
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
        lastSeenAt: '2026-04-09T09:58:00Z',
        localAddresses: [],
        capabilities: {},
        createdAt: '2026-04-09T09:00:00Z',
        updatedAt: '2026-04-09T09:58:00Z'
      }
    ]
  }),
  patchDevice: vi.fn().mockResolvedValue({})
}))

vi.mock('../api/desktopApi', () => ({
  desktopApi: {
    fetchDevices,
    createBindCode: vi.fn().mockResolvedValue({ code: 'ABCDEF', expiresAt: '2026-04-09T11:00:00Z' }),
    patchDevice,
    revokeDevice: vi.fn().mockResolvedValue({})
  }
}))

vi.mock('../state/DesktopContext', () => ({
  useDesktop: () => ({
    bootstrap: { languageTag: 'en', session: { authenticated: true }, profiles: [], connection: { state: 'connected', message: '' } }
  })
}))

vi.mock('../hooks/useDesktopRealtimeRefresh', () => ({
  useDesktopRealtimeRefresh: () => undefined
}))

describe('DevicesPage', () => {
  it('renames a device inline without prompt-based editing', async () => {
    render(
      <DesktopI18nProvider>
        <DevicesPage />
      </DesktopI18nProvider>
    )

    const nameInput = await screen.findByDisplayValue('Primary Phone')
    fireEvent.change(nameInput, { target: { value: 'Desk Phone' } })
    fireEvent.click(screen.getByText('Save Name'))

    await waitFor(() => {
      expect(patchDevice).toHaveBeenCalledWith(1, { displayName: 'Desk Phone' })
    })
  })
})
