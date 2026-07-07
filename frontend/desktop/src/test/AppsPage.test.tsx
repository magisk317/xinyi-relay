import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import { DesktopI18nProvider } from '../i18n'
import { AppsPage } from '../pages/AppsPage'

const mockUseDesktopDeviceConfig = vi.hoisted(() => ({
  config: { revision: 3 },
  devices: [{ id: 1, deviceName: 'Test Device', deviceModel: 'Model' }],
  selectedDeviceId: 1,
  setSelectedDeviceId: vi.fn(),
  root: {
    deviceAppInfos: {
      '1': [
        {
          packageName: 'com.example.bank',
          label: 'Bank',
          blocked: false,
          forwarding: true,
          forwardingConfigured: true,
          notifyTemplate: 'template-body'
        }
      ]
    },
    notifyRoutes: [{ id: 1, scope: 1, packageName: 'com.example.bank', senderId: 1, updateTime: 1 }],
    smsCodeRules: [{ id: 1, company: 'Bank', codeKeyword: 'code', codeRegex: '\\d+' }],
    forwardFilters: [{ id: 1, msgType: 'sms', scopeType: 'package', scopeKey: 'com.example.bank', senderId: 1, policy: 'allow', matchMode: 'contains', pattern: 'code', enabled: 1, updateTime: 1 }]
  },
  loading: false,
  saving: false,
  error: '',
  setError: vi.fn(),
  refresh: vi.fn().mockResolvedValue(undefined),
  queueMutation: vi.fn().mockResolvedValue(undefined)
}))

vi.mock('../state/DesktopContext', () => ({
  useDesktop: () => ({
    bootstrap: { languageTag: 'en', session: { authenticated: true }, profiles: [], connection: { state: 'connected', message: '' } }
  })
}))

vi.mock('../hooks/useDesktopDeviceConfig', () => ({
  useDesktopDeviceConfig: () => mockUseDesktopDeviceConfig
}))

describe('AppsPage', () => {
  it('shows template status instead of per-app SMS rule counts', async () => {
    render(
      <DesktopI18nProvider>
        <AppsPage />
      </DesktopI18nProvider>
    )

    expect(await screen.findByText('Template')).toBeTruthy()
    expect(screen.queryByText('SMS code rules')).toBeNull()
    expect(screen.getByText('Routing assets')).toBeTruthy()
  })
})
