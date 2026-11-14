<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { themeModes, useTheme, type ThemeMode } from '@/composables/useTheme'
const { t } = useI18n()
const { mode, setThemeMode } = useTheme()
</script>
<template>
  <a-card class="surface-card" :title="t('settings.menu.personalization')">
    <a-form :model="{ mode }" layout="vertical">
      <a-form-item :label="t('appearance.theme')" :help="t('appearance.description')">
        <a-radio-group :model-value="mode" @change="setThemeMode($event as ThemeMode)">
          <a-space wrap>
            <a-radio v-for="item in themeModes" :key="item" :value="item">{{ t('appearance.' + item) }}</a-radio>
          </a-space>
        </a-radio-group>
      </a-form-item>
    </a-form>
  </a-card>
</template>
