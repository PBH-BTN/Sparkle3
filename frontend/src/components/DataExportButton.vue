<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { IconDownload } from '@arco-design/web-vue/es/icon'
import { uiApi } from '@/api'
import type { DataExportCategory, DataExportOptions } from '@/api/types'
import { useSessionStore } from '@/stores/session'

const props = defineProps<{
  category: DataExportCategory
  filters: Record<string, unknown> | null
  disabled?: boolean
}>()

const { t, locale } = useI18n()
const session = useSessionStore()
const visible = ref(false)
const loading = ref(false)
const options = ref<DataExportOptions | null>(null)
let selection: { category: DataExportCategory; filters: Record<string, unknown>; language: string } | null = null

async function open() {
  if (!props.filters || loading.value) return
  selection = { category: props.category, filters: { ...props.filters }, language: locale.value }
  loading.value = true
  try {
    options.value = await uiApi.exportOptions(selection.language)
    visible.value = true
  } catch {
    // The shared API client displays the request error.
  } finally {
    loading.value = false
  }
}

async function confirm() {
  if (!selection || !options.value?.slotsAvailable) return false
  try {
    await uiApi.requestExport(selection)
    Message.success(t('exports.queued'))
    return true
  } catch {
    return false
  }
}
</script>

<template>
  <template v-if="session.user?.roles.includes('supporter')">
    <a-button :disabled="disabled || !filters" :loading="loading" @click="open">
      <template #icon><IconDownload /></template>
      {{ t('exports.button') }}
    </a-button>
    <a-modal
      v-model:visible="visible"
      :title="t('exports.title')"
      :ok-text="t('exports.start')"
      :ok-button-props="{ disabled: !options?.slotsAvailable }"
      :on-before-ok="confirm"
      :mask-closable="false"
      width="min(560px, calc(100vw - 32px))"
    >
      <div class="export-prompt">{{ options?.prompt }}</div>
    </a-modal>
  </template>
</template>

<style scoped>
.export-prompt {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
