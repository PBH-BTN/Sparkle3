<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { adminApi } from '@/api/admin'
import type { DatabaseConfigEntry } from '@/api/adminTypes'
import { errorMessage } from '@/utils/format'

const { t } = useI18n()
const configKey = 'sparkle.career.dedup-retention-days'
const entry = ref<DatabaseConfigEntry | null>(null)
const retentionDays = ref(7)
const loading = ref(true)
const saving = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const entries = await adminApi.databaseConfig(configKey)
    const value = entries.find((item) => item.key === configKey)
    if (!value) throw new Error(t('common.loadFailed'))
    entry.value = value
    retentionDays.value = Number(value.value)
  } catch (error) {
    loadError.value = errorMessage(error, t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!Number.isInteger(retentionDays.value) || retentionDays.value < 7 || retentionDays.value > 3650) {
    Message.warning(t('admin.settings.careerDedupInvalid'))
    return
  }
  saving.value = true
  try {
    entry.value = await adminApi.updateDatabaseConfig(configKey, String(retentionDays.value))
    Message.success(t('admin.settings.careerDedupSaved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <a-card class="surface-card" :title="t('admin.settings.careerDedupTitle')">
    <a-spin :loading="loading" style="width: 100%">
      <a-alert v-if="loadError" type="error">
        {{ loadError }}
        <template #action>
          <a-button size="small" @click="load">{{ t('common.retry') }}</a-button>
        </template>
      </a-alert>
      <a-form v-else-if="entry" :model="{ retentionDays }" layout="vertical" @submit="save">
        <a-alert v-if="entry.environmentOverride" type="info" class="override-notice">
          {{ t('admin.databaseConfig.envPriority') }}
        </a-alert>
        <a-form-item :label="t('admin.settings.careerDedupDays')" required>
          <a-input-number v-model="retentionDays" :min="7" :max="3650" :precision="0" class="retention-input">
            <template #suffix>{{ t('admin.settings.days') }}</template>
          </a-input-number>
          <template #extra>{{ t('admin.settings.careerDedupHelp') }}</template>
        </a-form-item>
        <p class="form-help">{{ t('admin.settings.careerDedupResume') }}</p>
        <a-button type="primary" html-type="submit" :loading="saving" :disabled="loading">
          {{ t('admin.databaseConfig.save') }}
        </a-button>
      </a-form>
    </a-spin>
  </a-card>
</template>

<style scoped>
.retention-input {
  width: 240px;
  max-width: 100%;
}

.override-notice {
  margin-bottom: 16px;
}
</style>
