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

  let resolved: NormalizedScheduledTask[]
  if (choice === 'keep-both') {
    resolved = [
      ...group.map((task) => ({
        ...task,
        syncId: deterministicConflictSyncId(conflictId, task.syncId, task.conflictSide),
        status: task.conflictPreviousStatus ?? 1,
        conflictId: null,
        conflictSide: '' as const,
        conflictPreviousStatus: null,
        syncDeleted: false,
      })),
      ...group.map(tombstoneConflictVersion),
    ]
  } else {
    const selected = group.find((task) => task.conflictSide === choice)
    if (!selected) throw new Error('This conflict choice has no retained task.')
    resolved = [
      {
        ...selected,
        status: selected.syncDeleted ? 0 : selected.conflictPreviousStatus ?? 1,
        conflictId: null,
        conflictSide: '',
        conflictPreviousStatus: null,
      },
      ...group.filter((task) => task.syncId !== selected.syncId).map(tombstoneConflictVersion),
    ]
  }

  return [...tasks.filter((task) => task.conflictId !== conflictId), ...resolved]
    .sort((left, right) => left.syncId.localeCompare(right.syncId))
}

function tombstoneConflictVersion(task: NormalizedScheduledTask): NormalizedScheduledTask {
  return {
    ...task,
    status: 0,
    conflictId: null,
    conflictSide: '',
    conflictPreviousStatus: null,
    syncDeleted: true,
  }
}

function deterministicConflictSyncId(conflictId: string, syncId: string, side: string): string {
  const source = `${conflictId}:${syncId}:${side}`
  const hashes = [2166136261, 2246822519, 3266489917, 668265263].map((seed) => {
    let hash = seed
    for (let index = 0; index < source.length; index += 1) {
      hash = Math.imul(hash ^ source.charCodeAt(index), 16777619) >>> 0
    }
    return hash.toString(16).padStart(8, '0')
  })
  const raw = hashes.join('').split('')
  raw[12] = '5'
  raw[16] = ['8', '9', 'a', 'b'][Number.parseInt(raw[16] ?? '0', 16) & 3] ?? '8'
  const hex = raw.join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`
}

export function isScheduledTaskHeartbeatEligible(lastSeenAt: string | null | undefined, now = Date.now()): boolean {
  if (!lastSeenAt) return false
  const timestamp = Date.parse(lastSeenAt)
  return Number.isFinite(timestamp) && now - timestamp <= 15 * 60 * 1000
}
