import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AppearanceProvider } from './appearance'
import { initAnalytics } from './analytics'
import { I18nProvider } from './i18n'
import './index.css'

initAnalytics()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <I18nProvider>
      <AppearanceProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AppearanceProvider>
    </I18nProvider>
  </StrictMode>
)
