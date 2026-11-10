<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconCheckCircle,
  IconLock,
  IconSave,
  IconSettings,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type { RuntimeConfig } from '@/api/adminTypes'
import { errorMessage } from '@/utils/format'

const { t } = useI18n()
const loading = ref(true)
const saving = ref(false)
const form = reactive<RuntimeConfig>({
  protocolMinVersion: 20,
  protocolMaxVersion: 20,
  queryIpPowCaptcha: false,
  clientDiscoveryEnabled: false,
  trackerEndpoint: '',
})

async function load() {
  loading.value = true
  try {
    Object.assign(form, await adminApi.config())
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function validTrackerEndpoint(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

async function save() {
  if (
    form.protocolMinVersion < 1 ||
    form.protocolMaxVersion < form.protocolMinVersion
  ) {
    Message.warning(t('admin.settings.invalidRange'))
    return
  }
  if (!validTrackerEndpoint(form.trackerEndpoint)) {
    Message.warning(t('admin.settings.invalidEndpoint'))
    return
  }
  saving.value = true
  try {
    Object.assign(form, await adminApi.updateConfig(form))
    Message.success(t('admin.common.updated'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack settings-page">
    <PageHeader
      :title="t('admin.settings.title')"
      :description="t('admin.settings.description')"
    >
      <template #actions>
        <a-button type="primary" :loading="saving" @click="save">
          <template #icon><IconSave /></template>
          {{ t('admin.common.save') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      <template #title>
        <IconCheckCircle />
        {{ t('admin.settings.hotReloadTitle') }}
      </template>
      {{ t('admin.settings.hotReloadText') }}
    </a-alert>

    <a-spin :loading="loading" class="settings-spin">
      <a-card class="surface-card">
        <template #title>
          <span class="card-title">
            <IconSettings />
            BTN
          </span>
        </template>
        <a-form :model="form" layout="vertical" @submit.prevent="save">
          <div class="settings-grid">
            <a-form-item :label="t('admin.settings.protocolMin')" required>
              <a-input-number v-model="form.protocolMinVersion" :min="1" :max="10000" />
            </a-form-item>
            <a-form-item :label="t('admin.settings.protocolMax')" required>
              <a-input-number v-model="form.protocolMaxVersion" :min="1" :max="10000" />
            </a-form-item>
            <a-form-item :label="t('admin.settings.queryIpPow')">
              <a-switch v-model="form.queryIpPowCaptcha" />
            </a-form-item>
            <a-form-item :label="t('admin.settings.clientDiscovery')">
              <a-switch v-model="form.clientDiscoveryEnabled" />
            </a-form-item>
            <a-form-item class="settings-wide" :label="t('admin.settings.trackerEndpoint')" required>
              <a-input v-model="form.trackerEndpoint" />
            </a-form-item>
          </div>
        </a-form>
      </a-card>

      <a-card class="surface-card security-card">
        <template #title>
          <span class="card-title">
            <IconLock />
            {{ t('admin.settings.securityTitle') }}
          </span>
        </template>
        <p>{{ t('admin.settings.securityNotice') }}</p>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.settings-page {
  width: min(1040px, 100%);
}

.settings-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.settings-wide {
  grid-column: 1 / -1;
}

.settings-grid :deep(.arco-input-number) {
  width: 100%;
}

.security-card p {
  margin: 0;
  color: var(--color-text-2);
  line-height: 1.75;
}

@media (max-width: 620px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .settings-wide {
    grid-column: auto;
  }
}
</style>
