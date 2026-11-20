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

    expect(local.map((item) => item.syncId)).toEqual(['local', 'other'])
    expect(local[0]?.status).toBe(1)
    expect(local[0]?.conflictId).toBeNull()
    expect(remote.map((item) => item.syncId)).toEqual(['other', 'remote'])
    expect(remote[1]?.status).toBe(0)
  })

  it('keeps non-deleted variants and restores their prior statuses for keep-both', () => {
    const resolved = resolveScheduledTaskConflict(
      [task('deleted-local', 'local', 1, true), task('remote', 'remote', 0)],
      'conflict-1',
      'keep-both'
    )
    expect(resolved.map((item) => item.syncId)).toEqual(['remote'])
    expect(resolved[0]?.status).toBe(0)
    expect(resolved[0]?.conflictId).toBeNull()
  })

  it('allows writes at fifteen minutes and rejects an older or missing heartbeat', () => {
    const now = Date.parse('2026-11-20T12:00:00Z')
    expect(isScheduledTaskHeartbeatEligible('2026-11-20T11:45:00Z', now)).toBe(true)
    expect(isScheduledTaskHeartbeatEligible('2026-11-20T11:44:59Z', now)).toBe(false)
    expect(isScheduledTaskHeartbeatEligible(null, now)).toBe(false)
  })
})
