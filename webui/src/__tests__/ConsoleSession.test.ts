import { describe, expect, it } from 'vitest'
import {
  consoleSessionFromLogin,
  consoleSessionFromMe,
  desktopConsoleSessionFromState
} from '../../../shared/consoleSession'

describe('shared console session helpers', () => {
  it('normalizes web login and me responses', () => {
    expect(consoleSessionFromLogin({
      authenticated: true,
      username: 'relay',
      csrfToken: 'csrf',
      languageTag: 'en'
    })).toEqual({
      authenticated: true,
      username: 'relay',
      csrfToken: 'csrf',
      languageTag: 'en'
    })

    expect(consoleSessionFromMe({ authenticated: true })).toEqual({
      authenticated: false,
      username: '',
      csrfToken: '',
      languageTag: ''
    })
  })

  it('normalizes desktop session state into non-null display fields', () => {
    expect(desktopConsoleSessionFromState({
      authenticated: true,
      username: 'relay',
      expiresAt: null,
      refreshExpiresAt: '2026-05-25T00:00:00Z'
    })).toEqual({
      authenticated: true,
      username: 'relay',
      expiresAt: '',
      refreshExpiresAt: '2026-05-25T00:00:00Z'
    })

    expect(desktopConsoleSessionFromState(undefined)).toEqual({
      authenticated: false,
      username: '',
      expiresAt: '',
      refreshExpiresAt: ''
    })
  })
})
