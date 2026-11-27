import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import ts from 'typescript'
import { chromium, expect } from '@playwright/test'

const base = process.env.SPARKLE_ADMIN_BROWSER_URL ?? 'http://127.0.0.1:17799'
const address = new URL(base)
assert.ok(['127.0.0.1', 'localhost'].includes(address.hostname), 'Browser writes are restricted to the localhost test fixture')
const frontend = fileURLToPath(new URL('../', import.meta.url))
const output = process.env.SPARKLE_ADMIN_BROWSER_OUTPUT ?? join(frontend, '../build/reports/admin-browser')
await mkdir(output, { recursive: true })
async function importTs(path) {
  const { outputText } = ts.transpileModule(await readFile(path, 'utf8'), { compilerOptions: { module: ts.ModuleKind.ESNext } })
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
}
const { adminPages, adminCategories, adminLegacyRedirects } = await importTs(join(frontend, 'src/router/adminNavigation.ts'))
const { default: messages } = await importTs(join(frontend, 'src/i18n/locales/en-US.ts'))
const label = (key) => key.split('.').reduce((value, part) => value?.[part], messages)
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'en-US' })
await context.addInitScript(() => localStorage.setItem('sparkle-locale', 'en-US'))
await context.route('**/*', (route) => new URL(route.request().url()).origin === address.origin ? route.continue() : route.abort())
const page = await context.newPage()
const results = []
let errors = []
page.on('pageerror', (error) => errors.push(error.message))
page.on('response', (response) => {
  if (response.url().startsWith(base + '/api/admin/') && response.status() >= 400) errors.push(`${response.status()} ${new URL(response.url()).pathname}`)
})
async function check(name, action) {
  errors = []
  try { await action(); assert.deepEqual(errors, [], name); results.push({ name, passed: true }); console.log(`PASS ${name}`) }
  catch (error) { results.push({ name, passed: false, error: String(error.message) }); console.log(`FAIL ${name}: ${error.message}`); await page.screenshot({ path: join(output, `failure-${results.length}.png`), fullPage: true }).catch(() => {}) }
}
async function login(role) {
  const response = await context.request.get(`${base}/__admin-test/login?role=${role}`, { maxRedirects: 0 })
  assert.equal(response.status(), 302, 'The opt-in test login endpoint is required')
  assert.ok((await context.cookies()).some((cookie) => cookie.name === 'sparkle-admin-test-role'), 'Refusing writes without test fixture identity')
}
async function open(path) {
  const response = await page.goto(base + path)
  assert.equal(response.status(), 200, path)
  await expect(page.locator('.admin-header')).toBeVisible()
  await page.waitForLoadState('networkidle')
  await expect(page.locator('.admin-content')).not.toBeEmpty()
  await expect(page.locator('.arco-message-error')).toHaveCount(0)
}
async function csrf() {
  return decodeURIComponent((await context.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '')
}
async function put(path, body) {
  const response = await context.request.put(base + path, { data: body, headers: { 'X-XSRF-TOKEN': await csrf() } })
  assert.equal(response.status(), 200, `${path}: ${await response.text()}`)
  return response.json()
}
async function save(button, endpoint) {
  const [response] = await Promise.all([
    page.waitForResponse((response) => new URL(response.url()).pathname === endpoint && response.request().method() === 'PUT'),
    button.click(),
  ])
  assert.equal(response.status(), 200, `${endpoint}: ${await response.text()}`)
  await expect(page.locator('.arco-message-error')).toHaveCount(0)
  return response.json()
}
const saveButton = () => page.locator('.admin-content').getByRole('button', { name: label('admin.common.save'), exact: true })
try {
  await login('admin')
  for (const item of adminPages) await check(`admin page ${item.path}`, async () => {
    await open(item.path)
    await expect(page.locator('.admin-categories button[aria-current="page"]')).toHaveText(label(`admin.organization.categories.${item.category}`))
    await expect(page.locator('.admin-sidebar .arco-menu-selected')).toHaveText(label(item.label))
  })
  for (const redirect of adminLegacyRedirects) await check(`legacy ${redirect.path}`, async () => {
    await open(redirect.path)
    await expect(page).toHaveURL(base + redirect.to)
  })
  for (const category of adminCategories) await check(`navigation ${category.id}`, async () => {
    await page.locator('.admin-categories').getByRole('button', { name: label(category.label), exact: true }).click()
    await expect(page.locator('.admin-categories button[aria-current="page"]')).toHaveText(label(category.label))
    assert.equal(await page.locator('.admin-sidebar .arco-menu-item').count(), adminPages.filter((item) => item.category === category.id).length)
  })
  for (const item of adminPages) await check(`sidebar navigation ${item.path}`, async () => {
    const category = adminCategories.find((category) => category.id === item.category)
    await page.locator('.admin-categories').getByRole('button', { name: label(category.label), exact: true }).click()
    const peer = adminPages.find((peer) => peer.category === item.category && peer.path !== item.path)
    if (peer) await page.locator('.admin-sidebar .arco-menu-item').filter({ hasText: new RegExp(`^${label(peer.label).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`) }).click()
    await page.locator('.admin-sidebar .arco-menu-item').getByText(label(item.label), { exact: true }).click()
    await expect(page).toHaveURL(base + item.path)
    await page.waitForLoadState('networkidle')
  })
  await check('global admin search navigates between categories', async () => {
    await page.locator('.admin-search').click()
    await page.locator('.arco-select-dropdown:visible').getByText(`${label('admin.organization.categories.interface')} / ${label('admin.organization.pages.footer')}`, { exact: true }).click()
    await expect(page).toHaveURL(base + '/admin/footer')
  })
  for (const item of adminPages.filter((item) => item.view === 'AdminBtnConfigView')) await check(`scoped save ${item.props.group}`, async () => {
    await open(item.path)
    const data = await save(saveButton(), `/api/admin/btn/groups/${item.props.group}`)
    assert.ok(data.fields.length)
    const stored = await (await context.request.get(`${base}/api/admin/btn/groups/${item.props.group}`)).json()
    assert.deepEqual(stored.fields.map((field) => [field.key, field.value]), data.fields.map((field) => [field.key, field.value]))
  })
  for (const item of adminPages.filter((item) => item.view === 'AdminRuntimeSettingsView')) await check(`independent save ${item.props.section}`, async () => {
    await open(item.path)
    const toggle = page.locator('.admin-content .arco-switch').first()
    if (await toggle.count()) await toggle.click()
    else await page.locator('.admin-content input').first().fill('Admin test {{title}}')
    const saved = await save(saveButton(), `/api/admin/settings/${item.props.section}`)
    const stored = await (await context.request.get(`${base}/api/admin/settings/${item.props.section}`)).json()
    assert.deepEqual(stored, saved)
  })
  await check('mail channel save', async () => {
    await open('/admin/mail')
    const before = await (await context.request.get(base + '/api/admin/btn/groups/mail-identities')).json()
    await save(saveButton(), '/api/admin/mail/settings')
    const after = await (await context.request.get(base + '/api/admin/btn/groups/mail-identities')).json()
    assert.deepEqual(after.fields.map((field) => field.value), before.fields.map((field) => field.value))
  })
  await check('footer save', async () => { await open('/admin/footer'); await save(saveButton(), '/api/admin/footer-config') })
  await check('export settings save', async () => { await open('/admin/exports'); await save(saveButton(), '/api/admin/exports') })
  await check('word filter save', async () => {
    await open('/admin/content-moderation')
    await save(page.locator('.admin-content').getByRole('button', { name: label('admin.contentModeration.save'), exact: true }), '/api/admin/content-moderation')
  })
  await check('AI moderation settings save', async () => {
    await open('/admin/ai-moderation')
    await save(page.locator('.admin-content').getByRole('button', { name: label('admin.aiModeration.saveProvider'), exact: true }), '/api/admin/ai-moderation/settings')
  })
  await check('comment policy save', async () => {
    await open('/admin/comment-policy')
    await save(page.locator('.admin-content').getByRole('button', { name: label('admin.aiModeration.saveComments'), exact: true }), '/api/admin/ai-moderation/comments')
  })
  await check('module parameters save without schedule overwrite', async () => {
    await open('/admin/modules')
    const button = page.getByRole('button', { name: label('admin.modules.saveModule'), exact: true }).first()
    const [response] = await Promise.all([page.waitForResponse((response) => new URL(response.url()).pathname.startsWith('/api/admin/modules/') && response.request().method() === 'PUT'), button.click()])
    assert.equal(response.status(), 200, await response.text())
    const submitted = response.request().postDataJSON().values
    assert.ok(!('schedule' in submitted) && !('enabled' in submitted))
  })
  await check('invalid requests and cross-form writes rejected', async () => {
    const token = await csrf()
    const invalid = await context.request.put(base + '/api/admin/settings/statistics', { headers: { 'X-XSRF-TOKEN': token }, data: { enabled: true, refreshMinutes: 0, windowDays: 7 } })
    assert.equal(invalid.status(), 400)
    const cross = await context.request.put(base + '/api/admin/btn/groups/ranking', { headers: { 'X-XSRF-TOKEN': token }, data: { values: { 'sparkle.ping.config.min-protocol-version': '999' } } })
    assert.equal(cross.status(), 400)
    const noCsrf = await context.request.put(base + '/api/admin/settings/features', { data: { clientDiscoveryEnabled: false } })
    assert.equal(noCsrf.status(), 403)
  })
  for (const theme of ['light', 'dark']) for (const width of [390, 720, 980, 1440]) await check(`layout ${theme} ${width}`, async () => {
    await page.setViewportSize({ width, height: 1000 })
    await page.addInitScript((theme) => localStorage.setItem('sparkle:theme', theme), theme)
    await open('/admin/mail')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1)
    assert.equal(overflow, false, 'Page overflows horizontally')
    if (width <= 980) {
      await page.locator('.admin-menu-button').click()
      await expect(page.locator('.arco-drawer')).toBeVisible()
      await page.keyboard.press('Escape')
    }
    await page.screenshot({ path: join(output, `layout-${theme}-${width}.png`), fullPage: true })
  })
  await page.setViewportSize({ width: 1440, height: 1000 })
  for (const width of [390, 980]) {
    await page.setViewportSize({ width, height: 1000 })
    for (const item of adminPages) await check(`responsive page ${width} ${item.path}`, async () => {
      await open(item.path)
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, 'Page overflows horizontally')
    })
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await check('Chinese admin navigation and labels', async () => {
    await page.addInitScript(() => localStorage.setItem('sparkle-locale', 'zh-CN'))
    await open('/admin/footer')
    const { default: zh } = await importTs(join(frontend, 'src/i18n/locales/zh-CN.ts'))
    await expect(page.locator('.admin-heading strong')).toHaveText(zh.admin.title)
    await expect(page.locator('.admin-categories button[aria-current="page"]')).toHaveText(zh.admin.organization.categories.interface)
    await page.screenshot({ path: join(output, 'admin-navigation-zh.png') })
    await page.addInitScript(() => localStorage.setItem('sparkle-locale', 'en-US'))
  })
  await login('moderator')
  for (const item of adminPages.filter((item) => item.moderator)) await check(`moderator page ${item.path}`, async () => { await open(item.path); await expect(page).toHaveURL(base + item.path) })
  await check('moderator denied privileged pages and API', async () => {
    const response = await context.request.get(base + '/api/admin/settings/features')
    assert.equal(response.status(), 403)
    await open('/admin/rules?tab=geoip')
    await expect(page.getByText(label('admin.geoIpCityBlacklist.title'), { exact: true })).toHaveCount(0)
    const inaccessible = await context.request.get(base + '/api/admin/geoip-city-blacklist')
    assert.equal(inaccessible.status(), 403)
  })
  await login('user')
  await check('ordinary users cannot access admin pages or APIs', async () => {
    assert.equal((await context.request.get(base + '/api/admin/settings/features')).status(), 403)
    assert.equal((await context.request.get(base + '/api/admin/btn/groups/query')).status(), 403)
    await page.goto(base + '/admin/mail')
    await expect(page).toHaveURL(base + '/')
  })
  await login('anonymous')
  await check('anonymous requests require authentication', async () => {
    assert.equal((await context.request.get(base + '/api/admin/settings/features')).status(), 401)
    await page.goto(base + '/admin/mail')
    await expect(page).toHaveURL(base + '/login')
  })
} finally {
  await writeFile(join(output, 'results.json'), JSON.stringify(results, null, 2))
  await browser.close()
}
const failed = results.filter((result) => !result.passed)
console.log(`${results.length - failed.length}/${results.length} browser checks passed; report: ${output}`)
if (failed.length) process.exitCode = 1
