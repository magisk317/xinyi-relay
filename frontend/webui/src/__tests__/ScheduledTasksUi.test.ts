import { describe, expect, it } from 'vitest'
import {
  isScheduledTaskHeartbeatEligible,
  normalizeScheduledTask,
  resolveScheduledTaskConflict,
  type NormalizedScheduledTask
} from '../scheduledTasksUi'

function task(syncId: string, conflictSide: 'local' | 'remote', previousStatus: number, syncDeleted = false): NormalizedScheduledTask {
  return {
    syncId,
    name: syncId,
    taskType: 'sms',
    cronExpression: '0 9 * * *',
    simSlot: 1,
    mobiles: '10086',
    content: 'verification code',
    status: 0,
    createdAt: 7,
    conflictId: 'conflict-1',
    conflictSide,
    conflictPreviousStatus: previousStatus,
    syncDeleted
  }
}

describe('scheduled task UI logic', () => {
  it('normalizes snapshot fields without dropping conflict metadata', () => {
    expect(normalizeScheduledTask({
      syncId: 'task-local',
      conflictId: 'conflict-1',
      conflictSide: 'local',
      conflictPreviousStatus: 1,
      syncDeleted: false
    })).toEqual({
      syncId: 'task-local',
      name: '',
      taskType: 'sms',
      cronExpression: '',
      simSlot: 0,
      mobiles: '',
      content: '',
      status: 1,
      createdAt: 0,
      conflictId: 'conflict-1',
      conflictSide: 'local',
      conflictPreviousStatus: 1,
      syncDeleted: false
    })
  })

  it('resolves local and remote conflict choices while preserving unrelated tasks', () => {
    const unrelated = { ...task('other', 'remote', 1), conflictId: null, conflictSide: '' as const }
    const tasks = [task('local', 'local', 1), task('remote', 'remote', 0), unrelated]
    const local = resolveScheduledTaskConflict(tasks, 'conflict-1', 'local')
    const remote = resolveScheduledTaskConflict(tasks, 'conflict-1', 'remote')

    expect(local.map((item) => item.syncId)).toEqual(['local', 'other', 'remote'])
    expect(local[0]?.status).toBe(1)
    expect(local[0]?.conflictId).toBeNull()
    expect(local[2]?.syncDeleted).toBe(true)
    expect(remote.map((item) => item.syncId)).toEqual(['local', 'other', 'remote'])
    expect(remote[0]?.syncDeleted).toBe(true)
    expect(remote[2]?.status).toBe(0)
  })

  it('keeps both definitions under deterministic new ids and retains old-id tombstones', () => {
    const tasks = [task('local', 'local', 1), task('remote', 'remote', 0)]
    const resolved = resolveScheduledTaskConflict(
      tasks,
      'conflict-1',
      'keep-both'
    )
    const retried = resolveScheduledTaskConflict(tasks, 'conflict-1', 'keep-both')
    const live = resolved.filter((item) => !item.syncDeleted)
    const tombstones = resolved.filter((item) => item.syncDeleted)

    expect(resolved).toEqual(retried)
    expect(live).toHaveLength(2)
    expect(new Set(live.map((item) => item.syncId)).size).toBe(2)
    expect(live.map((item) => item.status).sort()).toEqual([0, 1])
    expect(live.map((item) => item.syncId)).not.toContain('local')
    expect(live.map((item) => item.syncId)).not.toContain('remote')
    expect(tombstones.map((item) => item.syncId).sort()).toEqual(['local', 'remote'])
    expect(resolved.every((item) => item.conflictId === null)).toBe(true)
  })

  it('preserves deleted-side base content for keep-both in delete-versus-edit conflicts', () => {
    const local = { ...task('local-tombstone', 'local', 1, true), content: 'base definition' }
    const remote = { ...task('remote-edit', 'remote', 1), content: 'remote edit' }
    const resolved = resolveScheduledTaskConflict([local, remote], 'conflict-1', 'keep-both')
    const live = resolved.filter((item) => !item.syncDeleted)

    expect(live.map((item) => item.content).sort()).toEqual(['base definition', 'remote edit'])
    expect(live.map((item) => item.syncId)).not.toContain('local-tombstone')
    expect(live.map((item) => item.syncId)).not.toContain('remote-edit')
    expect(resolved.filter((item) => item.syncDeleted).map((item) => item.syncId).sort())
      .toEqual(['local-tombstone', 'remote-edit'])
  })

  it('preserves deleted remote base content for keep-both in remote-delete conflicts', () => {
    const local = { ...task('local-edit', 'local', 1), content: 'local edit' }
    const remote = { ...task('remote-tombstone', 'remote', 0, true), content: 'base definition' }
    const resolved = resolveScheduledTaskConflict([local, remote], 'conflict-1', 'keep-both')
    const live = resolved.filter((item) => !item.syncDeleted)

    expect(live.map((item) => item.content).sort()).toEqual(['base definition', 'local edit'])
    expect(live.map((item) => item.syncId)).not.toContain('local-edit')
    expect(live.map((item) => item.syncId)).not.toContain('remote-tombstone')
    expect(resolved.filter((item) => item.syncDeleted).map((item) => item.syncId).sort())
      .toEqual(['local-edit', 'remote-tombstone'])
  })

  it('allows writes at fifteen minutes and rejects an older or missing heartbeat', () => {
    const now = Date.parse('2026-11-20T12:00:00Z')
    expect(isScheduledTaskHeartbeatEligible('2026-11-20T11:45:00Z', now)).toBe(true)
    expect(isScheduledTaskHeartbeatEligible('2026-11-20T11:44:59Z', now)).toBe(false)
    expect(isScheduledTaskHeartbeatEligible(null, now)).toBe(false)
  })
})
