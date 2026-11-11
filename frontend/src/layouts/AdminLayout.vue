<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  IconApps,
  IconArrowLeft,
  IconCode,
  IconDashboard,
  IconEmail,
  IconFile,
  IconLocation,
  IconMenu,
  IconNotification,
  IconRefresh,
  IconSchedule,
  IconSettings,
  IconStorage,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const items = computed(() => [
  { key: '/admin/dashboard', label: t('admin.nav.dashboard'), icon: IconDashboard },
  { key: '/admin/tasks', label: t('admin.nav.tasks'), icon: IconSchedule },
  {
    key: '/admin/homepage-statistics',
    label: t('admin.nav.homepageStatistics'),
    icon: IconRefresh,
  },
  { key: '/admin/users', label: t('admin.nav.users'), icon: IconUserGroup },
  { key: '/admin/userapps', label: t('admin.nav.userApps'), icon: IconApps },
  { key: '/admin/rules', label: t('admin.nav.rules'), icon: IconCode },
  {
    key: '/admin/geoip-city-blacklist',
    label: t('admin.nav.geoIpCityBlacklist'),
    icon: IconLocation,
  },
  { key: '/admin/modules', label: t('admin.nav.modules'), icon: IconCode },
  {
    key: '/admin/points-modules',
    label: t('admin.nav.pointsModules'),
    icon: IconDashboard,
  },
  { key: '/admin/storage', label: t('admin.nav.storage'), icon: IconStorage },
  { key: '/admin/version-alerts', label: t('admin.nav.versionAlerts'), icon: IconNotification },
  {
    key: '/admin/user-contact-emails',
    label: t('admin.nav.userContactEmails'),
    icon: IconEmail,
  },
  { key: '/admin/announcements', label: t('admin.nav.announcements'), icon: IconNotification },
  { key: '/admin/navigation', label: t('admin.nav.navigation'), icon: IconMenu },
  { key: '/admin/database-config', label: t('admin.nav.databaseConfig'), icon: IconFile },
  { key: '/admin/settings', label: t('admin.nav.settings'), icon: IconSettings },
])

const selectedKey = computed(
  () => items.value.find((item) => route.path.startsWith(item.key))?.key ?? '/admin/dashboard',
)

function navigate(key: string) {
  router.push(key)
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="admin-title">
        <span class="admin-title-icon"><IconDashboard /></span>
        <div>
          <strong>{{ t('admin.title') }}</strong>
          <small>{{ t('admin.subtitle') }}</small>
        </div>
      </div>
      <a-menu mode="vertical" :selected-keys="[selectedKey]" @menu-item-click="navigate">
        <a-menu-item v-for="item in items" :key="item.key">
          <template #icon><component :is="item.icon" /></template>
          {{ item.label }}
        </a-menu-item>
      </a-menu>
      <a-button class="back-button" type="text" @click="router.push('/')">
        <template #icon><IconArrowLeft /></template>
        {{ t('admin.backToSite') }}
      </a-button>
    </aside>

    <div class="admin-mobile-nav">
      <a-menu mode="horizontal" :selected-keys="[selectedKey]" @menu-item-click="navigate">
        <a-menu-item v-for="item in items" :key="item.key">
          <template #icon><component :is="item.icon" /></template>
          {{ item.label }}
        </a-menu-item>
      </a-menu>
    </div>

    <section class="admin-content">
      <router-view />
    </section>
  </div>
</template>

<style scoped>
.admin-shell {
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.admin-sidebar {
  position: sticky;
  top: 88px;
  padding: 16px 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.admin-title {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px 18px;
}

.admin-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: var(--color-white);
  font-size: 18px;
  background: rgb(var(--primary-6));
  border-radius: 8px;
}

.admin-title div {
  display: flex;
  flex-direction: column;
}

.admin-title strong {
  font-size: 15px;
}

.admin-title small {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 11px;
}

.admin-sidebar :deep(.arco-menu) {
  border-radius: 6px;
}

.back-button {
  width: 100%;
  margin-top: 16px;
  color: var(--color-text-3);
}

.admin-mobile-nav {
  display: none;
}

.admin-content {
  min-width: 0;
}

@media (max-width: 980px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    display: none;
  }

  .admin-mobile-nav {
    display: block;
    overflow: hidden;
    background: var(--color-bg-2);
    border: 1px solid var(--color-border-2);
    border-radius: 8px;
  }
}
</style>
