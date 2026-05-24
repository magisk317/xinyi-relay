import { describe, expect, it } from 'vitest'
import {
  buildSenderDraftJson,
  buildSenderJsonFromFormState,
  getSenderFieldSchemas,
  getSenderSettingSchemaContracts,
  parseSenderFormState,
  prettySenderJson,
  type SenderFieldKind,
  type SenderSettingContractFieldType
} from '../../../shared/senderDefaults'

const HIDDEN_COMPAT_FIELDS: Record<number, string[]> = {
  1: ['nickname']
}

const EXPECTED_UI_KINDS: Record<SenderSettingContractFieldType, SenderFieldKind[]> = {
  TEXT: ['text', 'textarea', 'select'],
  SECRET: ['text'],
  BOOLEAN: ['boolean'],
  INTEGER: ['number'],
  STRING_MAP: ['json'],
  EMAIL_RECIPIENTS: ['json'],
  PROXY_TYPE: ['select']
}

describe('email sender defaults', () => {
  it('falls back to visible sender fields for legacy email configs', () => {
    const rawJson = JSON.stringify({
      mailType: '@qq.com',
      fromEmail: 'relay@example.com',
      authEmail: '',
      nickname: 'Android relay',
      fromEmailAlias: '',
      toEmail: 'user@example.com'
    })

    const formState = parseSenderFormState(1, rawJson)

    expect(formState.authEmail).toBe('relay@example.com')
    expect(formState.fromEmailAlias).toBe('Android relay')

    const nextJson = buildSenderJsonFromFormState(1, formState)
    const parsed = JSON.parse(prettySenderJson(1, nextJson))

    expect(parsed.authEmail).toBe('relay@example.com')
    expect(parsed.fromEmailAlias).toBe('Android relay')
    expect(parsed.nickname).toBe('Android relay')
  })
})

describe('shared sender schema contract', () => {
  it('keeps structured Desktop fields aligned with the Kotlin schema contract', () => {
    for (const contract of getSenderSettingSchemaContracts()) {
      const fields = getSenderFieldSchemas(contract.senderType)
      const hiddenFields = HIDDEN_COMPAT_FIELDS[contract.senderType] ?? []

      for (const contractField of contract.fields) {
        if (hiddenFields.includes(contractField.name)) continue
        const uiField = fields.find((field) => field.key === contractField.name)
        expect(uiField, `${contract.senderType}.${contractField.name}`).toBeTruthy()
        expect(EXPECTED_UI_KINDS[contractField.type]).toContain(uiField?.kind)
      }
    }
  })

  it('derives Desktop defaults and select options from the Kotlin schema contract', () => {
    expect(JSON.parse(buildSenderDraftJson(1))).toMatchObject({
      port: '465',
      ssl: true
    })
    expect(JSON.parse(buildSenderDraftJson(15))).toMatchObject({
      method: 'MQTT',
      msgTemplate: '{"msg":"[msg]"}',
      outMessageTopic: 'relay/default'
    })

    const socketMethod = getSenderFieldSchemas(15).find((field) => field.key === 'method')
    expect(socketMethod?.kind).toBe('select')
    expect(socketMethod?.options?.map((option) => option.value)).toEqual(['TCP', 'UDP', 'MQTT'])

    const sanitized = JSON.parse(buildSenderJsonFromFormState(15, {
      method: 'INVALID',
      port: '1883'
    }))
    expect(sanitized.method).toBe('MQTT')
    expect(sanitized.port).toBe(1883)
  })
})
