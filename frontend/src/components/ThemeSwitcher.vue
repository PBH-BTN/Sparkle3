<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { IconDesktop, IconMoon, IconSun, IconCheck } from '@arco-design/web-vue/es/icon'
import { themeModes, useTheme } from '@/composables/useTheme'
const { t } = useI18n()
const { mode, setThemeMode } = useTheme()
const icons = { system: IconDesktop, light: IconSun, dark: IconMoon }
</script>
<template>
  <a-dropdown trigger="click">
    <a-button type="text" :aria-label="t('appearance.theme') + ': ' + t('appearance.' + mode)">
      <template #icon><component :is="icons[mode]" /></template>
    </a-button>
    <template #content>
      <a-doption v-for="item in themeModes" :key="item" @click="setThemeMode(item)">
        <template #icon><component :is="icons[item]" /></template>
        {{ t('appearance.' + item) }}
        <IconCheck v-if="mode === item" class="theme-check" />
      </a-doption>
    </template>
  </a-dropdown>
</template>
<style scoped>
.theme-check { margin-left: 16px; color: rgb(var(--primary-6)); }
</style>
