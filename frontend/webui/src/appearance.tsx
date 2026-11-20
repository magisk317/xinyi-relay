import { createContext, useContext, useLayoutEffect, useState, type PropsWithChildren } from 'react'
import {
  loadAppearancePreference,
  resolveAppearance,
  saveAppearancePreference,
  type AppearanceChoice
} from './appearancePreference'

type AppearanceContextValue = {
  appearance: AppearanceChoice
  setAppearance: (appearance: AppearanceChoice) => void
}

const AppearanceContext = createContext<AppearanceContextValue | null>(null)

export function AppearanceProvider({ children }: PropsWithChildren) {
  const [appearance, setAppearance] = useState<AppearanceChoice>(() => loadAppearancePreference())

  useLayoutEffect(() => {
    saveAppearancePreference(appearance)
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const apply = () => {
      document.documentElement.dataset.theme = resolveAppearance(appearance, media.matches)
      document.documentElement.dataset.appearance = appearance
    }
    apply()
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [appearance])

  return (
    <AppearanceContext.Provider value={{ appearance, setAppearance }}>
      {children}
    </AppearanceContext.Provider>
  )
}

export function useAppearance() {
  const value = useContext(AppearanceContext)
  if (!value) throw new Error('useAppearance must be used inside AppearanceProvider')
  return value
}
