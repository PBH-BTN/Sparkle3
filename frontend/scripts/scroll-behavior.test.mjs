import assert from 'node:assert/strict'
import test from 'node:test'
import { scrollBehavior } from '../src/router/scrollBehavior.ts'

const route = (overrides = {}) => ({
  name: 'ip-query',
  path: '/ip-query',
  hash: '',
  query: { q: '192.0.2.1', scope: '192.0.2.0/24' },
  ...overrides,
})

test('switching comment subnet keeps the current scroll position', () => {
  const from = route()
  const to = route({ query: { ...from.query, scope: '192.0.0.0/16' } })
  assert.equal(scrollBehavior(to, from, null), false)
})

test('a new IP query still scrolls to the top', () => {
  const to = route({ query: { q: '198.51.100.1', scope: '198.51.100.0/24' } })
  assert.deepEqual(scrollBehavior(to, route(), null), { top: 0 })
})

test('entering the query page from another page scrolls to the top', () => {
  assert.deepEqual(scrollBehavior(route(), route({ name: 'home', path: '/', query: {} }), null), { top: 0 })
})

test('hash navigation retains the existing scroll behavior', () => {
  const to = route({ hash: '#comments', query: { q: '192.0.2.1', scope: '192.0.0.0/16' } })
  assert.deepEqual(scrollBehavior(to, route(), null), { top: 0 })
})
