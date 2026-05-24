import { describe, expect, it } from 'vitest'
import {
  ConfigConflictError,
  latestConfigSnapshotFromError,
  loadNormalizedConfigSnapshot,
  normalizeConfigSnapshotError,
  saveNormalizedConfigSnapshot
} from '../../../shared/configSnapshot'
import type { ConfigSnapshotState, RemoteConfigRoot } from '../../../shared/contracts/console'

function snapshotState(revision: number, snapshot: Record<string, unknown>): ConfigSnapshotState {
  return { revision, snapshot }
}

describe('shared config snapshot helpers', () => {
  it('loads and normalizes optional snapshot arrays', async () => {
    const next = await loadNormalizedConfigSnapshot(async () =>
      snapshotState(3, {
        senders: [{ id: 1 }],
        appInfos: 'legacy-bad-value'
      })
    )

    expect(next.config.revision).toBe(3)
    expect(next.root.senders).toEqual([{ id: 1 }])
    expect(next.root.appInfos).toEqual([])
    expect(next.root.rules).toEqual([])
  })

  it('saves against the current revision and returns a normalized root', async () => {
    const saved = await saveNormalizedConfigSnapshot(
      snapshotState(8, {}),
      { senders: [{ id: 2 }] } as RemoteConfigRoot,
      async (baseRevision, snapshot) => {
        expect(baseRevision).toBe(8)
        expect(snapshot.senders).toEqual([{ id: 2 }])
        return snapshotState(9, {
          senders: snapshot.senders,
          notifyRoutes: undefined
        })
      }
    )

    expect(saved.config.revision).toBe(9)
    expect(saved.root.senders).toEqual([{ id: 2 }])
    expect(saved.root.notifyRoutes).toEqual([])
  })

  it('requires a loaded snapshot before saving', async () => {
    await expect(
      saveNormalizedConfigSnapshot(null, {}, async () => snapshotState(1, {}))
    ).rejects.toThrow('Cloud snapshot is not loaded yet.')
  })

  it('extracts normalized latest state from conflict errors', () => {
    const latest = snapshotState(11, { smsCodeRules: [{ id: 7 }] })
    const error = new ConfigConflictError('changed', latest)

    expect(latestConfigSnapshotFromError(error)).toEqual({
      config: latest,
      root: {
        smsCodeRules: [{ id: 7 }],
        senders: [],
        rules: [],
        appInfos: [],
        notifyRoutes: [],
        forwardFilters: []
      }
    })
    expect(latestConfigSnapshotFromError(new Error('other'))).toBeNull()
  })

  it('normalizes unknown errors with a fallback message', () => {
    expect(normalizeConfigSnapshotError(new Error('backend failed'), 'fallback')).toBe('backend failed')
    expect(normalizeConfigSnapshotError('bad', 'fallback')).toBe('fallback')
  })
})
