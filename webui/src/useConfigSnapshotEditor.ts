import { useCallback, useState } from 'react'
import { apiClient } from './api/client'
import { ConfigConflictError, normalizeConfigRoot } from './configSnapshot'
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
      const next = await apiClient.getConfigSnapshot()
      setConfig(next)
      setRoot(normalizeConfigRoot(next.snapshot))
      setError('')
      return next
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load cloud snapshot.'
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
        const next = await apiClient.putConfigSnapshot(config.revision, nextRoot)
        setConfig(next)
        setRoot(normalizeConfigRoot(next.snapshot))
        setError('')
        return next
      } catch (err) {
        if (err instanceof ConfigConflictError) {
          setConfig(err.latest)
          setRoot(normalizeConfigRoot(err.latest.snapshot))
          setError(err.message)
        } else {
          setError(err instanceof Error ? err.message : 'Failed to save cloud snapshot.')
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
