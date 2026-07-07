import { describe, expect, it } from 'vitest'
import {
  applyMutationBatchToConfigRoot,
  buildReplaceDeviceAppsMutation,
  buildReplaceSendersMutation
} from '../../../shared/configMutations'

describe('shared config mutations', () => {
  it('replace_senders prunes sender-scoped relations', () => {
    const next = applyMutationBatchToConfigRoot(
      {
        senders: [
          { id: 1, type: 4, name: 'Old', jsonSetting: '{}', status: 1, receiveCode: 1, receiveNonCode: 1, receiveAppNotify: 1, receiveCallNotify: 0 },
          { id: 2, type: 4, name: 'Keep', jsonSetting: '{}', status: 1, receiveCode: 1, receiveNonCode: 1, receiveAppNotify: 1, receiveCallNotify: 0 }
        ],
        rules: [{ id: 1, type: 'sms', filed: 'f', check: 'is', value: 'v', senderId: 1, title: 'r1' }, { id: 2, type: 'sms', filed: 'f', check: 'is', value: 'v', senderId: 2, title: 'r2' }],
        notifyRoutes: [{ id: 1, scope: 1, packageName: 'a', senderId: 1, updateTime: 1 }, { id: 2, scope: 1, packageName: 'b', senderId: 2, updateTime: 1 }],
        forwardFilters: [
          { id: 1, msgType: 'sms', scopeType: 'package', scopeKey: 'a', senderId: 1, policy: 'allow', matchMode: 'contains', pattern: 'x', enabled: 1, updateTime: 1 },
          { id: 2, msgType: 'sms', scopeType: 'package', scopeKey: 'b', senderId: 2, policy: 'allow', matchMode: 'contains', pattern: 'y', enabled: 1, updateTime: 1 }
        ],
        smsCodeRules: [],
        deviceAppInfos: {}
      },
      buildReplaceSendersMutation([
        { id: 2, type: 4, name: 'Keep', jsonSetting: '{}', status: 1, receiveCode: 1, receiveNonCode: 1, receiveAppNotify: 1, receiveCallNotify: 0 }
      ], [1])
    )

    expect(next.senders).toHaveLength(1)
    expect(next.senders[0]?.id).toBe(2)
    expect(next.rules).toHaveLength(1)
    expect(next.rules[0]?.senderId).toBe(2)
    expect(next.notifyRoutes).toHaveLength(1)
    expect(next.notifyRoutes[0]?.senderId).toBe(2)
    expect(next.forwardFilters).toHaveLength(1)
    expect(next.forwardFilters[0]?.senderId).toBe(2)
  })

  it('replace_device_apps updates only the targeted device app catalog', () => {
    const next = applyMutationBatchToConfigRoot(
      {
        senders: [],
        rules: [],
        notifyRoutes: [],
        forwardFilters: [],
        smsCodeRules: [],
        deviceAppInfos: {
          '1': [{ packageName: 'com.keep', blocked: false, forwarding: false, forwardingConfigured: false, notifyTemplate: '' }],
          '2': [{ packageName: 'com.old', blocked: false, forwarding: false, forwardingConfigured: false, notifyTemplate: '' }]
        }
      },
      buildReplaceDeviceAppsMutation(2, [{ packageName: 'com.new', blocked: false, forwarding: false, forwardingConfigured: false, notifyTemplate: '' }])
    )

    const deviceAppInfos = next.deviceAppInfos ?? {}
    expect(deviceAppInfos['1']).toEqual([{ packageName: 'com.keep', blocked: false, forwarding: false, forwardingConfigured: false, notifyTemplate: '' }])
    expect(deviceAppInfos['2']).toEqual([{ packageName: 'com.new', blocked: false, forwarding: false, forwardingConfigured: false, notifyTemplate: '' }])
  })

  it('rejects legacy whole-root replacement in typed mutation previews', () => {
    expect(() =>
      applyMutationBatchToConfigRoot(
        {
          senders: [],
          rules: [],
          notifyRoutes: [],
          forwardFilters: [],
          smsCodeRules: [],
          deviceAppInfos: {}
        },
        {
          operations: [
            {
              type: 'replace_root',
              snapshot: { senders: [] }
            }
          ]
        } as never
      )
    ).toThrow(/Unsupported config mutation operation/)
  })
})
