import { describe, expect, it } from 'vitest'
import { createConsoleApiClient, type ConsoleApiRequestOptions } from '../../../shared/consoleApiClient'

type RecordedRequest = {
  path: string
  options: ConsoleApiRequestOptions | undefined
}

function createRecordingClient() {
  const requests: RecordedRequest[] = []
  const client = createConsoleApiClient({
    async request<T>(path: string, options?: ConsoleApiRequestOptions): Promise<T> {
      requests.push({ path, options })
      return {} as T
    }
  })
  return { client, requests }
}

describe('shared console API client', () => {
  it('routes mutating device calls through csrf-protected endpoints', async () => {
    const { client, requests } = createRecordingClient()

    await client.patchDevice(42, { displayName: 'Kitchen', enabled: false })
    await client.revokeDevice(42)

    expect(requests).toEqual([
      {
        path: '/api/v1/devices/42',
        options: {
          method: 'PATCH',
          body: { displayName: 'Kitchen', enabled: false },
          requiresCsrf: true
        }
      },
      {
        path: '/api/v1/devices/42/revoke',
        options: {
          method: 'POST',
          requiresCsrf: true
        }
      }
    ])
  })

  it('uses the shared conflict contract for config snapshot writes', async () => {
    const { client, requests } = createRecordingClient()

    await client.putConfigSnapshot(7, { senders: [] })

    expect(requests[0]).toEqual({
      path: '/api/v1/config/snapshot',
      options: {
        method: 'PUT',
        body: {
          base_revision: 7,
          snapshot: { senders: [] }
        },
        requiresCsrf: true,
        conflictMessage: 'Cloud config changed on another client. Reloaded the latest revision.'
      }
    })
  })

  it('keeps optional query parameters out of record list requests', async () => {
    const { client, requests } = createRecordingClient()

    await client.getRecords(80)
    await client.getRecords(20, 5)

    expect(requests.map((request) => request.path)).toEqual([
      '/api/v1/records?limit=80',
      '/api/v1/records?limit=20&device_id=5'
    ])
  })
})
