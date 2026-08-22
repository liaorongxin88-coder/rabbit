import { access, readFile, readdir, stat } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(scriptDir, '../..')
const requested = process.argv.slice(2)
const defaultTargets = [
  'README.md',
  'CONTRIBUTING.md',
  'backend/README.md',
  'admin/README.md',
  'app/README.md',
  'docs',
]

async function collectMarkdown(target, files) {
  const absolute = path.resolve(rootDir, target)
  const targetStat = await stat(absolute)
  if (targetStat.isFile()) {
    if (absolute.endsWith('.md')) files.push(absolute)
    return
  }
  for (const entry of await readdir(absolute, { withFileTypes: true })) {
    if (entry.name === 'archive' || entry.name === 'node_modules') continue
    await collectMarkdown(path.join(target, entry.name), files)
  }
}

function linkTargets(markdown) {
  const targets = []
  const inline = /!?\[[^\]]*\]\(([^)]+)\)/g
  const reference = /^\s*\[[^\]]+\]:\s*(\S+)/gm
  for (const match of markdown.matchAll(inline)) {
    targets.push(match[1].trim().replace(/^<|>$/g, '').split(/\s+["']/)[0])
  }
  for (const match of markdown.matchAll(reference)) targets.push(match[1].trim())
  return targets
}

function localPath(target) {
  if (!target || target.startsWith('#')) return null
  if (/^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(target)) return null
  const withoutAnchor = target.split('#', 1)[0].split('?', 1)[0]
  if (!withoutAnchor) return null
  try {
    return decodeURIComponent(withoutAnchor)
  } catch {
    return withoutAnchor
  }
}

const files = []
for (const target of requested.length > 0 ? requested : defaultTargets) {
  await collectMarkdown(target, files)
}

const failures = []
let checkedLinks = 0
for (const file of files) {
  const markdown = await readFile(file, 'utf8')
  for (const target of linkTargets(markdown)) {
    const relative = localPath(target)
    if (relative === null) continue
    checkedLinks += 1
    const resolved = relative.startsWith('/')
      ? path.resolve(rootDir, `.${relative}`)
      : path.resolve(path.dirname(file), relative)
    try {
      await access(resolved)
    } catch {
      failures.push(`${path.relative(rootDir, file)} -> ${target}`)
    }
  }
}

if (failures.length > 0) {
  console.error('Broken local Markdown links:')
  for (const failure of [...new Set(failures)]) console.error(`- ${failure}`)
  process.exitCode = 1
} else {
  console.log(`Markdown links passed: ${files.length} files, ${checkedLinks} local links`)
}
