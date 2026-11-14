<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  type Component,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  IconApps,
  IconBug,
  IconDashboard,
  IconDown,
  IconEmail,
  IconExclamationCircle,
  IconFile,
  IconFire,
  IconHome,
  IconLink,
  IconList,
  IconMenu,
  IconSearch,
  IconCode,
  IconTrophy,
  IconUser,
} from '@arco-design/web-vue/es/icon'
import ThemeSwitcher from './ThemeSwitcher.vue'
import LanguageSwitcher from './LanguageSwitcher.vue'
import AppFooter from './AppFooter.vue'
import { useSessionStore } from '@/stores/session'
import { uiApi } from '@/api'
import { useFormatters } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { t, locale } = useI18n()
const { formatBytes, formatDate } = useFormatters()
const mobileMenuOpen = ref(false)
const unreadMessages = ref(0)
let unreadTimer: number | undefined

const navigationIcons: Record<string, Component> = {
  home: IconHome,
  apps: IconApps,
  ban: IconBug,
  search: IconSearch,
  swarm: IconList,
  rules: IconCode,
  ranking: IconTrophy,
  link: IconLink,
  file: IconFile,
  dashboard: IconDashboard,
}

const navItems = computed(() => [
  {
    id: 'builtin-ip-query',
    href: '/ip-query',
    openInNewTab: false,
    label: t('nav.ipQuery'),
    icon: IconSearch,
  },
  ...session.navigation.map((item) => ({
    id: item.id,
    href: item.href,
    openInNewTab: item.openInNewTab,
    label:
      locale.value.startsWith('en')
        ? item.labelEn || item.labelZh
        : item.labelZh || item.labelEn,
    icon: navigationIcons[item.icon] ?? IconLink,
  })),
].filter(
  (item, index, items) =>
    items.findIndex((candidate) => candidate.href === item.href) === index,
))

const selectedKey = computed(() => {
  return (
    navItems.value.find(
      (item) =>
        item.href.startsWith('/') &&
        (route.path === item.href || route.path.startsWith(`${item.href}/`)),
    )?.id ?? ''
  )
})

const trafficIsStale = computed(() => {
  const updatedAt = session.traffic?.lastUpdateAt
  if (!updatedAt) return true
  const timestamp = new Date(updatedAt).getTime()
  const staleAfterMinutes = Math.max(120, (session.traffic?.refreshIntervalMinutes ?? 60) * 2)
  return Number.isNaN(timestamp) || Date.now() - timestamp > staleAfterMinutes * 60 * 1000
})

function navigate(id: string) {
  mobileMenuOpen.value = false
  const item = navItems.value.find((candidate) => candidate.id === id)
  if (!item) {
    router.push(id)
    return
  }
  const internal = item.href.startsWith('/')
  if (item.openInNewTab) {
    window.open(item.href, '_blank', 'noopener,noreferrer')
    return
  }
  if (internal) {
    router.push(item.href)
  } else {
    window.location.assign(item.href)
  }
}

async function refreshUnread() {
  try {
    unreadMessages.value = (await uiApi.unreadMessageCount()).count
  } catch {
    // The next poll or inbox visit retries without blocking navigation.
  }
}

watch(
  () => route.fullPath,
  () => {
    refreshUnread()
  },
)

onMounted(() => {
  refreshUnread()
  window.addEventListener('sparkle:messages-changed', refreshUnread)
  unreadTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      refreshUnread()
    }
  }, 60_000)
})

onBeforeUnmount(() => {
  window.clearInterval(unreadTimer)
  window.removeEventListener('sparkle:messages-changed', refreshUnread)
})
</script>

