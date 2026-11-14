import assert from 'node:assert/strict'
import { readdir, readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import ts from 'typescript'
import { createI18n } from 'vue-i18n'

const frontend = fileURLToPath(new URL('../', import.meta.url))
const backend = join(frontend, '../src/main/java')

async function javaSources(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const sources = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return javaSources(path)
    return entry.name.endsWith('.java') ? [await readFile(path, 'utf8')] : []
  }))
  return sources.flat()
}

const keys = new Set((await javaSources(backend)).flatMap((source) =>
  [...source.matchAll(/"(admin\.tasks\.items\.[a-z0-9-]+\.(?:name|description))"/g)]
    .map((match) => match[1]),
))
assert.ok(keys.size > 0, 'No backend task translations discovered')

const missing = []
for (const locale of ['zh-CN', 'en-US']) {
  const source = await readFile(join(frontend, `src/i18n/locales/${locale}.ts`), 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext },
  })
  const { default: messages } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
  const i18n = createI18n({ legacy: false, locale, messages: { [locale]: messages } })
  for (const key of keys) {
    if (!i18n.global.te(key)) {
      missing.push(`${locale}: ${key}`)
      continue
    }
    const rendered = i18n.global.t(key)
    assert.ok(rendered.trim() && rendered !== key, `${locale}: unresolved task translation ${key}`)
  }
}

assert.deepEqual(missing, [], `Missing task translations: ${missing.join(', ')}`)
console.log(`Verified ${keys.size} backend task name and description keys in both locales`)
