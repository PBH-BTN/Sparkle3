<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  IconArrowLeft,
  IconLock,
  IconSafe,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const items = computed(() => [
  {
    key: '/user/settings/account',
    label: t('settings.menu.account'),
    icon: IconSafe,
  },
  {
    key: '/user/settings/privacy',
    label: t('settings.menu.privacy'),
    icon: IconLock,
  },
])

const selectedKey = computed(
  () => items.value.find((item) => route.path.startsWith(item.key))?.key ?? items.value[0]?.key,
)

function navigate(key: string) {
  router.push(key)
}
</script>

<template>
  <div class="settings-page">
    <PageHeader :title="t('settings.title')" :description="t('settings.description')">
      <template #actions>
        <a-button @click="router.push('/user/profile')">
          <template #icon><IconArrowLeft /></template>
          {{ t('settings.backToProfile') }}
        </a-button>
      </template>
    </PageHeader>

    <div class="settings-shell">
      <aside class="settings-sidebar">
        <a-menu
          mode="vertical"
          :selected-keys="selectedKey ? [selectedKey] : []"
          @menu-item-click="navigate"
        >
          <a-menu-item v-for="item in items" :key="item.key">
            <template #icon><component :is="item.icon" /></template>
            {{ item.label }}
          </a-menu-item>
        </a-menu>
      </aside>

      <div class="settings-mobile-nav">
        <a-menu
          mode="horizontal"
          :selected-keys="selectedKey ? [selectedKey] : []"
          @menu-item-click="navigate"
        >
          <a-menu-item v-for="item in items" :key="item.key">
            <template #icon><component :is="item.icon" /></template>
            {{ item.label }}
          </a-menu-item>
        </a-menu>
      </div>

      <section class="settings-content">
        <router-view />
      </section>
    </div>
  </div>
</template>

<style scoped>
.settings-page {
  width: min(1120px, 100%);
  margin: 0 auto;
}

.settings-shell {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.settings-sidebar {
  position: sticky;
  top: 88px;
  padding: 10px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.settings-sidebar :deep(.arco-menu) {
  border-radius: 6px;
}

.settings-mobile-nav {
  display: none;
}

.settings-content {
  min-width: 0;
}

@media (max-width: 820px) {
  .settings-shell {
    grid-template-columns: 1fr;
  }

  .settings-sidebar {
    display: none;
  }

  .settings-mobile-nav {
    display: block;
    overflow: hidden;
    background: var(--color-bg-2);
    border: 1px solid var(--color-border-2);
    border-radius: 8px;
  }
}
</style>
