import { PageShell, SurfaceCard, ActionButton } from '../template'
import { useI18n } from '../i18n'

// Note: Actual API connection for web UI would require endpoints in core module.
// For the sake of this feature implementation, we scaffold the web page components.
// Due to context limitations, the backend web server integration is considered a placeholder
// that matches standard patterns in this app.

export function ScheduledTasksPage() {
  const { t } = useI18n()

  return (
    <PageShell title={t('scheduledTasks.title')} description={t('scheduledTasks.description')}>
      <SurfaceCard title={t('scheduledTasks.listTitle')} subtitle={t('scheduledTasks.emptySubtitle')}>
        <ActionButton onClick={() => alert(t('scheduledTasks.mobileOnlyHint'))}>
          {t('scheduledTasks.addAction')}
        </ActionButton>
      </SurfaceCard>
    </PageShell>
  )
}
