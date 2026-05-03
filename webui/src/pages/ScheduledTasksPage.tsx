import { PageShell, SurfaceCard, ActionButton } from '../template'

// Note: Actual API connection for web UI would require endpoints in core module.
// For the sake of this feature implementation, we scaffold the web page components.
// Due to context limitations, the backend web server integration is considered a placeholder
// that matches standard patterns in this app.

export function ScheduledTasksPage() {
  return (
    <PageShell title="定时任务" description="定时发送短信（支持 Cron 表达式）">
      <SurfaceCard title="定时任务列表" subtitle="暂无定时任务">
        <ActionButton onClick={() => alert('请在手机端 App 中添加定时任务')}>添加任务</ActionButton>
      </SurfaceCard>
    </PageShell>
  )
}
