<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconLanguage } from '@arco-design/web-vue/es/icon'
import { setLocale, supportedLocales, type AppLocale } from '@/i18n'

const { locale, t } = useI18n()
const currentLabel = computed(
  () => supportedLocales.find((item) => item.value === locale.value)?.label ?? 'Language',
)

function changeLocale(value: AppLocale) {
  setLocale(value)
}
</script>

<template>
  <a-dropdown trigger="click">
    <a-button type="text" class="language-button">
      <template #icon><IconLanguage /></template>
      {{ locale === 'zh-CN' ? '中文' : 'EN' }}
    </a-button>
    <template #content>
      <a-doption
        v-for="item in supportedLocales"
        :key="item.value"
        :class="{ 'is-active-locale': item.value === locale }"
        @click="changeLocale(item.value)"
      >
        {{ item.label }}
        <span class="locale-short">{{ item.value === 'zh-CN' ? '中' : 'EN' }}</span>
      </a-doption>
    </template>
    <span class="sr-only">{{ t('common.language') }}: {{ currentLabel }}</span>
  </a-dropdown>
</template>

<style scoped>
.language-button {
  color: var(--color-text-2);
}

.locale-short {
  float: right;
  margin-left: 18px;
  color: var(--color-text-3);
  font-size: 12px;
}

.is-active-locale {
  color: rgb(var(--primary-6));
}
</style>
