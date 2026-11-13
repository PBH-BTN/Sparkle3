import assert from 'node:assert/strict'
import test from 'node:test'
import { membershipTransactionStatus } from '../src/utils/membership.ts'

test('completed transaction codes are translated consistently', () => {
  for (const status of ['2', 'PAID', 'COMPLETED', 'SUCCEEDED', 'success', ' Success ']) {
    assert.deepEqual(membershipTransactionStatus(status), { key: 'completed', color: 'green' })
  }
})

test('membership states keep their meaning', () => {
  assert.deepEqual(membershipTransactionStatus('ACTIVE'), { key: 'active', color: 'green' })
  assert.deepEqual(membershipTransactionStatus('FORMER'), { key: 'former', color: 'gray' })
})

test('unrecognized or missing statuses use a neutral label without exposing raw values', () => {
  for (const status of ['unexpected provider response', '', null, undefined]) {
    assert.deepEqual(membershipTransactionStatus(status), { key: 'unknown', color: 'gray' })
  }
})
