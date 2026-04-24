import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { vi } from 'vitest'
import { DesktopI18nProvider } from '../i18n'
import { SendersPage } from '../pages/SendersPage'

const { load, saveRoot } = vi.hoisted(() => ({
  load: vi.fn().mockResolvedValue(undefined),
  saveRoot: vi.fn().mockResolvedValue(undefined)
}))

vi.mock('../hooks/useDesktopRealtimeRefresh', () => ({
  useDesktopRealtimeRefresh: () => undefined
}))

vi.mock('../state/DesktopContext', () => ({
  useDesktop: () => ({
    bootstrap: { languageTag: 'en', session: { authenticated: true }, profiles: [], connection: { state: 'connected', message: '' } }
  })
}))

vi.mock('../hooks/useDesktopConfigSnapshotEditor', () => ({
  useDesktopConfigSnapshotEditor: () => ({
    config: { revision: 7 },
    root: {
      senders: [
        {
          id: 1,
          type: 4,
          name: 'Ops Robot',
          jsonSetting: '{"custom":"keep-me"}',
          status: 1,
          receiveCode: 1,
          receiveNonCode: 1,
          receiveAppNotify: 1,
          receiveCallNotify: 0
        }
      ],
      rules: [],
      notifyRoutes: [],
      forwardFilters: []
    },
    loading: false,
    saving: false,
    error: '',
    setError: vi.fn(),
    load,
    saveRoot
  })
}))

describe('SendersPage', () => {
  it('keeps custom sender JSON when changing the sender type', async () => {
    render(
      <DesktopI18nProvider>
        <SendersPage />
      </DesktopI18nProvider>
    )

    const card = screen.getByText('Ops Robot').closest('article')
    expect(card).not.toBeNull()

    const selectTrigger = within(card as HTMLElement).getByRole('button', { name: 'Sender type' })
    fireEvent.click(selectTrigger)
    fireEvent.click(screen.getByRole('button', { name: 'Telegram' }))

    await waitFor(() => {
      expect(saveRoot).toHaveBeenCalled()
    })

    const nextRoot = saveRoot.mock.calls[saveRoot.mock.calls.length - 1]?.[0]
    expect(nextRoot.senders[0].type).toBe(7)
    expect(nextRoot.senders[0].jsonSetting).toBe('{"custom":"keep-me"}')
  })

  it('persists active schedule changes through the sender page', async () => {
    render(
      <DesktopI18nProvider>
        <SendersPage />
      </DesktopI18nProvider>
    )

    const card = screen.getByText('Ops Robot').closest('article')
    expect(card).not.toBeNull()

    const enableCheckbox = within(card as HTMLElement).getByLabelText('SMS enabled')
    fireEvent.click(enableCheckbox)

    await waitFor(() => {
      expect(saveRoot).toHaveBeenCalled()
    })

    const nextRoot = saveRoot.mock.calls[saveRoot.mock.calls.length - 1]?.[0]
    expect(nextRoot.senders[0].activeSchedule.sms.enabled).toBe(true)
    expect(nextRoot.senders[0].activeSchedule.sms.ranges).toEqual([{ start: '09:00', end: '18:00' }])
  })
})
