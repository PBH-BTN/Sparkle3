import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createMemoryHistory, createRouter } from 'vue-router'
import { installChunkRecovery } from '../src/router/chunkRecovery.ts'

const origin = 'https://sparkle.example'
const target = '/details?page=2#history'
const chunkError = new TypeError('Failed to fetch dynamically imported module: /assets/Details-old.js')

function createTestRouter(mock, currentUrl, storage, error = chunkError) {
  const location = Object.assign(new URL(currentUrl, origin), {
    assign: mock.fn(),
    reload: mock.fn(),
  })
  const history = { replaceState: mock.fn() }
  globalThis.window = Object.assign(new EventTarget(), { location, history, sessionStorage: storage })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: {} },
      { path: '/details', component: () => Promise.reject(error) },
    ],
  })
  installChunkRecovery(router)
  return { router, location }
}

function createStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key),
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  }
}

test('navigates to the full target URL when a route chunk is missing', async ({ mock }) => {
  const { router, location } = createTestRouter(mock, '/', createStorage())
  await router.push('/')
  await assert.rejects(router.push(target), chunkError)
  assert.equal(location.assign.mock.callCount(), 1)
  assert.equal(location.assign.mock.calls[0].arguments[0], origin + target)
})

test('reloads the current URL once if its route chunk is missing', async ({ mock }) => {
  const storage = createStorage()
  const logError = mock.method(console, 'error', () => {})
  const first = createTestRouter(mock, target, storage)
  await assert.rejects(first.router.push(target), chunkError)
  assert.equal(first.location.reload.mock.callCount(), 1)

  const retry = createTestRouter(mock, target, storage)
  await assert.rejects(retry.router.push(target), chunkError)
  assert.equal(retry.location.reload.mock.callCount(), 0)
  assert.equal(retry.location.assign.mock.callCount(), 0)
  assert.equal(logError.mock.callCount(), 1)

  await retry.router.push('/')
  await assert.rejects(retry.router.push(target), chunkError)
  assert.equal(retry.location.reload.mock.callCount(), 1)
})

test('does not reload for unrelated navigation errors', async ({ mock }) => {
  const error = new Error('Navigation guard failed')
  const logError = mock.method(console, 'error', () => {})
  const { router, location } = createTestRouter(mock, '/', createStorage(), error)
  await assert.rejects(router.push(target), error)
  assert.equal(location.assign.mock.callCount(), 0)
  assert.equal(location.reload.mock.callCount(), 0)
  assert.equal(logError.mock.callCount(), 1)
})
