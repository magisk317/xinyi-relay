import { describe, expect, it } from 'vitest'
import {
  buildSenderJsonFromFormState,
  parseSenderFormState,
  prettySenderJson
} from '../../../shared/senderDefaults'

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
