import { createHash } from 'node:crypto'
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const webuiDir = path.resolve(__dirname, '..')
const distDir = path.join(webuiDir, 'dist')
const targetDir = path.resolve(webuiDir, '../app/src/main/assets/webui')

if (!existsSync(distDir)) {
  throw new Error(`dist not found: ${distDir}`)
}
if (!existsSync(targetDir)) {
  throw new Error(`target assets missing: ${targetDir}`)
}

function collectFiles(baseDir) {
  const files = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir)) {
      const full = path.join(dir, entry)
      const rel = path.relative(baseDir, full)
      const stat = statSync(full)
      if (stat.isDirectory()) {
        walk(full)
      } else {
        files.push(rel)
      }
    }
  }
  walk(baseDir)
  return files.sort()
}

function fileHash(filePath) {
  return createHash('sha256').update(readFileSync(filePath)).digest('hex')
}

const distFiles = collectFiles(distDir)
const targetFiles = collectFiles(targetDir)

const missing = distFiles.filter((f) => !targetFiles.includes(f))
const extra = targetFiles.filter((f) => !distFiles.includes(f))
const common = distFiles.filter((f) => targetFiles.includes(f))
const changed = common.filter((f) => {
  const a = fileHash(path.join(distDir, f))
  const b = fileHash(path.join(targetDir, f))
  return a !== b
})

if (missing.length || extra.length || changed.length) {
  console.error('WebUI dist mismatch with app/assets/webui')
  if (missing.length) console.error('Missing in assets:', missing)
  if (extra.length) console.error('Extra in assets:', extra)
  if (changed.length) console.error('Changed files:', changed)
  console.error('Run `pnpm -C webui sync-dist` and commit synced assets.')
  process.exit(1)
}

console.log('WebUI dist check passed.')
