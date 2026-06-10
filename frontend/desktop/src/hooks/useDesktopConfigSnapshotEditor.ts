import { useCallback, useState } from 'react'
import { desktopApi } from '../api/desktopApi'
import {
  loadNormalizedConfigSnapshot,
  normalizeConfigSnapshotError,
  saveNormalizedConfigSnapshot
} from '../configSnapshot'
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
      const next = await loadNormalizedConfigSnapshot(desktopApi.getConfigSnapshot)
      setConfig(next.config)
      setRoot(next.root)
      setError('')
      return next.config
    } catch (nextError) {
      const message = normalizeConfigSnapshotError(nextError, 'Failed to load desktop cloud snapshot.')
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
      const next = await saveNormalizedConfigSnapshot(config, nextRoot, desktopApi.putConfigSnapshot)
      setConfig(next.config)
      setRoot(next.root)
      setError('')
      return next.config
    } catch (nextError) {
      setError(normalizeConfigSnapshotError(nextError, 'Failed to save desktop cloud snapshot.'))
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
