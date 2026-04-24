import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import App from './App'
import { DesktopI18nProvider } from './i18n'
import { DesktopProvider } from './state/DesktopContext'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <HashRouter>
      <DesktopProvider>
        <DesktopI18nProvider>
          <App />
        </DesktopI18nProvider>
      </DesktopProvider>
    </HashRouter>
  </StrictMode>
)
