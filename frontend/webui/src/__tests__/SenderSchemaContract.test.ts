import { describe, expect, it } from 'vitest'
import {
  buildSenderDraftJson,
  buildSenderJsonFromFormState,
  getSenderFieldSchemas,
  getSenderSettingSchemaContracts,
  normalizeSnapshotSender,
  type SenderFieldKind,
  type SenderSettingContractFieldType
} from '../senderDefaults'

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

describe('shared sender schema contract', () => {
  it('keeps structured WebUI fields aligned with the Kotlin schema contract', () => {
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

  it('keeps hidden compatibility fields limited to non-required fields', () => {
    for (const contract of getSenderSettingSchemaContracts()) {
      const hiddenFields = HIDDEN_COMPAT_FIELDS[contract.senderType] ?? []
      for (const hiddenName of hiddenFields) {
        const contractField = contract.fields.find((field) => field.name === hiddenName)
        expect(contractField, `${contract.senderType}.${hiddenName}`).toBeTruthy()
        expect(contractField?.requiredForEnable).toBe(false)
      }
    }
  })

  it('derives WebUI defaults and select options from the Kotlin schema contract', () => {
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

  it('drops legacy Feishu App token auth fields when snapshot senders are normalized', () => {
    const editingJson = buildSenderJsonFromFormState(13, {
      appId: 'cli_a123',
      appSecret: 'app-secret',
      receiveId: 'receive-id',
      authType: 'token',
      botToken: 'bot-token'
    })
    expect(JSON.parse(editingJson)).toMatchObject({
      appId: 'cli_a123',
      appSecret: 'app-secret',
      receiveId: 'receive-id'
    })
    expect(editingJson).not.toContain('authType')
    expect(editingJson).not.toContain('botToken')

    const normalized = normalizeSnapshotSender({
      id: 1,
      type: 13,
      name: ' Feishu ',
      jsonSetting: editingJson,
      status: 1,
      receiveCode: 1,
      receiveNonCode: 1,
      receiveAppNotify: 1,
      receiveCallNotify: 0
    })
    expect(JSON.parse(normalized.jsonSetting)).toMatchObject({
      appId: 'cli_a123',
      appSecret: 'app-secret',
      receiveId: 'receive-id'
    })
    expect(normalized.jsonSetting).not.toContain('authType')
    expect(normalized.jsonSetting).not.toContain('botToken')
  })
})
