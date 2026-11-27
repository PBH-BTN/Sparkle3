<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import type { DataExportSettings } from '@/api/adminTypes'
import { adminApi } from '@/api/admin'
import PageHeader from '@/components/PageHeader.vue'

const { t } = useI18n()
const settings = ref<DataExportSettings | null>(null)
const loading = ref(true)
const saving = ref(false)
const limits = [
  { key: 'maxRows', unit: 'rows' },
  { key: 'maxFileMiB', unit: 'mib' },
  { key: 'retentionHours', unit: 'hours' },
  { key: 'downloadMbps', unit: 'mbps' },
  { key: 'dailySlots', unit: 'times' },
  { key: 'poolMiB', unit: 'mib' },
] as const

async function load() {
  loading.value = true
  try {
    settings.value = await adminApi.exportSettings()
  } catch {
    settings.value = null
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!settings.value || saving.value) return
  saving.value = true
  try {
    settings.value = await adminApi.updateExportSettings(settings.value)
    Message.success(t('admin.common.updated'))
  } catch {
    // The shared API client displays the request error.
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('admin.organization.pages.exports')" />
    <a-card class="surface-card">
      <a-spin :loading="loading" class="exports-settings">
        <a-form v-if="settings" :model="settings" layout="vertical" @submit="save">
          <a-row :gutter="16">
            <a-col v-for="{ key, unit } in limits" :key="key" :xs="24" :sm="12" :lg="8">
              <a-form-item :label="t(`admin.exports.${key}`)" required>
                <a-input-number v-model="settings[key]" :min="1" :max="2147483647" :precision="0">
                  <template #suffix>{{ t(`admin.exports.units.${unit}`) }}</template>
                </a-input-number>
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item :label="t('admin.exports.promptZh')" required>
            <a-textarea v-model="settings.promptZh" :max-length="4000" :auto-size="{ minRows: 6 }" />
          </a-form-item>
          <a-form-item :label="t('admin.exports.promptEn')" required>
            <a-textarea v-model="settings.promptEn" :max-length="4000" :auto-size="{ minRows: 6 }" />
          </a-form-item>
          <p class="muted">
            {{ t('admin.exports.placeholders') }}
            <code>{export_slots_max}</code>, <code>{export_slots_available}</code>,
            <code>{export_rows_max}</code>, <code>{export_filesize_max}</code>
          </p>
          <a-button type="primary" html-type="submit" :loading="saving">
            {{ t('admin.common.save') }}
          </a-button>
        </a-form>
        <a-button v-else-if="!loading" @click="load">{{ t('admin.common.refresh') }}</a-button>
      </a-spin>
    </a-card>
  </div>
</template>

<style scoped>
.exports-settings {
  display: block;
  width: 100%;
  min-height: 40px;
}
</style>
