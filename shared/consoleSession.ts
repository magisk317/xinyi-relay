import type { LoginResponse, MeResponse } from './contracts/console'

export type ConsoleSessionState = {
  authenticated: boolean
  username: string
  csrfToken: string
  languageTag: string
}

export function consoleSessionFromLogin(response: LoginResponse): ConsoleSessionState {
  return {
    authenticated: response.authenticated,
    username: response.authenticated ? response.username : '',
    csrfToken: response.authenticated ? response.csrfToken : '',
    languageTag: response.languageTag ?? ''
  }
}

export function consoleSessionFromMe(response: MeResponse): ConsoleSessionState {
  const authenticated = response.authenticated && Boolean(response.username) && Boolean(response.csrfToken)
  return {
    authenticated,
    username: authenticated ? response.username ?? '' : '',
    csrfToken: authenticated ? response.csrfToken ?? '' : '',
    languageTag: response.languageTag ?? ''
  }
}
