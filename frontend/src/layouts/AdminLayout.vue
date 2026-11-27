<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { IconArrowLeft, IconMenu, IconSearch } from '@arco-design/web-vue/es/icon'
import { useSessionStore } from '@/stores/session'
import { adminCategories, adminPages } from '@/router/adminNavigation'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { t } = useI18n()
const drawerVisible = ref(false)
const categoryNav = ref<HTMLElement>()
const sidebar = ref<HTMLElement>()
const searchSelection = ref<string>()
const pages = computed(() => adminPages.filter((page) => session.user?.admin || page.moderator))
const categories = computed(() => adminCategories.filter((category) => pages.value.some((page) => page.category === category.id)))
const currentPage = computed(() => pages.value.find((page) => page.path === route.path))
const currentCategory = computed(() => categories.value.find((category) => category.id === currentPage.value?.category))
const items = computed(() => pages.value.filter((page) => page.category === currentCategory.value?.id))
const searchOptions = computed(() => pages.value.map((page) => ({
  value: page.path,
  label: `${t(`admin.organization.categories.${page.category}`)} / ${t(page.label)}`,
})))

function navigate(path: string) {
  drawerVisible.value = false
  searchSelection.value = undefined
  void router.push(path)
}

function selectCategory(category: string) {
  const available = pages.value.filter((page) => page.category === category)
  let remembered: string | null = null
  try { remembered = localStorage.getItem(`sparkle:admin:category:${category}`) } catch { /* Storage may be disabled. */ }
  const destination = available.find((page) => page.path === remembered) ?? available[0]
  if (destination) navigate(destination.path)
}

function syncSidebar() {
  const selected = sidebar.value?.querySelector('.arco-menu-selected')
  if (sidebar.value && selected?.getClientRects().length) {
    const top = Math.max(88, sidebar.value.getBoundingClientRect().top)
    sidebar.value.style.maxHeight = `${Math.max(160, window.innerHeight - top - 20)}px`
    const panel = sidebar.value.getBoundingClientRect()
    const item = selected.getBoundingClientRect()
    if (item.top < panel.top) sidebar.value.scrollTop -= panel.top - item.top
    else if (item.bottom > panel.bottom) sidebar.value.scrollTop += item.bottom - panel.bottom
  }
}

let sidebarObserver: ResizeObserver | undefined
let sidebarFrame = 0
function scheduleSidebar() {
  cancelAnimationFrame(sidebarFrame)
  sidebarFrame = requestAnimationFrame(syncSidebar)
}
onMounted(() => {
  sidebarObserver = new ResizeObserver(scheduleSidebar)
  sidebarObserver.observe(document.body)
  if (sidebar.value) sidebarObserver.observe(sidebar.value)
  window.addEventListener('resize', scheduleSidebar)
  syncSidebar()
})
onBeforeUnmount(() => {
  sidebarObserver?.disconnect()
  cancelAnimationFrame(sidebarFrame)
  window.removeEventListener('resize', scheduleSidebar)
})

watch(currentPage, async (page) => {
  if (!page) return
  await nextTick()
  categoryNav.value?.querySelector('[aria-current="page"]')?.scrollIntoView({ inline: 'nearest', block: 'nearest' })
  syncSidebar()
  try { localStorage.setItem(`sparkle:admin:category:${page.category}`, page.path) } catch { /* Navigation does not require storage. */ }
}, { immediate: true })
</script>

