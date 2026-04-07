import { openUrl } from '@tauri-apps/plugin-shell'

const STORAGE_KEY = 'relay-desktop-backend-url'
const defaultUrl = 'https://localhost:8443'

const input = document.querySelector<HTMLInputElement>('#backend-url')
const saveButton = document.querySelector<HTMLButtonElement>('#save-btn')
const embedButton = document.querySelector<HTMLButtonElement>('#embed-btn')
const openButton = document.querySelector<HTMLButtonElement>('#open-btn')
const status = document.querySelector<HTMLParagraphElement>('#status')
const viewerUrl = document.querySelector<HTMLDivElement>('#viewer-url')
const frame = document.querySelector<HTMLIFrameElement>('#console-frame')

function setStatus(message: string): void {
  if (status) status.textContent = message
}

function normalizeUrl(value: string): string {
  return value.trim().replace(/\/+$/, '')
}

function readUrl(): string {
  return normalizeUrl(window.localStorage.getItem(STORAGE_KEY) ?? defaultUrl)
}

function writeUrl(value: string): void {
  window.localStorage.setItem(STORAGE_KEY, normalizeUrl(value))
}

function setEmbeddedUrl(value: string): void {
  if (frame) frame.src = value
  if (viewerUrl) viewerUrl.textContent = value
}

function resolveNextUrl(): string {
  return normalizeUrl(input?.value ?? readUrl())
}

if (input) {
  input.value = readUrl()
}

saveButton?.addEventListener('click', () => {
  const nextValue = resolveNextUrl()
  if (!nextValue) {
    setStatus('Backend URL is required.')
    return
  }
  writeUrl(nextValue)
  setStatus(`Saved backend URL: ${nextValue}`)
})

embedButton?.addEventListener('click', () => {
  const nextValue = resolveNextUrl()
  if (!nextValue) {
    setStatus('Backend URL is required.')
    return
  }
  writeUrl(nextValue)
  setEmbeddedUrl(nextValue)
  setStatus(`Embedded console: ${nextValue}`)
})

openButton?.addEventListener('click', async () => {
  const nextValue = resolveNextUrl()
  if (!nextValue) {
    setStatus('Backend URL is required.')
    return
  }
  writeUrl(nextValue)
  try {
    await openUrl(nextValue)
    setStatus(`Opened console: ${nextValue}`)
  } catch (error) {
    setStatus(error instanceof Error ? error.message : 'Failed to open remote console.')
  }
})

frame?.addEventListener('load', () => {
  const current = frame.src || readUrl()
  setStatus(`Embedded console loaded: ${current}`)
})

setEmbeddedUrl(readUrl())
setStatus(`Ready: ${readUrl()}`)
