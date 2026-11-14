import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url)
const source = ts.transpileModule(readFileSync(new URL('../src/composables/useTheme.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
function setup(saved = null, dark = false, blocked = false) {
  const listeners = {}
  const attributes = new Map()
  const root = { style: {}, dataset: {} }
  const storage = new Map(saved === null ? [] : [['sparkle:theme', saved]])
  const exports = {}
  vm.runInNewContext(source, {
    exports, require,
    localStorage: {
      getItem: (key) => { if (blocked) throw Error('blocked'); return storage.get(key) ?? null },
      setItem: (key, value) => { if (blocked) throw Error('blocked'); storage.set(key, value) },
    },
    window: {
      matchMedia: () => ({ matches: dark, addEventListener: (_, fn) => { listeners.media = fn } }),
      addEventListener: (_, fn) => { listeners.storage = fn },
    },
    document: {
      body: { setAttribute: (k, v) => attributes.set(k, v), removeAttribute: (k) => attributes.delete(k) },
      documentElement: root, querySelector: () => null,
    },
  })
  return { ...exports, listeners, attributes, root, storage }
}
test('system default follows changes; explicit light overrides the system', () => {
  const app = setup(null, true)
  assert.equal(app.useTheme().mode.value, 'system')
  assert.equal(app.attributes.get('arco-theme'), 'dark')
  app.listeners.media({ matches: false })
  assert.equal(app.root.style.colorScheme, 'light')
  app.setThemeMode('light')
  app.listeners.media({ matches: true })
  assert.equal(app.root.dataset.theme, 'light')
  assert.equal(app.attributes.has('arco-theme'), false)
  app.setThemeMode('system')
  assert.equal(app.root.dataset.theme, 'dark')
})
test('saved dark preference survives initialization and synchronizes storage changes', () => {
  const app = setup('dark')
  assert.equal(app.root.dataset.theme, 'dark')
  app.storage.set('sparkle:theme', 'light')
  app.listeners.storage({ key: 'sparkle:theme' })
  assert.equal(app.useTheme().mode.value, 'light')
  app.storage.clear()
  app.listeners.storage({ key: null })
  assert.equal(app.useTheme().mode.value, 'system')
  app.setThemeMode('dark')
  assert.equal(app.storage.get('sparkle:theme'), 'dark')
})
test('invalid or unavailable storage falls back to system and permits session changes', () => {
  assert.equal(setup('invalid').useTheme().mode.value, 'system')
  const app = setup(null, false, true)
  app.setThemeMode('dark')
  assert.equal(app.root.dataset.theme, 'dark')
})
