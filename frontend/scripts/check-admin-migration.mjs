import assert from 'node:assert/strict'
import { readFile, readdir, access } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import ts from 'typescript'
import { createI18n } from 'vue-i18n'

const frontend = fileURLToPath(new URL('../', import.meta.url))
async function importTs(path) {
  const { outputText } = ts.transpileModule(await readFile(path, 'utf8'), { compilerOptions: { module: ts.ModuleKind.ESNext } })
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
}
const { adminPages, adminCategories, adminLegacyRedirects, moderatorRouteNames } = await importTs(join(frontend, 'src/router/adminNavigation.ts'))
for (const property of ['id', 'path', 'name']) assert.equal(new Set(adminPages.map((page) => page[property])).size, adminPages.length, `Duplicate admin ${property}`)
assert.equal(new Set(adminCategories.map((category) => category.id)).size, adminCategories.length)
const routes = await readFile(join(frontend, 'src/router/adminRoutes.ts'), 'utf8')
const spa = await readFile(join(frontend, '../src/main/java/com/ghostchu/btn/sparkle/controller/ui/SpaController.java'), 'utf8')
for (const category of adminCategories) assert.ok(adminPages.some((page) => page.category === category.id), `Empty category ${category.id}`)
for (const page of adminPages) {
  assert.ok(adminCategories.some((category) => category.id === page.category), `Unknown category ${page.id}`)
  assert.ok(page.path.startsWith('/admin/'), `Non-admin path ${page.path}`)
  assert.ok(routes.includes(`${page.view}: () => import('@/views/admin/${page.view}.vue')`), `Missing lazy loader ${page.view}`)
  await access(join(frontend, `src/views/admin/${page.view}.vue`))
  assert.ok(spa.includes(`"${page.path}"`), `Unregistered SPA path ${page.path}`)
  assert.equal(moderatorRouteNames.has(page.name), page.moderator)
}
for (const redirect of adminLegacyRedirects) {
  assert.ok(spa.includes(`"${redirect.path}"`), `Unregistered compatibility path ${redirect.path}`)
  assert.ok(adminPages.some((page) => page.path === redirect.to.split('?')[0]), `Broken redirect ${redirect.path}`)
}
const { adminConfigEditors } = await importTs(join(frontend, 'src/router/adminConfigEditors.ts'))
for (const [key, path] of Object.entries(adminConfigEditors)) assert.ok(adminPages.some((page) => page.path === path), `Unregistered config editor ${key}`)
for (const key of ['sparkle.ai.moderation.worker-delay-ms', 'sparkle.github-sync.targets-initialized', 'sparkle.homepage.future-setting']) assert.equal(adminConfigEditors[key], undefined, `Unexposed field must retain its advanced editor: ${key}`)
const legacyPaths = ['dashboard', 'tasks', 'ban-submissions', 'homepage-statistics', 'announcements', 'messages', 'community-groups', 'memberships', 'navigation', 'settings', 'version-alerts', 'users', 'points-modules', 'userapps', 'github-sync', 'flarum-integration', 'content-moderation', 'ai-moderation', 'ip-query-comments', 'btn', 'modules', 'rules', 'exports', 'database-config', 'user-contact-emails', 'storage', 'afdian', 'geoip-city-blacklist']
for (const path of legacyPaths) assert.ok([...adminPages, ...adminLegacyRedirects].some((entry) => entry.path === `/admin/${path}`), `Lost legacy entry ${path}`)
const expectedGroups = ['communication', 'reporting', 'rule-delivery', 'reporting-risk', 'query', 'ranking', 'ip-warning', 'measure', 'security', 'auto-accounts', 'retention', 'maintenance', 'points-timezone', 'mail-identities', 'cache']
assert.deepEqual(adminPages.filter((page) => page.view === 'AdminBtnConfigView').map((page) => page.props.group).sort(), expectedGroups.sort(), 'Lost or duplicated BTN editor')
const scopes = {
  AdminMembershipsView: ['transactions', 'licenses', 'reconciliation', 'policy', 'providers'],
  AdminAiModerationView: ['provider', 'comments', 'queue'],
  AdminCommunityGroupsView: ['groups', 'integration'],
  AdminRuntimeSettingsView: ['features', 'statistics', 'homepage-display', 'contact-template'],
}
for (const [view, expected] of Object.entries(scopes)) assert.deepEqual(adminPages.filter((page) => page.view === view).map((page) => page.props.scope ?? page.props.section).sort(), expected.sort(), `Incomplete ${view}`)
for (const locale of ['zh-CN', 'en-US']) {
  const { default: messages } = await importTs(join(frontend, `src/i18n/locales/${locale}.ts`))
  const i18n = createI18n({ legacy: false, locale, messages: { [locale]: messages } })
  for (const entry of [...adminPages, ...adminCategories]) assert.ok(i18n.global.te(entry.label), `${locale}: ${entry.label}`)
}
async function vueSources(directory) {
  const files = await readdir(directory, { withFileTypes: true })
  return (await Promise.all(files.map(async (file) => file.isDirectory() ? vueSources(join(directory, file.name)) : file.name.endsWith('.vue') ? [await readFile(join(directory, file.name), 'utf8')] : []))).flat()
}
const sources = (await Promise.all([vueSources(join(frontend, 'src/views/admin')), vueSources(join(frontend, 'src/components'))])).flat().join('\n')
const actions = new Set([...sources.matchAll(/adminApi\.(\w+)/g)].map((match) => match[1]))
const replacements = { config: 'settingsSection', updateConfig: 'updateSettingsSection', btnConfig: 'btnConfigGroup', updateBtnConfig: 'updateBtnConfigGroup', runAnalyseModule: 'runTask' }
const legacy = JSON.parse(await readFile(join(frontend, 'scripts/fixtures/admin-legacy-actions.json'), 'utf8'))
for (const [view, methods] of Object.entries(legacy)) for (const method of methods) assert.ok(actions.has(replacements[method] ?? method), `Lost business action ${view}.${method}`)
const renamedControls = {
  swarmStatisticsEnabled: 'enabled', swarmStatisticsRefreshMinutes: 'refreshMinutes', swarmStatisticsWindowDays: 'windowDays',
  userContactEmailBodyTemplate: 'body', userContactEmailSubjectTemplate: 'subject',
}
const controls = JSON.parse(await readFile(join(frontend, 'scripts/fixtures/admin-legacy-controls.json'), 'utf8'))
for (const [view, bindings] of Object.entries(controls)) for (const binding of bindings) {
  const property = binding.split('.').at(-1)
  assert.ok(sources.includes(renamedControls[property] ?? property), `Lost form control ${view}.${binding}`)
}
console.log(`Verified ${adminPages.length} admin pages, ${adminCategories.length} categories, ${legacyPaths.length} legacy URLs, 15 BTN editors and all legacy business actions and form controls`)
