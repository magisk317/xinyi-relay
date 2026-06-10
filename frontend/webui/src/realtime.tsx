import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren
} from 'react'

export type RealtimeEvent = {
  type: string
  time: string
  data: Record<string, unknown> | null
}

type RealtimeState = {
  connected: boolean
  lastEvent: RealtimeEvent | null
}

const RealtimeContext = createContext<RealtimeState | null>(null)

export function RealtimeProvider({ children }: PropsWithChildren) {
  const [connected, setConnected] = useState(false)
  const [lastEvent, setLastEvent] = useState<RealtimeEvent | null>(null)

  useEffect(() => {
    let socket: WebSocket | null = null
    let reconnectTimer: number | undefined
    let pingTimer: number | undefined
    let stopped = false

    const stopPing = () => {
      if (pingTimer != null) {
        window.clearInterval(pingTimer)
        pingTimer = undefined
      }
    }

    const connect = () => {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      socket = new WebSocket(`${protocol}//${window.location.host}/api/v1/realtime/ws`)

      socket.addEventListener('open', () => {
        setConnected(true)
        stopPing()
        pingTimer = window.setInterval(() => {
          if (socket?.readyState === WebSocket.OPEN) {
            socket.send('ping')
          }
        }, 25000)
      })

      socket.addEventListener('message', (event) => {
        try {
          setLastEvent(JSON.parse(event.data) as RealtimeEvent)
        } catch {
          // Ignore malformed events from experimental backends.
        }
      })

      socket.addEventListener('close', () => {
        setConnected(false)
        stopPing()
        if (!stopped) {
          reconnectTimer = window.setTimeout(connect, 2000)
        }
      })

      socket.addEventListener('error', () => {
        setConnected(false)
      })
    }

    connect()

    return () => {
      stopped = true
      stopPing()
      if (reconnectTimer != null) {
        window.clearTimeout(reconnectTimer)
      }
      socket?.close()
    }
  }, [])

  const value = useMemo(
    () => ({
      connected,
      lastEvent
    }),
    [connected, lastEvent]
  )

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

export function useRealtimeFeed() {
  const value = useContext(RealtimeContext)
  if (!value) {
    throw new Error('useRealtimeFeed must be used inside RealtimeProvider')
  }
  return value
}