<template>
  <a-layout class="app-layout">
    <a-layout-header class="app-header">
      <div class="app-header-inner">
        <button class="brand-button" type="button" @click="navigate('/')">
          <span class="brand-mark"><IconFire /></span>
          <span class="brand-copy">
            <strong>{{ t('app.shortName') }}</strong>
            <small>Threat Intelligence Network</small>
          </span>
        </button>

        <nav class="desktop-navigation">
          <a-menu
            mode="horizontal"
            :selected-keys="[selectedKey]"
            :ellipsis="true"
            @menu-item-click="navigate"
          >
            <a-menu-item v-for="item in navItems" :key="item.id">
              <template #icon><component :is="item.icon" /></template>
              {{ item.label }}
            </a-menu-item>
          </a-menu>
        </nav>

        <div class="header-actions">
          <ThemeSwitcher />
          <LanguageSwitcher />
          <a-badge :count="unreadMessages" :max-count="99" :offset="[-4, 4]">
            <a-button
              class="inbox-trigger"
              shape="circle"
              type="text"
              :aria-label="t('inbox.open')"
              @click="navigate('/user/inbox')"
            >
              <template #icon><IconEmail /></template>
            </a-button>
          </a-badge>
          <a-dropdown trigger="click">
            <button class="user-trigger" type="button">
              <a-avatar :size="32" :image-url="session.user?.avatarUrl">
                <IconUser />
              </a-avatar>
              <span class="user-name">{{ session.user?.nickname }}</span>
              <IconDown class="user-chevron" />
            </button>
            <template #content>
              <a-doption @click="navigate('/user/profile')">
                <template #icon><IconUser /></template>
                {{ t('nav.profile') }}
              </a-doption>
              <a-doption @click="navigate('/user/points')">
                <template #icon><IconFire /></template>
                {{ t('nav.points') }}
              </a-doption>
              <a-doption @click="navigate('/user/submissions')">
                <template #icon><IconList /></template>
                {{ t('nav.submissions') }}
              </a-doption>
              <a-doption
                v-if="session.user?.admin || session.user?.moderator"
                @click="navigate(session.user?.admin ? '/admin/dashboard' : '/admin/tasks')"
              >
                <template #icon><IconDashboard /></template>
                {{ t('admin.title') }}
              </a-doption>
            </template>
          </a-dropdown>
          <a-button class="mobile-menu-button" type="text" @click="mobileMenuOpen = true">
            <template #icon><IconMenu /></template>
          </a-button>
        </div>
      </div>
    </a-layout-header>

    <div v-if="session.traffic" class="traffic-strip">
      <div class="traffic-strip-inner">
        <span class="traffic-strip-title">
          {{ t('home.trafficWindow', { days: session.traffic.windowDays }) }}
        </span>
        <span class="traffic-item">
          <span class="traffic-label">{{ t('ranking.sentSelf') }}</span>
          <strong>{{ formatBytes(session.traffic.sentTrafficSelfReport) }}</strong>
        </span>
        <span class="traffic-item">
          <span class="traffic-label">{{ t('ranking.receivedSelf') }}</span>
          <strong>{{ formatBytes(session.traffic.receivedTrafficSelfReport) }}</strong>
        </span>
        <span class="traffic-item">
          <span class="traffic-label">{{ t('ranking.sentAck') }}</span>
          <strong>{{ formatBytes(session.traffic.sentTrafficOtherAck) }}</strong>
        </span>
        <span class="traffic-item">
          <span class="traffic-label">{{ t('ranking.receivedAck') }}</span>
          <strong>{{ formatBytes(session.traffic.receivedTrafficOtherAck) }}</strong>
        </span>
        <span class="traffic-updated" :class="{ 'traffic-updated--stale': trafficIsStale }">
          <a-tooltip v-if="trafficIsStale" :content="t('home.trafficStale')">
            <IconExclamationCircle />
          </a-tooltip>
          {{
            session.traffic.lastUpdateAt
              ? t('home.trafficUpdatedAt', { time: formatDate(session.traffic.lastUpdateAt) })
              : t('home.trafficNeverUpdated')
          }}
        </span>
      </div>
    </div>

    <a-layout-content class="app-content">
      <main class="page-container">
        <slot />
      </main>
    </a-layout-content>

    <AppFooter />

    <a-drawer
      v-model:visible="mobileMenuOpen"
      placement="right"
      :width="280"
      :footer="false"
      unmount-on-close
    >
      <template #title>
        <span class="brand-mark brand-mark--small"><IconFire /></span>
        {{ t('app.shortName') }}
      </template>
      <a-menu mode="vertical" :selected-keys="[selectedKey]" @menu-item-click="navigate">
        <a-menu-item v-for="item in navItems" :key="item.id">
          <template #icon><component :is="item.icon" /></template>
          {{ item.label }}
        </a-menu-item>
        <a-menu-item key="/user/profile">
          <template #icon><IconUser /></template>
          {{ t('nav.profile') }}
        </a-menu-item>
        <a-menu-item key="/user/points">
          <template #icon><IconFire /></template>
          {{ t('nav.points') }}
        </a-menu-item>
        <a-menu-item key="/user/submissions">
          <template #icon><IconList /></template>
          {{ t('nav.submissions') }}
        </a-menu-item>
        <a-menu-item key="/user/inbox">
          <template #icon><IconEmail /></template>
          {{ t('inbox.open') }}
        </a-menu-item>
      </a-menu>
    </a-drawer>
  </a-layout>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 64px;
  padding: 0;
  background: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border-2);
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%);
}

