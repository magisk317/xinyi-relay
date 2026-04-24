import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { DesktopI18nProvider } from '../i18n'
import { RecordsPage } from '../pages/RecordsPage'

const { fetchRecords, fetchDevices } = vi.hoisted(() => ({
  fetchRecords: vi.fn().mockResolvedValue({
    records: [
      {
        id: 10,
        deviceId: 1,
        recordType: 'sms_code',
        sender: 'Bank',
        body: 'Your code is 246810',
        smsCode: '246810',
        packageName: 'com.example.bank',
        metadata: null,
        msgType: 1,
        callType: 0,
        occurredAt: '2026-04-09T10:00:00Z',
        uploadedAt: '2026-04-09T10:00:01Z'
      }
    ],
    limit: 80,
    offset: 0
  }),
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
  })
}))

vi.mock('../api/desktopApi', () => ({
  desktopApi: {
    fetchRecords,
    fetchDevices
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

describe('RecordsPage', () => {
  it('copies extracted codes and gives inline feedback', async () => {
    render(
      <DesktopI18nProvider>
        <RecordsPage />
      </DesktopI18nProvider>
    )

    const copyButton = await screen.findByText('Copy Code')
    fireEvent.click(copyButton)

    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith('246810')
      expect(screen.getByText('Copied')).toBeTruthy()
    })
  })
})
