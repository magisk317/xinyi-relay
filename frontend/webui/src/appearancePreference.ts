export type AppearanceChoice = 'system' | 'light' | 'dark'
export type ResolvedAppearance = 'light' | 'dark'

export type AppearanceStorage = Pick<Storage, 'getItem' | 'setItem'>

export const APPEARANCE_STORAGE_KEY = 'relay-webui-appearance'

function browserStorage(): AppearanceStorage | null {
  if (typeof window === 'undefined') return null
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function loadAppearancePreference(storage: AppearanceStorage | null = browserStorage()): AppearanceChoice {
  if (!storage) return 'system'
  try {
    const value = storage.getItem(APPEARANCE_STORAGE_KEY)
    return value === 'light' || value === 'dark' || value === 'system' ? value : 'system'
  } catch {
    return 'system'
  }
}

export function saveAppearancePreference(
  appearance: AppearanceChoice,
  storage: AppearanceStorage | null = browserStorage()
): void {
  try {
    storage?.setItem(APPEARANCE_STORAGE_KEY, appearance)
  } catch {
    // Storage may be disabled by the browser; the in-memory choice still applies.
  }
}

export function resolveAppearance(appearance: AppearanceChoice, prefersDark: boolean): ResolvedAppearance {
  if (appearance === 'system') return prefersDark ? 'dark' : 'light'
  return appearance
}
