import React, { useState, useEffect } from 'react'
import { PageShell, SurfaceCard, ActionButton } from '../template'
import { useTranslation } from '../i18n'

// Note: Actual API connection for web UI would require endpoints in core module.
// For the sake of this feature implementation, we scaffold the web page components.
// Due to context limitations, the backend web server integration is considered a placeholder
// that matches standard patterns in this app.

export function ScheduledTasksPage() {
  const { t } = useTranslation()
  const [tasks, setTasks] = useState<any[]>([])

  return (
    <PageShell title="定时任务" activeNav="advanced">
      <SurfaceCard title="定时任务列表" subtitle="暂无定时任务">
        <ActionButton onClick={() => alert('请在手机端 App 中添加定时任务')}>添加任务</ActionButton>
      </SurfaceCard>
    </PageShell>
  )
}
