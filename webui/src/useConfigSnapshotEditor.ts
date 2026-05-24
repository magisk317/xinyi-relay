import { useCallback, useState } from 'react'
import { apiClient } from './api/client'
import {
  latestConfigSnapshotFromError,
  loadNormalizedConfigSnapshot,
  normalizeConfigSnapshotError,
  saveNormalizedConfigSnapshot
} from './configSnapshot'
import type { ConfigSnapshotState, RemoteConfigRoot } from './types'

export function useConfigSnapshotEditor() {
  const [config, setConfig] = useState<ConfigSnapshotState | null>(null)
  const [root, setRoot] = useState<RemoteConfigRoot | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setLoading(true)
      const next = await loadNormalizedConfigSnapshot(apiClient.getConfigSnapshot)
      setConfig(next.config)
      setRoot(next.root)
      setError('')
      return next.config
    } catch (err) {
      const message = normalizeConfigSnapshotError(err, 'Failed to load cloud snapshot.')
      setError(message)
      throw err
    } finally {
      setLoading(false)
    }
  }, [])

  const saveRoot = useCallback(
    async (nextRoot: RemoteConfigRoot) => {
      if (!config) {
        throw new Error('Cloud snapshot is not loaded yet.')
      }
      try {
        setSaving(true)
        const next = await saveNormalizedConfigSnapshot(config, nextRoot, apiClient.putConfigSnapshot)
        setConfig(next.config)
        setRoot(next.root)
        setError('')
        return next.config
      } catch (err) {
        const latest = latestConfigSnapshotFromError(err)
        if (latest) {
          setConfig(latest.config)
          setRoot(latest.root)
          setError(normalizeConfigSnapshotError(err, 'Cloud config changed on another client.'))
        } else {
          setError(normalizeConfigSnapshotError(err, 'Failed to save cloud snapshot.'))
        }
        throw err
      } finally {
        setSaving(false)
      }
    },
    [config]
  )

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