<template>
  <div class="admin-shell">
    <header class="admin-header surface-card">
      <div class="admin-heading">
        <strong>{{ t('admin.title') }}</strong>
        <a-button type="text" @click="navigate('/')">
          <template #icon><IconArrowLeft /></template>
          {{ t('admin.backToSite') }}
        </a-button>
      </div>
      <nav ref="categoryNav" class="admin-categories" :aria-label="t('admin.organization.menu')">
        <button v-for="category in categories" :key="category.id" type="button"
          :class="{ active: category.id === currentCategory?.id }"
          :aria-current="category.id === currentCategory?.id ? 'page' : undefined"
          @click="selectCategory(category.id)">
          {{ t(category.label) }}
        </button>
      </nav>
    </header>

    <div class="admin-toolbar">
      <a-space>
        <a-button class="admin-menu-button" :aria-label="t('admin.organization.menu')" @click="drawerVisible = true">
          <template #icon><IconMenu /></template>
        </a-button>
        <a-breadcrumb>
          <a-breadcrumb-item>{{ t('admin.title') }}</a-breadcrumb-item>
          <a-breadcrumb-item v-if="currentCategory">{{ t(currentCategory.label) }}</a-breadcrumb-item>
          <a-breadcrumb-item v-if="currentPage">{{ t(currentPage.label) }}</a-breadcrumb-item>
        </a-breadcrumb>
      </a-space>
      <div class="admin-search"><a-select v-model="searchSelection" allow-search allow-clear
        :options="searchOptions" :placeholder="t('admin.organization.search')"
        :aria-label="t('admin.organization.search')" @change="(value) => value && navigate(String(value))">
        <template #prefix><IconSearch /></template>
      </a-select></div>
    </div>

    <div class="admin-body">
      <aside ref="sidebar" class="admin-sidebar surface-card">
        <strong class="admin-category-title">{{ currentCategory ? t(currentCategory.label) : t('admin.title') }}</strong>
        <a-menu :selected-keys="[route.path]" @menu-item-click="navigate">
          <a-menu-item v-for="item in items" :key="item.path">{{ t(item.label) }}</a-menu-item>
        </a-menu>
      </aside>
      <section class="admin-content">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.path" />
        </router-view>
      </section>
    </div>

    <a-drawer v-model:visible="drawerVisible" placement="left" :width="280" :footer="false"
      :title="t('admin.organization.menu')">
      <a-select :model-value="currentCategory?.id" :options="categories.map((category) => ({ value: category.id, label: t(category.label) }))"
        :aria-label="t('admin.organization.menu')" @change="(value) => selectCategory(String(value))" />
      <a-menu :selected-keys="[route.path]" @menu-item-click="navigate">
        <a-menu-item v-for="item in items" :key="item.path">{{ t(item.label) }}</a-menu-item>
      </a-menu>
    </a-drawer>
  </div>
</template>

<style scoped>
.admin-shell, .admin-content { width: 100%; min-width: 0; }
.admin-shell { display: flex; flex-direction: column; gap: 16px; }
.admin-header { padding: 12px 16px 0; }
.admin-heading, .admin-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.admin-heading strong { font-size: 17px; }
.admin-categories { display: flex; gap: 6px; margin-top: 10px; padding-bottom: 8px; overflow-x: auto; }
.admin-categories button { flex-shrink: 0; padding: 8px 15px; border: 0; border-radius: var(--app-radius); color: var(--color-text-2); background: transparent; font: inherit; cursor: pointer; }
.admin-categories button:hover { background: var(--color-fill-2); }
.admin-categories button.active { color: rgb(var(--primary-6)); background: var(--color-primary-light-1); font-weight: 600; }
.admin-categories button:focus-visible { outline: 2px solid rgb(var(--primary-6)); outline-offset: -2px; }
.admin-search { width: 280px; flex: 0 0 280px; }
.admin-toolbar > :first-child { min-width: 0; }
.admin-body { display: grid; grid-template-columns: 208px minmax(0, 1fr); gap: 20px; align-items: start; }
.admin-sidebar { position: sticky; top: 88px; max-height: calc(100dvh - 108px); padding: 12px 8px; overflow-y: auto; }
.admin-category-title { display: block; padding: 8px 12px 14px; font-size: 15px; }
.admin-sidebar :deep(.arco-menu-inner) { padding: 0; }
.admin-sidebar :deep(.arco-menu-item) { height: auto; min-height: 40px; padding-block: 10px; line-height: 1.5; white-space: normal; }
.admin-sidebar :deep(.arco-menu-item-inner) { white-space: normal; overflow: visible; text-overflow: clip; }
.admin-menu-button { display: none; }
@media (max-width: 980px) {
  .admin-body { grid-template-columns: 1fr; }
  .admin-sidebar { display: none; }
  .admin-menu-button { display: inline-flex; }
}
@media (max-width: 720px) {
  .admin-toolbar { flex-wrap: wrap; }
  .admin-search { width: 100%; flex-basis: 100%; }
  .admin-categories button { padding: 8px 12px; }
  .admin-toolbar :deep(.arco-breadcrumb) { font-size: 12px; }
}
</style>
