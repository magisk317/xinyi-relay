import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { buildReplaceScheduledTasksMutation } from '../../../shared/configMutations'
import { useDeviceConfig } from '../deviceConfig'
import { useI18n } from '../i18n'
import { useRealtimeFeed } from '../realtime'
import {
  isScheduledTaskHeartbeatEligible,
  normalizeScheduledTask,
  resolveScheduledTaskConflict,
  type NormalizedScheduledTask
} from '../scheduledTasksUi'
import {
  ActionButton,
  EmptyCard,
  ErrorBanner,
  LoadingCard,
  PageShell,
  RelayBadge,
  RelaySelect,
  RelaySwitch,
  SurfaceCard
} from '../template'

type TaskDraft = {
  name: string
  cronExpression: string
  simSlot: number
  mobiles: string
  content: string
  enabled: boolean
}

const emptyDraft: TaskDraft = {
  name: '',
  cronExpression: '0 9 * * *',
  simSlot: 0,
  mobiles: '',
  content: '',
  enabled: true
}

export function ScheduledTasksPage() {
  const { t } = useI18n()
  const {
    config,
    devices,
    selectedDeviceId,
    setSelectedDeviceId,
    root,
    loading,
    saving,
    error,
    setError,
    refreshConfig,
    queueMutation
  } = useDeviceConfig()
  const { connected } = useRealtimeFeed()
  const [now, setNow] = useState(() => Date.now())
  const [editingSyncId, setEditingSyncId] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [draft, setDraft] = useState<TaskDraft>(emptyDraft)

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  const selectedDevice = devices.find((device) => device.id === selectedDeviceId)
  const tasks = useMemo(() => (root?.scheduledTasks ?? []).map(normalizeScheduledTask), [root?.scheduledTasks])
  const conflicts = useMemo(() => {
    const groups = new Map<string, NormalizedScheduledTask[]>()
    tasks.forEach((task) => {
      if (!task.conflictId) return
      groups.set(task.conflictId, [...(groups.get(task.conflictId) ?? []), task])
    })
    return [...groups.entries()]
  }, [tasks])
  const activeTasks = tasks.filter((task) => !task.conflictId && !task.syncDeleted)
  const deviceTimeZone = selectedDevice?.timeZone?.trim() || 'UTC'
  const heartbeatEligible = isScheduledTaskHeartbeatEligible(selectedDevice?.lastSeenAt, now)
  const supportsScheduledTasks = selectedDevice?.capabilities?.scheduledTasks === true
  const canEdit = Boolean(selectedDeviceId && config && heartbeatEligible && supportsScheduledTasks && !saving)
  const pendingTaskCommand = config?.pendingCommands
    .filter((command) => command.mutation.operations.some((operation) => operation.type === 'replace_scheduled_tasks'))
    .sort((left, right) => right.targetRevision - left.targetRevision)[0]

  const queueTasks = (nextTasks: NormalizedScheduledTask[], summary: string, onSaved?: () => void) => {
    if (!canEdit) {
      setError(t('scheduledTasks.writeUnavailable'))
      return
    }
    void queueMutation(buildReplaceScheduledTasksMutation(nextTasks), summary)
      .then(() => {
        setError('')
        onSaved?.()
      })
      .catch((nextError) => {
        setError(nextError instanceof Error ? nextError.message : t('common.saveFailed'))
      })
  }

  const openNewTask = () => {
    setEditingSyncId(null)
    setDraft(emptyDraft)
    setFormOpen(true)
  }

  const openEditTask = (task: NormalizedScheduledTask) => {
    setEditingSyncId(task.syncId)
    setDraft({
      name: task.name,
      cronExpression: task.cronExpression,
      simSlot: task.simSlot,
      mobiles: task.mobiles,
      content: task.content,
      enabled: task.status === 1
    })
    setFormOpen(true)
  }

  const saveTask = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!draft.name.trim() || !draft.cronExpression.trim() || !draft.mobiles.trim() || !draft.content.trim()) {
      setError(t('scheduledTasks.requiredFields'))
      return
    }
    const current = editingSyncId ? tasks.find((task) => task.syncId === editingSyncId) : undefined
    const saved: NormalizedScheduledTask = {
      syncId: current?.syncId ?? createSyncId(),
      name: draft.name.trim(),
      taskType: 'sms',
      cronExpression: draft.cronExpression.trim(),
      simSlot: draft.simSlot,
      mobiles: draft.mobiles.trim(),
      content: draft.content,
      status: draft.enabled ? 1 : 0,
      createdAt: current?.createdAt ?? Date.now(),
      conflictId: null,
      conflictSide: '',
      conflictPreviousStatus: null,
      syncDeleted: false
    }
    const next = current
      ? tasks.map((task) => task.syncId === current.syncId ? saved : task)
      : [...tasks, saved]
    queueTasks(next, editingSyncId ? t('scheduledTasks.updateSummary') : t('scheduledTasks.createSummary'), () => {
      setFormOpen(false)
      setEditingSyncId(null)
      setDraft(emptyDraft)
    })
  }

  const updateTask = (task: NormalizedScheduledTask, update: Partial<NormalizedScheduledTask>, summary: string) => {
    queueTasks(tasks.map((current) => current.syncId === task.syncId ? { ...current, ...update } : current), summary)
  }

  const resolveConflict = (conflictId: string, choice: 'local' | 'remote' | 'keep-both') => {
    try {
      queueTasks(resolveScheduledTaskConflict(tasks, conflictId, choice), t('scheduledTasks.resolveSummary'))
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : t('common.saveFailed'))
    }
  }

  const deviceOptions = devices.map((device) => ({
    value: device.id,
    label: device.displayName || device.deviceName,
    description: `${device.platform} / ${device.timeZone || 'UTC'}`
  }))
  const headerActions = (
    <div className="flex flex-wrap items-center gap-2">
      <RelayBadge tone={connected ? 'success' : 'warning'}>
        {connected ? t('common.liveConnected') : t('common.liveReconnecting')}
      </RelayBadge>
      {deviceOptions.length ? (
        <RelaySelect
          className="min-w-0 flex-1 sm:min-w-[15rem] sm:flex-none"
          value={selectedDeviceId ?? ''}
          options={deviceOptions}
          onChange={(value) => setSelectedDeviceId(Number(value))}
        />
      ) : null}
      <ActionButton onClick={() => void refreshConfig()}>{t('common.refresh')}</ActionButton>
    </div>
  )

  return (
    <PageShell
      title={t('scheduledTasks.title')}
      description={t('scheduledTasks.description')}
      badge={t('layout.nav.group.automation')}
      actions={headerActions}
    >
      <ErrorBanner message={error} />
      {loading && !config ? (
        <LoadingCard title={t('scheduledTasks.loadingTitle')} message={t('common.loadingMessage')} />
      ) : !selectedDevice ? (
        <EmptyCard title={t('scheduledTasks.noDevice')} message={t('scheduledTasks.noDeviceHint')} />
      ) : (
        <>
          <SurfaceCard title={t('scheduledTasks.deviceTitle')} subtitle={t('scheduledTasks.deviceSubtitle', { timeZone: deviceTimeZone })}>
            <div className="flex flex-wrap items-center gap-2">
              <RelayBadge>{t('records.deviceBadge', { deviceId: selectedDevice.id })}</RelayBadge>
              <RelayBadge tone={supportsScheduledTasks ? 'success' : 'warning'}>
                {supportsScheduledTasks ? t('scheduledTasks.capabilityReady') : t('scheduledTasks.capabilityUnavailable')}
              </RelayBadge>
              <RelayBadge tone={heartbeatEligible ? 'success' : 'warning'}>
                {heartbeatEligible ? t('scheduledTasks.heartbeatFresh') : t('scheduledTasks.heartbeatStale')}
              </RelayBadge>
              {pendingTaskCommand ? (
                <RelayBadge tone={pendingTaskCommand.status === 'pending' ? 'warning' : pendingTaskCommand.status === 'failed' || pendingTaskCommand.status === 'stale' ? 'danger' : 'success'}>
                  {t('scheduledTasks.commandState', { revision: pendingTaskCommand.targetRevision, status: pendingTaskCommand.status })}
                </RelayBadge>
              ) : null}
            </div>
            {!canEdit ? <p className="mt-3 text-sm text-[var(--relay-muted)]">{t('scheduledTasks.writeUnavailable')}</p> : null}
          </SurfaceCard>

          <SurfaceCard title={t('scheduledTasks.listTitle')} subtitle={t('scheduledTasks.listSubtitle')}>
            {activeTasks.length ? (
              <div className="divide-y divide-[var(--relay-border)]">
                {activeTasks.map((task) => (
                  <div key={task.syncId} className="flex flex-wrap items-center gap-4 py-3">
                    <div className="min-w-[14rem] flex-1">
                      <div className="font-medium text-[var(--relay-text)]">{task.name}</div>
                      <div className="mt-1 text-sm text-[var(--relay-muted)]">{task.cronExpression} · {t('scheduledTasks.simSlot', { simSlot: task.simSlot })}</div>
                      <div className="mt-1 text-sm text-[var(--relay-muted)]">{task.mobiles} · {task.content}</div>
                    </div>
                    <RelayBadge tone={task.status === 1 ? 'success' : 'muted'}>
                      {task.status === 1 ? t('common.enabled') : t('common.disabled')}
                    </RelayBadge>
                    <RelaySwitch
                      checked={task.status === 1}
                      onChange={(enabled) => updateTask(task, { status: enabled ? 1 : 0 }, t('scheduledTasks.updateSummary'))}
                      disabled={!canEdit}
                    />
                    <ActionButton disabled={!canEdit} onClick={() => openEditTask(task)}>{t('scheduledTasks.editAction')}</ActionButton>
                    <ActionButton
                      tone="danger"
                      disabled={!canEdit || saving}
                      onClick={() => {
                        if (window.confirm(t('scheduledTasks.deleteConfirm', { name: task.name }))) {
                          queueTasks(tasks.filter((candidate) => candidate.syncId !== task.syncId), t('scheduledTasks.deleteSummary'))
                        }
                      }}
                    >
                      {t('common.delete')}
                    </ActionButton>
                  </div>
                ))}
              </div>
            ) : <p className="text-sm text-[var(--relay-muted)]">{t('scheduledTasks.emptySubtitle')}</p>}
          </SurfaceCard>

          {conflicts.length ? (
            <SurfaceCard title={t('scheduledTasks.conflictsTitle')} subtitle={t('scheduledTasks.conflictsSubtitle')}>
              <div className="space-y-4">
                {conflicts.map(([conflictId, variants]) => (
                  <div key={conflictId} className="relay-history-row rounded-md border p-4">
                    <div className="grid gap-3 md:grid-cols-2">
                      {variants.map((task) => (
                        <div key={task.syncId}>
                          <RelayBadge tone="warning">{task.conflictSide === 'local' ? t('scheduledTasks.localVersion') : t('scheduledTasks.remoteVersion')}</RelayBadge>
                          <div className="mt-2 font-medium text-[var(--relay-text)]">{task.syncDeleted ? t('scheduledTasks.deletedTask', { name: task.name }) : task.name}</div>
                          <div className="mt-1 text-sm text-[var(--relay-muted)]">{task.cronExpression} · {task.mobiles}</div>
                        </div>
                      ))}
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <ActionButton disabled={!canEdit || saving} onClick={() => resolveConflict(conflictId, 'local')}>{t('scheduledTasks.useLocal')}</ActionButton>
                      <ActionButton disabled={!canEdit || saving} onClick={() => resolveConflict(conflictId, 'remote')}>{t('scheduledTasks.useRemote')}</ActionButton>
                      <ActionButton disabled={!canEdit || saving || variants.every((task) => task.syncDeleted)} onClick={() => resolveConflict(conflictId, 'keep-both')}>{t('scheduledTasks.keepBoth')}</ActionButton>
                    </div>
                  </div>
                ))}
              </div>
            </SurfaceCard>
          ) : null}

          <SurfaceCard
            title={t(formOpen && editingSyncId ? 'scheduledTasks.editTitle' : 'scheduledTasks.createTitle')}
            subtitle={t('scheduledTasks.formSubtitle')}
          >
            {!formOpen ? (
              <ActionButton tone="primary" disabled={!canEdit} onClick={openNewTask}>{t('scheduledTasks.addAction')}</ActionButton>
            ) : (
              <form className="grid gap-3 md:grid-cols-2" onSubmit={saveTask}>
                <label className="grid gap-1 text-sm text-[var(--relay-text-soft)]">
                  {t('scheduledTasks.field.name')}
                  <input className="relay-input" value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
                </label>
                <label className="grid gap-1 text-sm text-[var(--relay-text-soft)]">
                  {t('scheduledTasks.field.cron')}
                  <input className="relay-input" value={draft.cronExpression} onChange={(event) => setDraft({ ...draft, cronExpression: event.target.value })} />
                </label>
                <label className="grid gap-1 text-sm text-[var(--relay-text-soft)]">
                  {t('scheduledTasks.field.simSlot')}
                  <select className="relay-input" value={draft.simSlot} onChange={(event) => setDraft({ ...draft, simSlot: Number(event.target.value) })}>
                    <option value={0}>{t('scheduledTasks.sim.auto')}</option>
                    <option value={1}>{t('scheduledTasks.sim.one')}</option>
                    <option value={2}>{t('scheduledTasks.sim.two')}</option>
                  </select>
                </label>
                <label className="grid gap-1 text-sm text-[var(--relay-text-soft)]">
                  {t('scheduledTasks.field.mobiles')}
                  <input className="relay-input" value={draft.mobiles} onChange={(event) => setDraft({ ...draft, mobiles: event.target.value })} />
                </label>
                <label className="grid gap-1 text-sm text-[var(--relay-text-soft)] md:col-span-2">
                  {t('scheduledTasks.field.content')}
                  <textarea className="relay-input" rows={3} value={draft.content} onChange={(event) => setDraft({ ...draft, content: event.target.value })} />
                </label>
                <div className="flex items-center gap-2 md:col-span-2">
                  <RelaySwitch checked={draft.enabled} onChange={(enabled) => setDraft({ ...draft, enabled })} disabled={!canEdit || saving} />
                  <span className="text-sm text-[var(--relay-text-soft)]">{draft.enabled ? t('common.enabled') : t('common.disabled')}</span>
                </div>
                <div className="flex flex-wrap gap-2 md:col-span-2">
                  <ActionButton type="submit" tone="primary" disabled={!canEdit || saving}>{t('common.save')}</ActionButton>
                  <ActionButton onClick={() => { setFormOpen(false); setEditingSyncId(null); setDraft(emptyDraft) }}>{t('common.cancel')}</ActionButton>
                </div>
              </form>
            )}
          </SurfaceCard>
        </>
      )}
    </PageShell>
  )
}

function createSyncId() {
  if (typeof window !== 'undefined' && typeof window.crypto?.randomUUID === 'function') {
    return window.crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.random() * 16 | 0
    return (character === 'x' ? random : (random & 0x3 | 0x8)).toString(16)
  })
}
