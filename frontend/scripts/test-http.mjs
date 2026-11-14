import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const messages = []
const events = []
globalThis.document = { cookie: 'XSRF-TOKEN=csrf' }
globalThis.window = { dispatchEvent: event => events.push(event.type) }
globalThis.CustomEvent = class { constructor(type) { this.type = type } }
globalThis.__httpTest = {
  Message: { error: value => messages.push(value) },
  i18n: { global: { t: key => key } },
}
const source = (await readFile(new URL('../src/api/http.ts', import.meta.url), 'utf8'))
  .replace(/import \{ Message \} from '@arco-design\/web-vue'/, 'const { Message } = globalThis.__httpTest')
  .replace(/import i18n from '@\/i18n'/, 'const { i18n } = globalThis.__httpTest')
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const { request, ApiError } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'))
for (const status of [400, 401, 403, 500]) {
  globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Save failed' }), { status, headers: { 'content-type': 'application/json' } })
  await assert.rejects(request('/test'), error => error instanceof ApiError && error.status === status)
  assert.equal(messages.at(-1).content, 'Save failed')
}
assert.deepEqual(events, ['sparkle:unauthorized'])
globalThis.fetch = async () => { throw new TypeError('private network details') }
await assert.rejects(request('/test'), { message: 'common.networkError' })
assert.equal(messages.at(-1).content, 'common.networkError')
globalThis.fetch = async () => new Response('invalid', { headers: { 'content-type': 'application/json' } })
await assert.rejects(request('/test'), { message: 'common.invalidResponse' })
globalThis.fetch = async () => new Response('<html>internal details</html>', { status: 502 })
await assert.rejects(request('/test'), { message: 'common.requestFailed' })
const count = messages.length
globalThis.fetch = async (_, init) => {
  assert.equal(init.headers.get('X-XSRF-TOKEN'), 'csrf')
  return new Response(null, { status: 204 })
}
assert.equal(await request('/test', { method: 'PUT' }), undefined)
globalThis.fetch = async () => new Response('{"ok":true}', { headers: { 'content-type': 'application/json' } })
assert.deepEqual(await request('/test'), { ok: true })
assert.equal(messages.length, count)
console.log('HTTP error notifications, authentication, CSRF and success responses verified')
