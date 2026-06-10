const measurementId = import.meta.env.VITE_GA_MEASUREMENT_ID as string | undefined
let initialized = false

type AnalyticsValue = string | number | boolean | null | undefined
type AnalyticsParams = Record<string, AnalyticsValue>
type GtagCommand = 'js' | 'config' | 'event'
type Gtag = (command: GtagCommand, target: string | Date, params?: AnalyticsParams) => void

declare global {
  interface Window {
    gtag?: Gtag
    dataLayer?: unknown[]
  }
}

function ensureGtag(): void {
  if (!measurementId || initialized) return
  initialized = true

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${measurementId}`
  document.head.appendChild(script)

  const inline = document.createElement('script')
  inline.innerHTML = `window.dataLayer = window.dataLayer || [];
function gtag(){dataLayer.push(arguments);} 
gtag('js', new Date());
gtag('config', '${measurementId}', { anonymize_ip: true });`
  document.head.appendChild(inline)
}

export function initAnalytics(): void {
  ensureGtag()
}

export function trackPageView(path: string): void {
  if (!measurementId) return
  ensureGtag()
  const gtag = window.gtag
  if (!gtag) return
  gtag('event', 'page_view', { page_path: path })
}

export function trackEvent(name: string, params: AnalyticsParams = {}): void {
  if (!measurementId) return
  ensureGtag()
  const gtag = window.gtag
  if (!gtag) return
  gtag('event', name, params)
}
