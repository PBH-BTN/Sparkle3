<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconRefresh,
  IconSave,
  IconThunderbolt,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  HomepageStatisticsAdminResponse,
  HomepageStatisticsModuleConfig,
  HomepageStatisticsModuleStatus,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatDuration } = useFormatters()
const loading = ref(true)
const saving = ref(false)
const refreshingAll = ref(false)
const refreshingModule = ref<string | null>(null)
const data = ref<HomepageStatisticsAdminResponse | null>(null)
const configs = ref<HomepageStatisticsModuleConfig[]>([])
let pollTimer: number | undefined

const runningCount = computed(
  () => data.value?.modules.filter((module) => module.running).length ?? 0,
)
const anyRunning = computed(() => runningCount.value > 0)
const anyEnabled = computed(() => configs.value.some((module) => module.enabled))

function moduleLabel(module: string, field: 'name' | 'description') {
  return t(`admin.homepageStatistics.modules.${module}.${field}`)
}

function moduleStatus(module: string) {
  return data.value?.modules.find((item) => item.module === module)
}

function triggerLabel(status: HomepageStatisticsModuleStatus | undefined) {
  if (!status?.trigger) return t('common.notAvailable')
  return status.trigger === 'MANUAL'
    ? t('admin.homepageStatistics.manualTrigger')
    : t('admin.homepageStatistics.scheduledTrigger')
}

function applyResponse(response: HomepageStatisticsAdminResponse) {
  data.value = response
  if (configs.value.length === 0) {
    configs.value = response.modules.map((module) => ({
      module: module.module,
      enabled: module.enabled,
      refreshIntervalMinutes: module.refreshIntervalMinutes,
    }))
  }
}

