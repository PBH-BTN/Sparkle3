<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TableColumnData } from '@arco-design/web-vue'
import { uiApi } from '@/api'
import type { DailyTraffic } from '@/api/types'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import TablePager from '@/components/TablePager.vue'
import { useFormatters } from '@/utils/format'

const { t, locale } = useI18n()
const { formatBytes } = useFormatters()
const statisticsStartedOn = '2026/11/15'
const page = ref(1)
const size = ref(20)
const total = ref(0)
const records = ref<DailyTraffic[]>([])
const loading = ref(false)
const failed = ref(false)
let sequence = 0
const metrics = [
  ['upload', 'career.upload', 'careerHelp.upload'],
  ['download', 'career.download', 'careerHelp.download'],
  ['uploadAck', 'career.uploadAck', 'careerHelp.uploadAck'],
  ['downloadAck', 'career.downloadAck', 'careerHelp.downloadAck'],
  ['seeding', 'career.seeding', 'careerHelp.seeding'],
  ['downloading', 'career.downloading', 'careerHelp.downloading'],
  ['loss', 'traffic.loss', 'traffic.lossHelp'],
  ['lossRatio', 'traffic.lossRatio', 'traffic.lossHelp'],
] as const
const columns = computed<TableColumnData[]>(() => [
  { title: t('traffic.day'), slotName: 'day', width: 145, fixed: 'left' },
  ...metrics.map(([key, label]) => ({ title: t(label), slotName: key, width: 180 })),
])
function formatMetric(row: DailyTraffic, key: string) {
  const value = row.metrics[key] || '0'
  if (key === 'seeding' || key === 'downloading') {
    return t('traffic.hours', { value: new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2 }).format(Number(value) / 3600) })
  }
  return key === 'lossRatio' ? value + '%' : formatBytes(value)
}
async function load() {
  const request = ++sequence
  loading.value = true
  failed.value = false
  try {
    const result = await uiApi.dailyTraffic(page.value, size.value)
    if (request !== sequence) return
    records.value = result.records
    total.value = result.total
  } catch {
    if (request === sequence) { failed.value = true; records.value = []; total.value = 0 }
  } finally {
    if (request === sequence) loading.value = false
  }
}
function changeSize(value: number) { size.value = value; page.value = 1 }
watch([page, size], load, { immediate: true })
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('traffic.heading')" :description="t('traffic.description')" />
    <a-card class="surface-card table-card">
      <div class="traffic-toolbar">
        <p class="traffic-note">{{ t('traffic.note', { date: statisticsStartedOn }) }}</p>
        <a-button :loading="loading" @click="load">{{ t('traffic.refresh') }}</a-button>
      </div>
      <p class="traffic-note">{{ t('traffic.timeNote') }}</p>
      <p class="traffic-note">{{ t('traffic.lossHelp') }}</p>
      <a-alert v-if="failed" type="error">{{ t('traffic.failed') }}</a-alert>
      <a-table v-else :columns="columns" :data="records" :loading="loading" :pagination="false" :scroll="{ x: 1585 }" row-key="day">
        <template #day="{ record }">{{ record.day.replaceAll('-', '/') }}</template>
        <template v-for="[key, , help] in metrics" :key="key" #[key]="{ record }">
          <a-tooltip :content="t(help)">{{ formatMetric(record, key) }}</a-tooltip>
        </template>
        <template #empty><EmptyState :title="t('traffic.empty')" :description="t('traffic.emptyDescription')" /></template>
      </a-table>
      <TablePager :current="page" :page-size="size" :total="total" :records-length="records.length" @change="page = $event" @page-size-change="changeSize" />
    </a-card>
  </div>
</template>

<style scoped>
.traffic-note { color: var(--color-text-3); line-height: 1.7; margin: 12px 0; }
.traffic-toolbar { display: flex; align-items: flex-start; gap: 16px; margin-bottom: 12px; }
.traffic-toolbar .traffic-note { flex: 1; min-width: 0; margin: 0; }
.traffic-toolbar :deep(.arco-btn) { flex-shrink: 0; }
@media (max-width: 980px) { .traffic-note { font-size: 13px; } }
@media (max-width: 720px) { .traffic-toolbar { gap: 12px; } }
</style>
