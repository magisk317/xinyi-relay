import { describe, expect, it } from 'vitest'
import openApi from '../../../shared/contracts/openapi.json'
import type {
  BindCodeResponse,
  ConfigAuditLogItem,
  ConfigAuditLogsResponse,
  ConfigSnapshotState,
  DeviceItem,
  DevicesResponse,
  ErrorResponse,
  LoginResponse,
  MeResponse,
  RealtimeEvent,
  RecordItem,
  RecordsResponse,
  SystemInfoState,
  DesktopAuthExchangeResponse
} from '../../../shared/contracts/console'

type OpenApiSchema = {
  properties?: Record<string, unknown>
}

type OpenApiDocument = {
  components: {
    schemas: Record<string, OpenApiSchema>
  }
}

type StringKeyOf<T> = Extract<keyof T, string>
type MissingKeys<T, Keys extends readonly string[]> = Exclude<StringKeyOf<T>, Keys[number]>
type ExtraKeys<T, Keys extends readonly string[]> = Exclude<Keys[number], StringKeyOf<T>>
type ExactKeys<T, Keys extends readonly string[]> =
  [MissingKeys<T, Keys>] extends [never]
    ? [ExtraKeys<T, Keys>] extends [never]
      ? unknown
      : ['extra keys', ExtraKeys<T, Keys>]
    : ['missing keys', MissingKeys<T, Keys>]

function fieldsFor<T>() {
  return <const Keys extends readonly string[]>(...keys: Keys & ExactKeys<T, Keys>) => keys
}

const schemaCases = [
  { schemaName: 'ErrorResponse', fields: fieldsFor<ErrorResponse>()('error') },
  {
    schemaName: 'SystemInfoResponse',
    fields: fieldsFor<SystemInfoState>()(
      'service',
      'appEnv',
      'localBaseUrl',
      'publicBaseUrl',
      'databaseReady',
      'userCount',
      'time'
    )
  },
  {
    schemaName: 'LoginResponse',
    fields: fieldsFor<LoginResponse>()('authenticated', 'username', 'csrfToken', 'languageTag')
  },
  {
    schemaName: 'MeResponse',
    fields: fieldsFor<MeResponse>()('authenticated', 'username', 'csrfToken', 'languageTag')
  },
  { schemaName: 'BindCodeResponse', fields: fieldsFor<BindCodeResponse>()('code', 'expiresAt') },
  {
    schemaName: 'DeviceItem',
    fields: fieldsFor<DeviceItem>()(
      'id',
      'userId',
      'deviceName',
      'deviceModel',
      'platform',
      'appVersion',
      'displayName',
      'enabled',
      'revokedAt',
      'lastSeenAt',
      'localAddresses',
      'capabilities',
      'createdAt',
      'updatedAt'
    )
  },
  { schemaName: 'DevicesResponse', fields: fieldsFor<DevicesResponse>()('devices') },
  {
    schemaName: 'ConfigSnapshotResponse',
    fields: fieldsFor<ConfigSnapshotState>()('revision', 'snapshot')
  },
  {
    schemaName: 'ConfigAuditLogItem',
    fields: fieldsFor<ConfigAuditLogItem>()('id', 'revision', 'actorType', 'actorId', 'summary', 'createdAt')
  },
  {
    schemaName: 'ConfigAuditLogsResponse',
    fields: fieldsFor<ConfigAuditLogsResponse>()('logs', 'limit', 'offset')
  },
  {
    schemaName: 'RelayRecord',
    fields: fieldsFor<RecordItem>()(
      'id',
      'deviceId',
      'eventId',
      'recordType',
      'sender',
      'body',
      'smsCode',
      'packageName',
      'metadata',
      'msgType',
      'callType',
      'occurredAt',
      'uploadedAt'
    )
  },
  { schemaName: 'RecordsResponse', fields: fieldsFor<RecordsResponse>()('records', 'limit', 'offset') },
  {
    schemaName: 'DesktopSessionResponse',
    fields: fieldsFor<DesktopAuthExchangeResponse>()(
      'authenticated',
      'username',
      'accessToken',
      'refreshToken',
      'expiresAt',
      'refreshExpiresAt'
    )
  },
  { schemaName: 'RealtimeEvent', fields: fieldsFor<RealtimeEvent>()('type', 'time', 'data') }
] as const

function openApiSchemaFields(schemaName: string): string[] {
  const doc = openApi as OpenApiDocument
  const schema = doc.components.schemas[schemaName]
  expect(schema, `schema ${schemaName} should exist`).toBeTruthy()
  expect(schema.properties, `schema ${schemaName} should declare properties`).toBeTruthy()
  return Object.keys(schema.properties ?? {}).sort()
}

describe('shared console OpenAPI contract', () => {
  it.each(schemaCases)('$schemaName fields match shared TypeScript contract', ({ schemaName, fields }) => {
    expect(openApiSchemaFields(schemaName)).toEqual([...fields].sort())
  })
})
