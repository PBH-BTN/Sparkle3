<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSessionStore } from '@/stores/session'
import { adminPages } from '@/router/adminNavigation'

const props = defineProps<{ paths: string[] }>()
const { t } = useI18n()
const session = useSessionStore()
const route = useRoute()
const links = computed(() => props.paths.flatMap((path) => {
  const page = adminPages.find((item) => item.path === path.split('?')[0])
  return page && path !== route.fullPath && (session.user?.admin || page.moderator) ? [{ path, label: page.label }] : []
}))
</script>

<template>
  <div v-if="links.length" class="admin-related surface-card">
    <span>{{ t('admin.organization.related') }}</span>
    <router-link v-for="link in links" :key="link.path" :to="link.path">{{ t(link.label) }}</router-link>
  </div>
</template>

<style scoped>
.admin-related { display: flex; flex-wrap: wrap; align-items: center; gap: 12px 20px; padding: 14px 18px; }
.admin-related > span { color: var(--color-text-3); font-size: 12px; }
.admin-related a { color: rgb(var(--primary-6)); text-decoration: none; }
.admin-related a:hover { text-decoration: underline; }
</style>
