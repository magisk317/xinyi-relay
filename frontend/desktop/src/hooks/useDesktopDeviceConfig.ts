import { startTransition, useCallback, useEffect, useMemo, useState } from 'react'
import { desktopApi } from '../api/desktopApi'
import { appendPendingCommand, deriveEffectiveConfigRoot, latestEffectiveRevision } from '../../../shared/deviceConfigCommands'
import { useDesktopRealtimeRefresh } from './useDesktopRealtimeRefresh'
import type {
  ConfigMutationBatch,
  DeviceConfigState,
  DeviceItem,
  RemoteConfigRoot
} from '../../../shared/contracts/console'

const SELECTED_DEVICE_STORAGE_KEY = 'relay-desktop-selected-device-id'

const DEVICE_CONFIG_REFRESH_EVENTS = [
  'device.registered',
  'device.updated',
  'device.revoked',
  'device.config.updated',
  'device.config.command.updated'
] as const

export function useDesktopDeviceConfig() {
  const [devices, setDevices] = useState<DeviceItem[]>([])
  const [selectedDeviceId, setSelectedDeviceIdState] = useState<number | null>(null)
  const [config, setConfig] = useState<DeviceConfigState | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const loadDevices = useCallback(async () => {
    const response = await desktopApi.getDevices()
    setDevices(response.devices)
    setSelectedDeviceIdState((current) => {
      const preferred = current ?? readStoredSelectedDeviceId()
      const nextDevice = pickDevice(response.devices, preferred)
      persistSelectedDeviceId(nextDevice?.id ?? null)
      return nextDevice?.id ?? null
    })
    return response.devices
  }, [])

  const loadConfig = useCallback(async (deviceId: number | null) => {
    if (!deviceId) {
      setConfig(null)
      return
    }
    const next = await desktopApi.getDeviceConfig(deviceId)
    setConfig(next)
  }, [])

  const refresh = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      const nextDevices = await loadDevices()
      const deviceId = selectedDeviceId ?? nextDevices[0]?.id ?? null
      await loadConfig(deviceId)
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Failed to load device config.')
    } finally {
      setLoading(false)
    }
  }, [loadConfig, loadDevices, selectedDeviceId])

  useEffect(() => {
    queueMicrotask(() => {
      void refresh()
    })
  }, [refresh])

  useEffect(() => {
    startTransition(() => {
      void loadConfig(selectedDeviceId).catch((nextError) => {
        setError(nextError instanceof Error ? nextError.message : 'Failed to load device config.')
      })
    })
  }, [loadConfig, selectedDeviceId])

  useDesktopRealtimeRefresh(() => {
    void refresh()
  }, DEVICE_CONFIG_REFRESH_EVENTS)

  const root = useMemo(() => (config ? deriveEffectiveRoot(config) : null), [config])

  const queueMutation = useCallback(async (mutation: ConfigMutationBatch, summary: string) => {
    if (!config || !selectedDeviceId) {
      throw new Error('Device config is not loaded yet.')
    }
    const baseRevision = latestEffectiveRevision(config)
    try {
      setSaving(true)
      const command = await desktopApi.queueDeviceConfigCommand(
        selectedDeviceId,
        baseRevision,
        mutation,
        summary
      )
      setConfig((current) =>
        current && current.deviceId === selectedDeviceId
          ? { ...current, pendingCommands: appendPendingCommand(current.pendingCommands, command) }
          : current
      )
      setError('')
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Failed to queue device config command.')
      throw nextError
    } finally {
      setSaving(false)
    }
  }, [config, selectedDeviceId])

  return {
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
    refresh,
    queueMutation
  }
}

function deriveEffectiveRoot(config: DeviceConfigState): RemoteConfigRoot {
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
  const storage = safeLocalStorage()
  const raw = storage?.getItem(SELECTED_DEVICE_STORAGE_KEY)
  if (!raw) return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}

function persistSelectedDeviceId(deviceId: number | null) {
  const storage = safeLocalStorage()
  if (!storage) return
  if (deviceId == null) {
    storage.removeItem(SELECTED_DEVICE_STORAGE_KEY)
    return
  }
  storage.setItem(SELECTED_DEVICE_STORAGE_KEY, String(deviceId))
}

function safeLocalStorage(): Storage | null {
  if (typeof window === 'undefined') return null
  return window.localStorage ?? null
}
