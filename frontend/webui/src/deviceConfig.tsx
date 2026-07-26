import {
  createContext,
  startTransition,
  useCallback,
  useContext,
  useEffect,
  useEffectEvent,
  useMemo,
  useState,
  type PropsWithChildren
} from 'react'
import { apiClient } from './api/client'
import { appendPendingCommand, deriveEffectiveConfigRoot, latestEffectiveRevision } from '../../shared/deviceConfigCommands'
import { useRealtimeFeed } from './realtime'
import type {
  ConfigMutationBatch,
  DeviceConfigState,
  DeviceItem,
  RemoteConfigRoot
} from './types'

const SELECTED_DEVICE_STORAGE_KEY = 'relay-webui-selected-device-id'

type DeviceConfigContextValue = {
  devices: DeviceItem[]
  selectedDeviceId: number | null
  setSelectedDeviceId: (deviceId: number) => void
  config: DeviceConfigState | null
  root: RemoteConfigRoot | null
  loading: boolean
  saving: boolean
  error: string
  setError: (message: string) => void
  refreshDevices: () => Promise<void>
  refreshConfig: () => Promise<void>
  queueMutation: (mutation: ConfigMutationBatch, summary: string) => Promise<void>
}

const DeviceConfigContext = createContext<DeviceConfigContextValue | null>(null)

export function DeviceConfigProvider({ children }: PropsWithChildren) {
  const { lastEvent } = useRealtimeFeed()
  const [devices, setDevices] = useState<DeviceItem[]>([])
  const [selectedDeviceId, setSelectedDeviceIdState] = useState<number | null>(null)
  const [config, setConfig] = useState<DeviceConfigState | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const loadDevices = useCallback(async () => {
    const response = await apiClient.getDevices()
    setDevices(response.devices)
    setSelectedDeviceIdState((current) => {
      const preferred = current ?? readStoredSelectedDeviceId()
      const nextDevice = pickDevice(response.devices, preferred)
      persistSelectedDeviceId(nextDevice?.id ?? null)
      return nextDevice?.id ?? null
    })
  }, [])

  const loadConfig = useCallback(async (deviceId: number | null) => {
    if (!deviceId) {
      setConfig(null)
      setLoading(false)
      return
    }
    const next = await apiClient.getDeviceConfig(deviceId)
    setConfig(next)
  }, [])

  const refreshDevices = useCallback(async () => {
    try {
      await loadDevices()
      setError('')
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Failed to load devices.')
      throw nextError
    }
  }, [loadDevices])

  const refreshConfig = useCallback(async () => {
    try {
      setLoading(true)
      await loadConfig(selectedDeviceId)
      setError('')
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Failed to load device config.')
      throw nextError
    } finally {
      setLoading(false)
    }
  }, [loadConfig, selectedDeviceId])

  const queueMutation = useCallback(
    async (mutation: ConfigMutationBatch, summary: string) => {
      if (!config || !selectedDeviceId) {
        throw new Error('Device config is not loaded yet.')
      }
      const baseRevision = latestEffectiveRevision(config)
      try {
        setSaving(true)
        const command = await apiClient.queueDeviceConfigCommand(
          selectedDeviceId,
          baseRevision,
          mutation,
          summary
        )
        setConfig((current) =>
          current && current.deviceId === selectedDeviceId
            ? {
                ...current,
                pendingCommands: appendPendingCommand(current.pendingCommands, command)
              }
            : current
        )
        setError('')
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : 'Failed to queue device config command.')
        throw nextError
      } finally {
        setSaving(false)
      }
    },
    [config, selectedDeviceId]
  )

  useEffect(() => {
    queueMicrotask(() => {
      void refreshDevices()
    })
  }, [refreshDevices])

  useEffect(() => {
    startTransition(() => {
      void refreshConfig()
    })
  }, [refreshConfig])

  const handleRealtimeEvent = useEffectEvent((event: { type: string; data?: unknown }) => {
    const data = asRecord(event.data)
    const deviceId = asNumber(data?.deviceId)
    if (event.type === 'device.registered' || event.type === 'device.updated' || event.type === 'device.revoked') {
      queueMicrotask(() => {
        void refreshDevices().then(() => refreshConfig()).catch(() => {})
      })
      return
    }
    if (
      (event.type === 'device.config.updated' || event.type === 'device.config.command.updated') &&
      deviceId != null &&
      deviceId === selectedDeviceId
    ) {
      queueMicrotask(() => {
        void refreshConfig().catch(() => {})
      })
    }
  })

  useEffect(() => {
    if (!lastEvent) return
    handleRealtimeEvent(lastEvent)
  }, [lastEvent])

  const root = useMemo(() => deriveEffectiveRoot(config), [config])

  const value = useMemo<DeviceConfigContextValue>(
    () => ({
      devices,
      selectedDeviceId,
      setSelectedDeviceId: (deviceId: number) => {
        persistSelectedDeviceId(deviceId)
        setSelectedDeviceIdState(deviceId)
      },
      config,
      root,
      loading,
      saving,
      error,
      setError,
      refreshDevices,
      refreshConfig,
      queueMutation
    }),
    [config, devices, error, loading, queueMutation, refreshConfig, refreshDevices, root, saving, selectedDeviceId]
  )

  return <DeviceConfigContext.Provider value={value}>{children}</DeviceConfigContext.Provider>
}

export function useDeviceConfig() {
  const value = useContext(DeviceConfigContext)
  if (!value) {
    throw new Error('useDeviceConfig must be used inside DeviceConfigProvider')
  }
  return value
}

function deriveEffectiveRoot(config: DeviceConfigState | null): RemoteConfigRoot | null {
  if (!config) return null
  return deriveEffectiveConfigRoot(config)
}

function pickDevice(devices: DeviceItem[], preferredId: number | null): DeviceItem | null {
  if (!devices.length) return null
  if (preferredId != null) {
    const preferred = devices.find((device) => device.id === preferredId)
    if (preferred) return preferred
  }
  return devices[0] ?? null
}

function readStoredSelectedDeviceId(): number | null {
  const raw = window.localStorage.getItem(SELECTED_DEVICE_STORAGE_KEY)
  if (!raw) return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}

function persistSelectedDeviceId(deviceId: number | null) {
  if (deviceId == null) {
    window.localStorage.removeItem(SELECTED_DEVICE_STORAGE_KEY)
    return
  }
  window.localStorage.setItem(SELECTED_DEVICE_STORAGE_KEY, String(deviceId))
}

function asNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null
}
