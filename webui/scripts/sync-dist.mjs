import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const webuiDir = path.resolve(__dirname, '..')
const distDir = path.join(webuiDir, 'dist')
const targetDir = path.resolve(webuiDir, '../app/src/main/assets/webui')

if (!existsSync(distDir)) {
  throw new Error(`dist not found: ${distDir}. Run pnpm -C webui build first.`)
}

rmSync(targetDir, { recursive: true, force: true })
mkdirSync(targetDir, { recursive: true })
cpSync(distDir, targetDir, { recursive: true })

console.log(`Synced WebUI dist -> ${targetDir}`)
