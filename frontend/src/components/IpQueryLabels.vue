<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { IpQueryResult } from '@/api/types'

const props = defineProps<{ result: IpQueryResult }>()
const { t, te } = useI18n()

const labels = computed(() => {
  const details = props.result.label_details
  if (!details) {
    return (props.result.labels ?? []).map((label) => label === 'BTN用户' ? t('ipQuery.btnUser') : label)
  }
  return [...new Set(details.map(({ type, source, name }) => {
    if (type === 'btn_bypass') return t('ipQuery.btnBypass')
    if (type === 'btn_user') {
      return name?.trim() ? `${t('ipQuery.btnUser')}->${name}` : t('ipQuery.btnUser')
    }
    const sourceKey = `rulePreview.sources.${source}`
    const rule = name?.trim() ? name : te(sourceKey) ? t(sourceKey) : t('rulePreview.generatedRule')
    return `${t(`rulePreview.types.${type}`)}->${rule}`
  }))]
})
</script>

<template>
  <a-tag v-for="label in labels" :key="label" color="arcoblue" class="ip-query-label">
    {{ label }}
  </a-tag>
</template>

<style scoped>
.ip-query-label {
  height: auto;
  min-height: 24px;
  max-width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
}
</style>
