import type { SnapshotScheduledTask } from '../../shared/contracts/console'

export type ScheduledTaskConflictChoice = 'local' | 'remote' | 'keep-both'

export type NormalizedScheduledTask = {
  syncId: string
  name: string
  taskType: string
  cronExpression: string
  simSlot: number
  mobiles: string
  content: string
  status: number
  createdAt: number
  conflictId: string | null
  conflictSide: 'local' | 'remote' | ''
  conflictPreviousStatus: number | null
  syncDeleted: boolean
}

export function normalizeScheduledTask(task: SnapshotScheduledTask): NormalizedScheduledTask {
  return {
    syncId: task.syncId ?? '',
    name: task.name ?? '',
    taskType: task.taskType ?? 'sms',
    cronExpression: task.cronExpression ?? '',
    simSlot: task.simSlot ?? 0,
    mobiles: task.mobiles ?? '',
    content: task.content ?? '',
    status: task.status ?? 1,
    createdAt: task.createdAt ?? 0,
    conflictId: task.conflictId ?? null,
    conflictSide: task.conflictSide === 'local' || task.conflictSide === 'remote' ? task.conflictSide : '',
    conflictPreviousStatus: task.conflictPreviousStatus ?? null,
    syncDeleted: task.syncDeleted ?? false
  }
}

export function resolveScheduledTaskConflict(
  tasks: NormalizedScheduledTask[],
  conflictId: string,
  choice: ScheduledTaskConflictChoice
): NormalizedScheduledTask[] {
  const group = tasks.filter((task) => task.conflictId === conflictId)
  if (!group.length) throw new Error('Scheduled task conflict not found.')

  const retained = choice === 'keep-both'
    ? group.filter((task) => !task.syncDeleted)
    : group.filter((task) => task.conflictSide === choice).slice(0, 1)
  if (!retained.length) throw new Error('This conflict choice has no retained task.')

  const resolved: NormalizedScheduledTask[] = retained.map((task) => ({
    ...task,
    status: task.conflictPreviousStatus ?? 1,
    conflictId: null,
    conflictSide: '' as const,
    conflictPreviousStatus: null,
  }))
  return [...tasks.filter((task) => task.conflictId !== conflictId), ...resolved]
    .sort((left, right) => left.syncId.localeCompare(right.syncId))
}

export function isScheduledTaskHeartbeatEligible(lastSeenAt: string | null | undefined, now = Date.now()): boolean {
  if (!lastSeenAt) return false
  const timestamp = Date.parse(lastSeenAt)
  return Number.isFinite(timestamp) && now - timestamp <= 15 * 60 * 1000
}
