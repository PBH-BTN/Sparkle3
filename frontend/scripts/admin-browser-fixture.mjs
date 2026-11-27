import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import ts from 'typescript'
import { chromium, expect } from '@playwright/test'

export async function adminBrowserFixture(name) {
  const base = process.env.SPARKLE_ADMIN_BROWSER_URL ?? 'http://127.0.0.1:17799'
  assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname))
  const frontend = fileURLToPath(new URL('../', import.meta.url))
  const output = join(frontend, '../build/reports/admin-browser', name)
  await mkdir(output, { recursive: true })
  const { outputText } = ts.transpileModule(await readFile(join(frontend, 'src/i18n/locales/en-US.ts'), 'utf8'), { compilerOptions: { module: ts.ModuleKind.ESNext } })
  const { default: messages } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
  const label = (key) => key.split('.').reduce((object, part) => object?.[part], messages)
  const browser = await chromium.launch()
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 } })
  const login = await context.request.get(base + '/__admin-test/login?role=admin', { maxRedirects: 0 })
  assert.equal(login.status(), 302)
  assert.ok((await context.cookies()).some((cookie) => cookie.name === 'sparkle-admin-test-role'), 'Refusing writes outside test fixture')
  await context.addInitScript(() => localStorage.setItem('sparkle-locale', 'en-US'))
  await context.route('**/*', (route) => new URL(route.request().url()).origin === new URL(base).origin ? route.continue() : route.abort())
  const page = await context.newPage()
  page.setDefaultTimeout(15000)
  const results = []
  let errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('response', (response) => { if (response.url().startsWith(base + '/api/admin/') && response.status() >= 400) errors.push(`${response.status()} ${new URL(response.url()).pathname}`) })
  async function check(title, action) {
    if (process.env.SPARKLE_ADMIN_FORM_FILTER && !new RegExp(process.env.SPARKLE_ADMIN_FORM_FILTER, 'i').test(title)) return
    errors = []
    try { await action(); assert.deepEqual(errors, []); results.push({ title, passed: true }); console.log(`PASS ${title}`) }
    catch (error) { results.push({ title, passed: false, error: error.message }); console.log(`FAIL ${title}: ${error.message}`); await page.screenshot({ path: join(output, `failure-${results.length}.png`), fullPage: true }).catch(() => {}) }
  }
  async function open(path) {
    assert.equal((await page.goto(base + path)).status(), 200)
    await expect(page.locator('.admin-header')).toBeVisible()
    await page.waitForLoadState('networkidle')
  }
  async function request(method, path, body) {
    const token = (await context.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
    const response = await context.request.fetch(base + path, { method, data: body, headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) } })
    assert.ok(response.ok(), `${method} ${path}: ${response.status()} ${await response.text()}`)
    const text = await response.text()
    return !text ? undefined : response.headers()['content-type']?.includes('application/json') ? JSON.parse(text) : text
  }
  async function submit(method, path, click) {
    const [response] = await Promise.all([page.waitForResponse((response) => new URL(response.url()).pathname === path && response.request().method() === method), click()])
    assert.ok(response.ok(), `${method} ${path}: ${response.status()} ${await response.text()}`)
    const text = await response.text()
    return text ? JSON.parse(text) : undefined
  }
  const modal = () => page.locator('.arco-modal:visible, .arco-drawer:visible').last()
  const field = (container, key) => {
    const text = label(key)
    assert.equal(typeof text, 'string', `Unknown form label ${key}`)
    const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    return container.locator('.arco-form-item').filter({ has: page.locator('.arco-form-item-label').filter({ hasText: new RegExp(`^\\s*\\*?\\s*${escaped}\\s*\\*?\\s*$`) }) }).first()
  }
  async function fill(container, key, value) { await field(container, key).locator('input, textarea').first().fill(String(value)) }
  const button = (container, key) => container.getByRole('button', { name: label(key), exact: true })
  const modalSave = (method, path) => submit(method, path, () => modal().locator('.arco-btn-primary').last().click())
  async function confirm(method, path) {
    return submit(method, path, () => page.getByRole('button', { name: /^ok$/i }).last().click())
  }
  const row = (text) => page.locator('.admin-content tr').filter({ hasText: text }).first()
  async function close() {
    await writeFile(join(output, 'results.json'), JSON.stringify(results, null, 2))
    await browser.close()
    const failures = results.filter((result) => !result.passed)
    console.log(`${results.length - failures.length}/${results.length} form checks passed`)
    if (failures.length) process.exitCode = 1
  }
  return { page, context, check, open, request, submit, modal, fill, field, button, modalSave, confirm, row, label, close }
}
