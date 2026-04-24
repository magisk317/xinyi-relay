import { useCallback, useState } from 'react'
import { desktopApi } from '../api/desktopApi'
import { normalizeConfigRoot } from '../configSnapshot'
import type { ConfigSnapshotState, RemoteConfigRoot } from '../../../shared/contracts/console'

export function useDesktopConfigSnapshotEditor() {
  const [config, setConfig] = useState<ConfigSnapshotState | null>(null)
  const [root, setRoot] = useState<RemoteConfigRoot | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setLoading(true)
      const next = await desktopApi.fetchConfigSnapshot()
      setConfig(next)
      setRoot(normalizeConfigRoot(next.snapshot))
      setError('')
      return next
    } catch (nextError) {
      const message = nextError instanceof Error ? nextError.message : 'Failed to load desktop cloud snapshot.'
      setError(message)
      throw nextError
    } finally {
      setLoading(false)
    }
  }, [])

  const saveRoot = useCallback(async (nextRoot: RemoteConfigRoot) => {
    if (!config) {
      throw new Error('Cloud snapshot is not loaded yet.')
    }
    try {
      setSaving(true)
      const next = await desktopApi.putConfigSnapshot(config.revision, nextRoot)
      setConfig(next)
      setRoot(normalizeConfigRoot(next.snapshot))
      setError('')
      return next
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Failed to save desktop cloud snapshot.')
      throw nextError
    } finally {
      setSaving(false)
    }
  }, [config])

  return {
    config,
    root,
    loading,
    saving,
    error,
    setError,
    setRoot,
    load,
    saveRoot
  }
}
