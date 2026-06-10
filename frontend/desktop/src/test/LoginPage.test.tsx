import type { DesktopAuthStart } from '../../../shared/contracts/console'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { DesktopI18nProvider } from '../i18n'
import { LoginPage } from '../pages/LoginPage'

const mockDesktop = vi.hoisted(() => ({
  bootstrap: {
    session: { authenticated: false },
    profiles: [
      {
        id: 'profile-a',
        name: 'Primary Backend',
        baseUrl: 'https://primary.example.com',
        allowSelfSigned: false,
        active: true
      },
      {
        id: 'profile-b',
        name: 'Staging Backend',
        baseUrl: 'https://staging.example.com',
        allowSelfSigned: true,
        active: false
      }
    ]
  },
  session: {
    authenticated: false,
    username: '',
    expiresAt: '',
    refreshExpiresAt: ''
  },
  activeProfile: {
    id: 'profile-a',
    name: 'Primary Backend',
    baseUrl: 'https://primary.example.com',
    allowSelfSigned: false,
    active: true
  },
  authBusy: false,
  error: '',
  lastProbe: null,
  pendingAuthStart: null as DesktopAuthStart | null,
  beginBrowserLogin: vi.fn().mockResolvedValue(undefined),
  retryPendingBrowserOpen: vi.fn().mockResolvedValue(undefined),
  completeBrowserLogin: vi.fn().mockResolvedValue(undefined),
  probeBackend: vi.fn().mockResolvedValue(undefined),
  saveProfile: vi.fn().mockResolvedValue(undefined),
  deleteProfile: vi.fn().mockResolvedValue(undefined),
  setActiveProfile: vi.fn().mockResolvedValue(undefined)
}))

vi.mock('../state/DesktopContext', () => ({
  useDesktop: () => mockDesktop
}))

describe('LoginPage', () => {
  it('lets users switch the active profile before browser login', async () => {
    render(
      <MemoryRouter>
        <DesktopI18nProvider>
          <LoginPage />
        </DesktopI18nProvider>
      </MemoryRouter>
    )

    fireEvent.click(screen.getByText('Make Active'))

    await waitFor(() => {
      expect(mockDesktop.setActiveProfile).toHaveBeenCalledWith('profile-b')
    })

    fireEvent.click(screen.getByText('Continue In Browser'))

    await waitFor(() => {
      expect(mockDesktop.beginBrowserLogin).toHaveBeenCalledWith('profile-a')
    })
  })

  it('allows manual completion once a callback URL is pasted', async () => {
    mockDesktop.pendingAuthStart = {
      authUrl: 'http://backend.test/start',
      callbackUrl: 'http://127.0.0.1:46025/callback',
      state: 'desktop-state'
    }

    render(
      <MemoryRouter>
        <DesktopI18nProvider>
          <LoginPage />
        </DesktopI18nProvider>
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText(/127\.0\.0\.1/), {
      target: {
        value: 'http://127.0.0.1:46025/callback?code=desktop-code'
      }
    })

    await waitFor(() => {
      expect(mockDesktop.completeBrowserLogin).toHaveBeenCalledWith('desktop-code', 'desktop-state')
    })

    mockDesktop.pendingAuthStart = null
  })

  it('auto-submits when a local callback URL is pasted', async () => {
    mockDesktop.pendingAuthStart = {
      authUrl: 'http://backend.test/start',
      callbackUrl: 'http://127.0.0.1:46025/callback',
      state: 'desktop-state-auto'
    }

    render(
      <MemoryRouter>
        <DesktopI18nProvider>
          <LoginPage />
        </DesktopI18nProvider>
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText(/127\.0\.0\.1/), {
      target: {
        value: 'http://127.0.0.1:46025/callback?code=auto-code&state=desktop-state-auto'
      }
    })

    await waitFor(() => {
      expect(mockDesktop.completeBrowserLogin).toHaveBeenCalledWith('auto-code', 'desktop-state-auto')
    })

    mockDesktop.pendingAuthStart = null
  })
})
