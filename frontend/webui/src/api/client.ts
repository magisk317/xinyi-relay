import { createConsoleApiClient, type ConsoleApiRequestOptions } from '../../../shared/consoleApiClient'
import { ConfigConflictError } from '../configSnapshot'
import { translateStatic } from '../i18n'

let csrfToken = ''
const DEFAULT_TIMEOUT_MS = 8_000

export function setCsrfToken(nextToken: string): void {
  csrfToken = nextToken
}

function extractErrorMessage(text: string, status: number): string {
  const trimmed = text.trim()
  if (!trimmed) {
    return `${translateStatic('common.requestFailed')}${status}`
  }

  try {
    const parsed = JSON.parse(trimmed) as { error?: string }
    if (parsed.error?.trim()) {
      return parsed.error.trim()
    }
  } catch {
    // Fall back to raw text below when the payload is not JSON.
  }

  return trimmed
}

function normalizeRequestError(error: unknown): Error {
  if (error instanceof DOMException && error.name === 'AbortError') {
    return new Error('Connection timed out. Make sure the remote backend is still active.')
  }
  if (error instanceof TypeError) {
    return new Error('Unable to connect to the remote backend.')
  }
  return error instanceof Error ? error : new Error(translateStatic('common.requestFailed'))
}

async function request<T>(
  path: string,
  options: ConsoleApiRequestOptions = {}
): Promise<T> {
  const { body, conflictMessage, requiresCsrf = false, timeoutMs = DEFAULT_TIMEOUT_MS } = options
  const headers = new Headers()
  const method = (options.method ?? 'GET').toUpperCase()
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (requiresCsrf && method !== 'GET') {
    headers.set('X-CSRF-Token', csrfToken)
  }

  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const resp = await fetch(path, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal
    })

    const text = await resp.text()
    if (resp.status === 409 && conflictMessage) {
      const latest = JSON.parse(text) as import('../types').ConfigSnapshotState
      throw new ConfigConflictError(conflictMessage, latest)
    }
    if (!resp.ok) {
      throw new Error(extractErrorMessage(text, resp.status))
    }

    return (text.trim() ? JSON.parse(text) : undefined) as T
  } catch (error) {
    throw normalizeRequestError(error)
  } finally {
    window.clearTimeout(timeoutId)
  }
}

export const apiClient = createConsoleApiClient({ request })
