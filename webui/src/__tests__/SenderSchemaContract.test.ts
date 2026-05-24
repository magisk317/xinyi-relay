import { describe, expect, it } from 'vitest'
import {
  getSenderFieldSchemas,
  getSenderSettingSchemaContracts,
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
})
