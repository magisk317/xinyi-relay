import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { DesktopI18nProvider } from '../i18n'
import { DevicesPage } from '../pages/DevicesPage'

const { getDevices, getLocalServerAddr, patchDevice } = vi.hoisted(() => ({
  getDevices: vi.fn().mockResolvedValue({
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
  getLocalServerAddr: vi.fn().mockResolvedValue('127.0.0.1:43123'),
  patchDevice: vi.fn().mockResolvedValue({})
}))

vi.mock('../api/desktopApi', () => ({
  desktopApi: {
    getDevices,
    createBindCode: vi.fn().mockResolvedValue({ code: 'ABCDEF', expiresAt: '2026-04-09T11:00:00Z' }),
    getLocalServerAddr,
    patchDevice,
    revokeDevice: vi.fn().mockResolvedValue({})
  }
}))

vi.mock('../state/DesktopContext', () => ({
  useDesktop: () => ({
    activeProfile: null,
    bootstrap: { languageTag: 'en', session: { authenticated: true }, profiles: [], connection: { state: 'connected', message: '' } },
    runMode: 'local'
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

  it('uses the actual loopback server address for local bind QR codes', async () => {
    const { container } = render(
      <DesktopI18nProvider>
        <DevicesPage />
      </DesktopI18nProvider>
    )

    fireEvent.click(await screen.findByText('Create Bind Code'))

    await waitFor(() => {
      expect(getLocalServerAddr).toHaveBeenCalled()
      expect(container.querySelector('svg')).not.toBeNull()
      expect(screen.getByText(/loopback-only/)).toBeTruthy()
    })
  })
})
