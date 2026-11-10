<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconCheckCircle,
  IconLock,
  IconRefresh,
  IconSave,
  IconSettings,
  IconThunderbolt,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type { RuntimeConfig, SwarmStatisticsStatus } from '@/api/adminTypes'
import { useSessionStore } from '@/stores/session'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatDuration, formatNumber } = useFormatters()
const session = useSessionStore()
const loading = ref(true)
const saving = ref(false)
const refreshStarting = ref(false)
const status = ref<SwarmStatisticsStatus | null>(null)
const form = reactive<RuntimeConfig>({
  protocolMinVersion: 20,
  protocolMaxVersion: 20,
  queryIpPowCaptcha: false,
  clientDiscoveryEnabled: false,
  swarmStatisticsEnabled: true,
  swarmStatisticsRefreshMinutes: 60,
  swarmStatisticsWindowDays: 7,
})
let pollTimer: number | undefined

async function load() {
  loading.value = true
  try {
    Object.assign(form, await adminApi.config())
    await loadStatus()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function loadStatus() {
  try {
    status.value = await adminApi.swarmStatisticsStatus()
    if (status.value.running) {
      scheduleStatusPoll()
    }
  } catch {
    // The main settings form remains usable when the status endpoint is temporarily unavailable.
  }
}

function scheduleStatusPoll() {
  window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(async () => {
    await loadStatus()
    if (status.value?.running) scheduleStatusPoll()
  }, 2500)
}

async function save() {
  if (
    form.protocolMinVersion < 1 ||
    form.protocolMaxVersion < form.protocolMinVersion
  ) {
    Message.warning(t('admin.settings.invalidRange'))
    return
  }
  if (form.swarmStatisticsRefreshMinutes < 1 || form.swarmStatisticsWindowDays < 1) {
    Message.warning(t('admin.settings.invalidStatistics'))
    return
  }
  saving.value = true
  try {
    Object.assign(form, await adminApi.updateConfig(form))
    await loadStatus()
    if (!form.swarmStatisticsEnabled) {
      session.traffic = null
    }
    await session.load().catch(() => undefined)
    Message.success(t('admin.common.updated'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

async function refreshStatistics() {
  refreshStarting.value = true
  try {
    status.value = await adminApi.refreshSwarmStatistics()
    Message.success(
      status.value.running
        ? t('admin.settings.refreshStarted')
        : t('admin.settings.refreshBusy'),
    )
    scheduleStatusPoll()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    refreshStarting.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => window.clearTimeout(pollTimer))
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
            <IconThunderbolt />
            {{ t('admin.settings.statisticsTitle') }}
          </span>
        </template>
        <template #extra>
          <a-button
            :loading="refreshStarting || status?.running"
            :disabled="status?.running || !status?.enabled"
            @click="refreshStatistics"
          >
            <template #icon><IconRefresh /></template>
            {{ status?.running ? t('admin.settings.refreshing') : t('admin.settings.refreshNow') }}
          </a-button>
        </template>

        <a-alert
          v-if="status?.lastError"
          type="error"
          show-icon
          class="status-alert"
        >
          <template #title>{{ t('admin.settings.refreshError') }}</template>
          {{ status.lastError }}
        </a-alert>
        <a-alert
          v-else-if="status?.stale"
          type="warning"
          show-icon
          class="status-alert"
        >
          {{ t('admin.settings.dataStale') }}
        </a-alert>

        <div class="status-grid">
          <div class="status-item">
            <span>{{ t('admin.settings.refreshState') }}</span>
            <a-tag :color="status?.running ? 'arcoblue' : status?.enabled ? 'green' : 'gray'">
              {{
                status?.running
                  ? t('admin.settings.stateRunning')
                  : status?.enabled
                    ? t('admin.settings.stateEnabled')
                    : t('admin.settings.stateDisabled')
              }}
            </a-tag>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.dataUpdatedAt') }}</span>
            <strong>{{ formatDate(status?.dataUpdatedAt) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.lastDuration') }}</span>
            <strong>{{ formatDuration(status?.lastDurationMs) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.updatedUsers') }}</span>
            <strong>{{ formatNumber(status?.updatedUsers) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.lastTrigger') }}</span>
            <strong>
              {{
                status?.trigger === 'MANUAL'
                  ? t('admin.settings.manualTrigger')
                  : status?.trigger === 'SCHEDULED'
                    ? t('admin.settings.scheduledTrigger')
                    : t('common.notAvailable')
              }}
            </strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.lastFinishedAt') }}</span>
            <strong>{{ formatDate(status?.lastFinishedAt) }}</strong>
          </div>
        </div>

        <a-divider />
        <a-form :model="form" layout="vertical">
          <div class="settings-grid">
            <a-form-item :label="t('admin.settings.statisticsEnabled')">
              <a-switch v-model="form.swarmStatisticsEnabled" />
            </a-form-item>
            <div />
            <a-form-item :label="t('admin.settings.refreshInterval')" required>
              <a-input-number
                v-model="form.swarmStatisticsRefreshMinutes"
                :min="1"
                :max="10080"
              >
                <template #suffix>{{ t('admin.settings.minutes') }}</template>
              </a-input-number>
            </a-form-item>
            <a-form-item :label="t('admin.settings.windowDays')" required>
              <a-input-number
                v-model="form.swarmStatisticsWindowDays"
                :min="1"
                :max="90"
              >
                <template #suffix>{{ t('admin.settings.days') }}</template>
              </a-input-number>
            </a-form-item>
          </div>
          <p class="form-help">{{ t('admin.settings.statisticsHelp') }}</p>
        </a-form>
      </a-card>

      <a-card class="surface-card">
        <template #title>
          <span class="card-title">
            <IconSettings />
            BTN
          </span>
        </template>
        <a-form :model="form" layout="vertical" @submit="save">
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
  width: min(1080px, 100%);
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

.status-alert {
  margin-bottom: 16px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.status-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 62px;
  padding: 12px 14px;
  background: var(--color-fill-1);
  border-radius: 6px;
}

.status-item > span {
  color: var(--color-text-3);
  font-size: 12px;
}

.status-item strong {
  overflow-wrap: anywhere;
  font-size: 13px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.settings-grid :deep(.arco-input-number) {
  width: 100%;
}

.form-help,
.security-card p {
  margin: 0;
  color: var(--color-text-2);
  line-height: 1.75;
}

@media (max-width: 760px) {
  .status-grid,
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
