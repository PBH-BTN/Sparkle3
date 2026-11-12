<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconCopy,
  IconRefresh,
  IconSave,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type { FlarumIntegrationSettings } from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()

const loading = ref(true)
const saving = ref(false)
const loadError = ref('')
const settings = ref<FlarumIntegrationSettings | null>(null)
const form = reactive({
  token: '',
  clearToken: false,
})

const endpoint = computed(
  () => `${window.location.origin}/api/integrations/flarum/user-by-github`,
)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    settings.value = await adminApi.flarumIntegration()
    form.token = ''
    form.clearToken = false
  } catch (error) {
    loadError.value = errorMessage(error, t('common.loadFailed'))
    Message.error(loadError.value)
  } finally {
    loading.value = false
  }
}

async function save() {
  const token = form.token.trim()
  if (!form.clearToken && !token) {
    Message.warning(
      t(
        settings.value?.tokenConfigured
          ? 'admin.flarumIntegration.tokenKeepHint'
          : 'admin.flarumIntegration.tokenRequired',
      ),
    )
    return
  }

  saving.value = true
  try {
    settings.value = await adminApi.updateFlarumIntegration({
      token: form.clearToken ? undefined : token,
      clearToken: form.clearToken,
    })
    form.token = ''
    form.clearToken = false
    Message.success(t('admin.flarumIntegration.saved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

async function copyEndpoint() {
  try {
    await navigator.clipboard.writeText(endpoint.value)
    Message.success(t('admin.flarumIntegration.endpointCopied'))
  } catch {
    Message.error(t('admin.flarumIntegration.copyFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader
      :title="t('admin.flarumIntegration.title')"
      :description="t('admin.flarumIntegration.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      {{ t('admin.flarumIntegration.alert') }}
    </a-alert>

    <a-alert v-if="loadError" type="error" show-icon>
      {{ loadError }}
    </a-alert>

    <a-spin :loading="loading">
      <a-card class="surface-card">
        <template #title>{{ t('admin.flarumIntegration.settingsTitle') }}</template>
        <a-form :model="form" layout="vertical">
          <a-form-item :label="t('admin.flarumIntegration.token')">
            <a-input-password
              v-model="form.token"
              :placeholder="
                settings?.tokenConfigured
                  ? t('admin.flarumIntegration.tokenKeepPlaceholder')
                  : t('admin.flarumIntegration.tokenPlaceholder')
              "
              :max-length="4096"
              :disabled="form.clearToken"
            />
            <template #extra>
              <div class="token-meta">
                <a-tag :color="settings?.tokenConfigured ? 'green' : 'gray'">
                  {{
                    settings?.tokenConfigured
                      ? t('admin.flarumIntegration.tokenConfigured')
                      : t('admin.flarumIntegration.tokenMissing')
                  }}
                </a-tag>
                <a-checkbox
                  v-if="settings?.tokenConfigured"
                  v-model="form.clearToken"
                >
                  {{ t('admin.flarumIntegration.clearToken') }}
                </a-checkbox>
              </div>
            </template>
          </a-form-item>

          <a-form-item :label="t('admin.flarumIntegration.endpoint')">
            <div class="endpoint-row">
              <code>{{ endpoint }}</code>
              <a-button type="text" @click="copyEndpoint">
                <template #icon><IconCopy /></template>
                {{ t('admin.flarumIntegration.copyEndpoint') }}
              </a-button>
            </div>
            <template #extra>
              {{ t('admin.flarumIntegration.endpointHint') }}
            </template>
          </a-form-item>

          <div class="save-row">
            <a-button
              type="primary"
              :loading="saving"
              :disabled="Boolean(loadError)"
              @click="save"
            >
              <template #icon><IconSave /></template>
              {{ t('admin.common.save') }}
            </a-button>
            <span v-if="settings?.updatedAt" class="updated">
              {{
                t('admin.flarumIntegration.updatedAt', {
                  time: formatDate(settings.updatedAt),
                })
              }}
            </span>
          </div>
        </a-form>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.token-meta,
.endpoint-row,
.save-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.token-meta,
.save-row {
  flex-wrap: wrap;
}

.endpoint-row {
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 9px 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
}

.endpoint-row code {
  min-width: 0;
  overflow-wrap: anywhere;
}

.updated {
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
