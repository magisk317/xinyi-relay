import { useEffect } from 'react'
import { useDesktop } from '../state/DesktopContext'
import type { RealtimeEventType } from '../../../shared/contracts/console'

export function useDesktopRealtimeRefresh(
  onRefresh: () => void,
  eventTypes: readonly RealtimeEventType[]
) {
  const { lastRealtimeEvent } = useDesktop()

  useEffect(() => {
    if (!lastRealtimeEvent || !eventTypes.includes(lastRealtimeEvent.type)) {
      return
    }
    onRefresh()
  }, [eventTypes, lastRealtimeEvent, onRefresh])
}
