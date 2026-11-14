import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
const exports = {}
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/utils/chartTooltip.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, { exports })
const { sortedTooltipItems, formatAxisTooltip, formatItemTooltip } = exports

test('tooltip values sort numerically descending and exclude zero and missing values', () => {
  const input = [{value: 2}, {value: 0}, {value: '100'}, {value: -3}, {value: null}, {value: NaN}]
  assert.equal(JSON.stringify(sortedTooltipItems(input).map(item => item.value)), '["100",2,-3]')
  assert.equal(input[0].value, 2)
})
test('axis tooltip uses hovered values, preserves formatting and escapes labels', () => {
  const result = formatAxisTooltip([
    {seriesName: '<low>', axisValueLabel: '<day>', value: 3},
    {seriesName: 'high', value: 20},
    {seriesName: 'zero', value: 0},
  ], value => value + ' bytes')
  assert.ok(result.startsWith('&lt;day&gt;'))
  assert.ok(result.indexOf('high') < result.indexOf('&lt;low&gt;'))
  assert.ok(result.includes('20 bytes'))
  assert.ok(!result.includes('zero'))
})
test('zero-only tooltips are hidden for both axis and item charts', () => {
  assert.equal(formatAxisTooltip([{value: 0}]), '')
  assert.equal(formatItemTooltip({value: 0}), '')
  assert.ok(formatItemTooltip({name: '<peer>', value: 1}).includes('&lt;peer&gt;'))
})
test('dataset tooltip values respect the encoded value dimension', () => {
  const result = sortedTooltipItems([
    {value: ['2026-11-14', 0], encode: {y: [1]}},
    {value: ['2026-11-14', 42], encode: {y: [1]}},
  ])
  assert.equal(result.length, 1)
  assert.equal(result[0].value[1], 42)
})
