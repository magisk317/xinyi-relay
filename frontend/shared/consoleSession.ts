import type { DesktopSessionState, LoginResponse, MeResponse } from './contracts/console'

export type ConsoleSessionState = {
  authenticated: boolean
  username: string
  csrfToken: string
  languageTag: string
}

export type DesktopConsoleSessionState = {
  authenticated: boolean
  username: string
  expiresAt: string
  refreshExpiresAt: string
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

export function desktopConsoleSessionFromState(
  session: DesktopSessionState | null | undefined
): DesktopConsoleSessionState {
  return {
    authenticated: session?.authenticated === true,
    username: session?.username ?? '',
    expiresAt: session?.expiresAt ?? '',
    refreshExpiresAt: session?.refreshExpiresAt ?? ''
  }
}
