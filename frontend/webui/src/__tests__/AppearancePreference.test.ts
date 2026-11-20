import { describe, expect, it } from 'vitest'
import {
  loadAppearancePreference,
  resolveAppearance,
  saveAppearancePreference,
  type AppearanceStorage
} from '../appearancePreference'

function createStorage(initial: string | null = null) {
  let storedValue = initial
  const storage: AppearanceStorage = {
    getItem: () => storedValue,
    setItem: (_key, value) => {
      storedValue = value
    }
  }
  return { storage, value: () => storedValue }
}

describe('appearance preference', () => {
  it('keeps an explicit choice in local storage', () => {
    const { storage } = createStorage()
    saveAppearancePreference('dark', storage)
    expect(loadAppearancePreference(storage)).toBe('dark')

    saveAppearancePreference('light', storage)
    expect(loadAppearancePreference(storage)).toBe('light')
  })

  it('uses system appearance when missing or invalid', () => {
    const empty = createStorage()
    expect(loadAppearancePreference(empty.storage)).toBe('system')

    const invalid = createStorage('sepia')
    expect(loadAppearancePreference(invalid.storage)).toBe('system')
  })

  it('resolves system choice from the current system preference', () => {
    expect(resolveAppearance('system', true)).toBe('dark')
    expect(resolveAppearance('system', false)).toBe('light')
    expect(resolveAppearance('light', true)).toBe('light')
    expect(resolveAppearance('dark', false)).toBe('dark')
  })
})
