<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconApps,
  IconBug,
  IconCloud,
  IconCode,
  IconComputer,
  IconDashboard,
  IconDesktop,
  IconRefresh,
  IconStorage,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import MetricTrendChart from '@/components/admin/MetricTrendChart.vue'
import { adminApi } from '@/api/admin'
import type { AdminDashboard } from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const {
  formatBytes,
  formatDate,
  formatDuration,
  formatNumber,
  formatProgress,
  formatRatio,
} = useFormatters()
const data = ref<AdminDashboard | null>(null)
const loading = ref(true)
let refreshTimer: ReturnType<typeof setInterval> | undefined

const metricCards = computed(() => {
  if (!data.value) return []
  return [
    {
      key: 'cpu',
      label: t('admin.dashboard.cpu'),
      value: data.value.current.cpuUsage,
      detail: `${data.value.jvm.availableProcessors} ${t('admin.dashboard.processors').toLowerCase()}`,
      icon: IconComputer,
      points: data.value.history.map((sample) => ({
        timestamp: sample.timestamp,
        value: sample.cpuUsage,
      })),
    },
    {
      key: 'memory',
      label: t('admin.dashboard.memory'),
      value: data.value.current.memoryUsage,
      detail: t('admin.dashboard.usedOfTotal', {
        used: formatBytes(data.value.current.memoryUsed),
        total: formatBytes(data.value.current.memoryTotal),
      }),
      icon: IconStorage,
      points: data.value.history.map((sample) => ({
        timestamp: sample.timestamp,
        value: sample.memoryUsage,
      })),
    },
    {
      key: 'disk',
      label: t('admin.dashboard.disk'),
      value: data.value.current.diskUsage,
      detail: t('admin.dashboard.usedOfTotal', {
        used: formatBytes(data.value.current.diskUsed),
        total: formatBytes(data.value.current.diskTotal),
      }),
      icon: IconDesktop,
      points: data.value.history.map((sample) => ({
        timestamp: sample.timestamp,
        value: sample.diskUsage,
      })),
    },
  ]
})

const moduleIcons: Record<string, unknown> = {
  users: IconUserGroup,
  userApps: IconApps,
  manualRules: IconCode,
  bans24h: IconBug,
  activeApps24h: IconCloud,
  swarms24h: IconDashboard,
}

function moduleLabel(key: string) {
  return t(`admin.dashboard.moduleLabels.${key}`)
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    data.value = await adminApi.dashboard()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  refreshTimer = setInterval(() => load(true), 10_000)
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('admin.dashboard.title')" :description="t('admin.dashboard.description')">
      <template #actions>
        <span v-if="data" class="updated-at">
          {{ t('admin.dashboard.updatedAt', { time: formatDate(data.generatedAt) }) }}
        </span>
        <a-button :loading="loading" @click="load()">
          <template #icon><IconRefresh /></template>
          {{ t('admin.dashboard.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :loading="loading" class="dashboard-spin">
      <template v-if="data">
        <div class="host-grid">
          <a-card v-for="metric in metricCards" :key="metric.key" class="surface-card metric-card">
            <div class="metric-heading">
              <span class="metric-icon"><component :is="metric.icon" /></span>
              <div>
                <strong>{{ metric.label }}</strong>
                <span>{{ metric.detail }}</span>
              </div>
              <a-tag color="green" size="small">{{ t('admin.dashboard.realtime') }}</a-tag>
            </div>
            <div class="metric-value">
              <strong>{{ formatProgress(metric.value) }}</strong>
              <span>{{ t('admin.dashboard.samples', { count: metric.points.length }) }}</span>
            </div>
            <a-progress
              type="line"
              :percent="metric.value"
              :show-text="false"
              :stroke-width="6"
            />
            <h3>{{ t('admin.dashboard.trend7d') }}</h3>
            <MetricTrendChart
              :points="metric.points"
              :empty-text="t('admin.dashboard.noHistory')"
            />
          </a-card>
        </div>

        <a-card class="surface-card">
          <template #title>
            <span class="card-title">
              <IconDashboard />
              {{ t('admin.dashboard.modules') }}
            </span>
          </template>
          <div class="module-grid">
            <div v-for="metric in data.modules" :key="metric.key" class="module-card">
              <span class="module-icon">
                <component :is="moduleIcons[metric.key] ?? IconDashboard" />
              </span>
              <div>
                <span>{{ moduleLabel(metric.key) }}</span>
                <strong>{{ formatNumber(metric.value) }}</strong>
              </div>
            </div>
          </div>
        </a-card>

        <a-card class="surface-card">
          <template #title>
            <span class="card-title">
              <IconComputer />
              {{ t('admin.dashboard.systemInfo') }}
            </span>
          </template>
          <a-descriptions :column="{ xs: 1, md: 2, xl: 3 }" bordered>
            <a-descriptions-item :label="t('admin.dashboard.processors')">
              {{ data.jvm.availableProcessors }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.dashboard.loadAverage')">
              {{ formatRatio(data.jvm.systemLoadAverage) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.dashboard.uptime')">
              {{ formatDuration(data.jvm.uptimeMillis) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.dashboard.heap')">
              {{ formatBytes(data.jvm.heapUsed) }} / {{ formatBytes(data.jvm.heapMax) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.dashboard.threads')">
              {{ data.jvm.threadCount }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.dashboard.javaVersion')">
              {{ data.jvm.javaVersion }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.updated-at {
  color: var(--color-text-3);
  font-size: 12px;
}

.dashboard-spin {
  display: block;
  width: 100%;
}

.host-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.metric-card :deep(.arco-card-body) {
  padding: 20px;
}

.metric-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.metric-heading > div {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.metric-heading strong {
  font-size: 14px;
}

.metric-heading span {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-icon,
.module-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
  border-radius: var(--app-radius);
}

.metric-value {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 20px 0 8px;
}

.metric-value strong {
  color: var(--color-text-1);
  font-size: 30px;
  line-height: 1;
}

.metric-value span {
  color: var(--color-text-3);
  font-size: 11px;
}

h3 {
  margin: 18px 0 6px;
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.module-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}

.module-card div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.module-card span {
  color: var(--color-text-3);
  font-size: 11px;
  line-height: 1.35;
}

.module-card strong {
  margin-top: 3px;
  font-size: 20px;
}

@media (max-width: 1200px) {
  .module-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .host-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .module-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