.app-header-inner,
.traffic-strip-inner,
.page-container {
  width: min(1440px, calc(100% - 40px));
  margin: 0 auto;
}

.app-header-inner {
  display: flex;
  align-items: center;
  height: 100%;
  gap: 16px;
}

.brand-button {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  padding: 0;
  color: var(--color-text-1);
  text-align: left;
  background: none;
  border: 0;
  cursor: pointer;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: white;
  font-size: 19px;
  background: rgb(var(--primary-6));
  border-radius: 10px;
}

.brand-mark--small {
  width: 28px;
  height: 28px;
  margin-right: 8px;
  font-size: 15px;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-copy strong {
  font-size: 15px;
}

.brand-copy small {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.desktop-navigation {
  flex: 1 1 auto;
  min-width: 0;
}

.desktop-navigation :deep(.arco-menu-horizontal) {
  background: transparent;
  border-bottom: 0;
}

.desktop-navigation :deep(.arco-menu-inner) {
  padding: 14px 0;
}

.desktop-navigation :deep(.arco-menu-item) {
  padding: 0 8px;
}

.desktop-navigation :deep(.arco-menu-item .arco-menu-icon) {
  margin-right: 6px;
}

.header-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 4px 8px;
  color: var(--color-text-1);
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.inbox-trigger {
  color: var(--color-text-2);
  font-size: 18px;
}

.user-trigger:hover {
  background: var(--color-fill-2);
}

.user-name {
  max-width: 108px;
  overflow: hidden;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-chevron {
  color: var(--color-text-3);
  font-size: 12px;
}

.mobile-menu-button {
  display: none;
}

.traffic-strip {
  background: var(--color-primary-light-1);
  border-bottom: 1px solid var(--color-border-2);
}

.traffic-strip-inner {
  display: flex;
  align-items: center;
  gap: 32px;
  min-height: 38px;
  overflow-x: auto;
  color: var(--color-text-2);
  font-size: 12px;
  white-space: nowrap;
}

.traffic-strip-title {
  color: rgb(var(--primary-6));
  font-weight: 600;
}

.traffic-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.traffic-label {
  color: var(--color-text-3);
}

.traffic-updated {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  color: var(--color-text-3);
}

.traffic-updated--stale {
  color: rgb(var(--orange-6));
}

.app-content {
  background: var(--color-fill-2);
}

.page-container {
  min-height: calc(100vh - 190px);
  padding: 24px 0 48px;
}

@media (max-width: 1180px) {
  .desktop-navigation {
    display: none;
  }

  .header-actions {
    margin-left: auto;
  }

  .mobile-menu-button {
    display: inline-flex;
  }
}

@media (max-width: 720px) {
  .app-header-inner,
  .traffic-strip-inner,
  .page-container {
    width: min(100% - 24px, 1440px);
  }

  .brand-copy small,
  .user-name,
  .user-chevron {
    display: none;
  }

  .traffic-strip-inner {
    gap: 20px;
  }

}
@media (max-width: 480px) {
  .brand-copy { display: none; }
  .app-header-inner { gap: 8px; }
  .header-actions { gap: 4px; }
  .user-trigger { padding: 4px; }
}
</style>
