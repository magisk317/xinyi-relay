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

  it('routes device config reads and command writes through device-scoped endpoints', async () => {
    const { client, requests } = createRecordingClient()

    await client.getDeviceConfig(9)
    await client.queueDeviceConfigCommand(9, 4, { operations: [{ type: 'replace_senders', senders: [] }] }, 'senders:update')

    expect(requests).toEqual([
      {
        path: '/api/v1/devices/9/config',
        options: undefined
      },
      {
        path: '/api/v1/devices/9/config/commands',
        options: {
          method: 'POST',
          body: {
            baseRevision: 4,
            mutation: {
              operations: [{ type: 'replace_senders', senders: [] }]
            },
            summary: 'senders:update'
          },
          requiresCsrf: true
        }
      }
    ])
  })

  it('routes device action writes through csrf-protected endpoints', async () => {
    const { client, requests } = createRecordingClient()

    await client.queueDeviceAction(7, 'SET_RINGER_MODE', { mode: 'silent' })

    expect(requests).toEqual([
      {
        path: '/api/v1/devices/7/actions',
        options: {
          method: 'POST',
          body: { action: 'SET_RINGER_MODE', params: { mode: 'silent' } },
          requiresCsrf: true
        }
      }
    ])
  })

  it('reads device action history through device-scoped endpoints', async () => {
    const { client, requests } = createRecordingClient()

    await client.listDeviceActions(7)
    await client.listDeviceActions(7, 20, 5)

    expect(requests.map((request) => request.path)).toEqual([
      '/api/v1/devices/7/actions?limit=50&offset=0',
      '/api/v1/devices/7/actions?limit=20&offset=5'
    ])
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

  it('normalizes a null record list to an empty array', async () => {
    const client = createConsoleApiClient({
      async request<T>(): Promise<T> {
        return { records: null, limit: 80, offset: 0 } as unknown as T
      }
    })

    await expect(client.getRecords()).resolves.toEqual({
      records: [],
      limit: 80,
      offset: 0
    })
  })
})