async function load() {
  loading.value = true
  try {
    const response = await adminApi.homepageStatistics()
    configs.value = []
    applyResponse(response)
    if (response.modules.some((module) => module.running)) schedulePoll()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function loadStatus() {
  try {
    data.value = await adminApi.homepageStatistics()
  } catch {
    // Keep the current status visible while the backend is temporarily unavailable.
  }
}

function schedulePoll() {
  window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(async () => {
    await loadStatus()
    if (anyRunning.value) schedulePoll()
  }, 2500)
}

async function save() {
  const invalid = configs.value.some((module) => {
    const interval = Number(module.refreshIntervalMinutes)
    return !Number.isFinite(interval) || interval < 1 || interval > 10_080
  })
  if (invalid) {
    Message.warning(t('admin.homepageStatistics.invalidInterval'))
    return
  }

  saving.value = true
  try {
    const response = await adminApi.updateHomepageStatisticsConfig({
      modules: configs.value.map((module) => ({
        ...module,
        refreshIntervalMinutes: Number(module.refreshIntervalMinutes),
      })),
    })
    applyResponse(response)
    Message.success(t('admin.homepageStatistics.saved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

async function refresh(module = 'all') {
  if (module === 'all') refreshingAll.value = true
  else refreshingModule.value = module
  try {
    data.value = await adminApi.refreshHomepageStatistics(module)
    Message.success(t('admin.homepageStatistics.refreshStarted'))
    schedulePoll()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    refreshingAll.value = false
    refreshingModule.value = null
  }
}

onMounted(load)
onBeforeUnmount(() => window.clearTimeout(pollTimer))
</script>

<template>
  <div class="page-stack homepage-statistics-page">
    <PageHeader
      :title="t('admin.homepageStatistics.title')"
      :description="t('admin.homepageStatistics.description')"
    >
      <template #actions>
        <a-button type="primary" :loading="saving" @click="save">
          <template #icon><IconSave /></template>
          {{ t('admin.common.save') }}
        </a-button>
        <a-button :loading="refreshingAll" :disabled="!anyEnabled" @click="refresh()">
          <template #icon><IconRefresh /></template>
          {{ t('admin.homepageStatistics.refreshAll') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      <template #title>
        {{ t('admin.homepageStatistics.hotReloadTitle') }}
      </template>
      {{ t('admin.homepageStatistics.hotReloadText') }}
    </a-alert>

    <a-spin :loading="loading" class="statistics-spin">
      <a-card class="surface-card">
        <template #title>
          <span class="card-title">
            <IconThunderbolt />
            {{ t('admin.homepageStatistics.tasks') }}
          </span>
        </template>
        <template #extra>
          <a-tag :color="anyRunning ? 'arcoblue' : 'green'">
            {{
              anyRunning
                ? t('admin.homepageStatistics.runningCount', { count: runningCount })
                : t('admin.homepageStatistics.allIdle')
            }}
          </a-tag>
        </template>

        <div class="task-list">
          <div v-for="config in configs" :key="config.module" class="task-row">
            <div class="task-heading">
              <span class="task-icon"><IconRefresh /></span>
              <div>
                <strong>{{ moduleLabel(config.module, 'name') }}</strong>
                <span>{{ moduleLabel(config.module, 'description') }}</span>
              </div>
            </div>

            <div class="task-settings">
              <label>
                <span>
                  {{
                    config.module === 'bittorrent-traffic'
                      ? t('admin.homepageStatistics.updateAndDisplay')
                      : t('admin.homepageStatistics.enabled')
                  }}
                </span>
                <a-switch v-model="config.enabled" />
              </label>
              <label>
                <span>{{ t('admin.homepageStatistics.interval') }}</span>
                <a-input-number
                  v-model="config.refreshIntervalMinutes"
                  :min="1"
                  :max="10080"
                  :precision="0"
                >
                  <template #suffix>{{ t('admin.homepageStatistics.minutes') }}</template>
                </a-input-number>
              </label>
            </div>

            <div class="task-state">
              <a-tag
                :color="
                  moduleStatus(config.module)?.running
                    ? 'arcoblue'
                    : config.enabled
                      ? 'green'
                      : 'gray'
                "
              >
                {{
                  moduleStatus(config.module)?.running
                    ? t('admin.homepageStatistics.running')
                    : config.enabled
                      ? t('admin.common.enabled')
                      : t('admin.common.disabled')
                }}
              </a-tag>
              <span>
                {{ t('admin.homepageStatistics.lastFinished') }}:
                {{ formatDate(moduleStatus(config.module)?.lastFinishedAt) }}
              </span>
              <span>
                {{ t('admin.homepageStatistics.nextRefresh') }}:
                {{ formatDate(moduleStatus(config.module)?.nextRefreshAt) }}
              </span>
              <span>
                {{ t('admin.homepageStatistics.lastDuration') }}:
                {{ formatDuration(moduleStatus(config.module)?.lastDurationMs) }}
              </span>
              <span>
                {{ t('admin.homepageStatistics.lastTrigger') }}:
                {{ triggerLabel(moduleStatus(config.module)) }}
              </span>
            </div>

            <div class="task-action">
              <a-alert
                v-if="moduleStatus(config.module)?.lastError"
                type="error"
                show-icon
                class="task-error"
              >
                {{ moduleStatus(config.module)?.lastError }}
              </a-alert>
              <a-button
                type="outline"
                :loading="refreshingModule === config.module"
                :disabled="
                  moduleStatus(config.module)?.running ||
                  (config.module === 'bittorrent-traffic' && !config.enabled)
                "
                @click="refresh(config.module)"
              >
                <template #icon><IconRefresh /></template>
                {{ t('admin.homepageStatistics.refreshNow') }}
              </a-button>
            </div>
          </div>
        </div>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.homepage-statistics-page {
  width: min(1180px, 100%);
}

.statistics-spin {
  display: block;
  width: 100%;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.25fr) minmax(250px, 1fr) minmax(210px, 0.9fr) auto;
  gap: 18px;
  align-items: center;
  padding: 16px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.task-heading {
  display: flex;
  gap: 11px;
  min-width: 0;
}

.task-heading > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.task-heading strong {
  color: var(--color-text-1);
  font-size: 14px;
}

.task-heading span:not(.task-icon) {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.5;
}

.task-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
  border-radius: 7px;
}

.task-settings {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.task-settings label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 100px;
}

.task-settings label > span {
  color: var(--color-text-3);
  font-size: 11px;
}

.task-settings :deep(.arco-input-number) {
  width: 128px;
}

.task-state {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--color-text-3);
  font-size: 11px;
  line-height: 1.45;
}

.task-action {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.task-error {
  max-width: 280px;
}

@media (max-width: 1180px) {
  .task-row {
    grid-template-columns: minmax(220px, 1fr) minmax(250px, 1fr);
  }

  .task-action {
    align-items: flex-start;
  }
}

@media (max-width: 720px) {
  .task-row {
    grid-template-columns: 1fr;
  }

  .task-settings {
    flex-direction: column;
  }
}
</style>
