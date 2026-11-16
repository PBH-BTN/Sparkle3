<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { YAXisComponentOption } from 'echarts'
import type { EChartsCoreOption } from 'echarts/core'
import type { LineSeriesOption } from 'echarts/charts'
import type { TableColumnData } from '@arco-design/web-vue'
import { uiApi } from '@/api'
import type { DailyTraffic } from '@/api/types'
import PageHeader from '@/components/PageHeader.vue'
import DataExportButton from '@/components/DataExportButton.vue'
import EmptyState from '@/components/EmptyState.vue'
import TablePager from '@/components/TablePager.vue'
import EChart from '@/components/EChart.vue'
import { stackedAreaChartOption } from '@/utils/charts'
import { formatAxisTooltip, sortedTooltipItems } from '@/utils/chartTooltip'
import { useFormatters } from '@/utils/format'

const { t, locale } = useI18n()
const { formatBytes, formatHours } = useFormatters()
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
    return formatHours(value)
  }
  return key === 'lossRatio' ? value + '%' : formatBytes(value)
}
const chartRows = computed(() => [...records.value].sort((a, b) => a.day.localeCompare(b.day)))
const chartRange = computed(() => {
  const rows = chartRows.value
  return rows.length ? rows[0]!.day.replaceAll('-', '/') + ' — ' + rows[rows.length - 1]!.day.replaceAll('-', '/') : ''
})
type MetricKey = typeof metrics[number][0]
type ChartUnit = 'bytes' | 'hours' | 'loss'
function chartOption(keys: MetricKey[], unit: ChartUnit): EChartsCoreOption {
  const rows = chartRows.value
  const byteUnits = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB']
  const peak = unit === 'hours' ? 0n : rows.reduce((max, row) => {
    const sum = keys.filter(key => key !== 'lossRatio').reduce((sum, key) => sum + BigInt(row.metrics[key] || '0'), 0n)
    return sum > max ? sum : max
  }, 0n)
  let divisor = 1n
  let unitIndex = 0
  while (unit !== 'hours' && unitIndex < byteUnits.length - 1 && peak / divisor >= 1024n) {
    divisor *= 1024n
    unitIndex++
  }
  const option = stackedAreaChartOption(rows.map(row => row.day.replaceAll('-', '/')), keys.map(key => ({
    name: t(metrics.find(metric => metric[0] === key)![1]),
    values: rows.map(row => {
      const value = row.metrics[key] || '0'
      if (key === 'lossRatio') return Number(value)
      if (unit === 'hours') return Number(value) / 3600
      const bytes = BigInt(value)
      return Number(bytes / divisor) + Number(bytes % divisor) / Number(divisor)
    }),
  })))
  const number = (value: number) => new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2 }).format(value)
  const axes: YAXisComponentOption[] = [{
    type: 'value', min: 0, name: unit === 'hours' ? t('careerUnits.hours') : byteUnits[unitIndex],
    axisLabel: { color: '#86909C', formatter: number },
    splitLine: { lineStyle: { color: '#E5E6EB' } },
  }]
  if (unit === 'loss') {
    axes.push({
      type: 'value', min: 0, max: 100, name: '%',
      axisLabel: { color: '#86909C', formatter: (value: number) => number(value) + '%' },
      splitLine: { show: false },
    })
    option.grid = { top: 54, right: 16, bottom: 16, left: 10, containLabel: true }
  }
  option.yAxis = axes
  option.series = (option.series as LineSeriesOption[]).map((series, index) => ({
    ...series,
    // Different units and the loss subset must not be added together.
    stack: unit === 'loss' ? undefined : 'total',
    yAxisIndex: keys[index] === 'lossRatio' ? 1 : 0,
    smooth: false,
    showSymbol: rows.length === 1,
    symbol: rows.length === 1 ? 'circle' : 'none',
    areaStyle: keys[index] === 'lossRatio' ? undefined : { opacity: 0.2 },
  }))
  option.tooltip = {
    trigger: 'axis', confine: true,
    formatter: (params: unknown) => sortedTooltipItems(params).map((item, index) => {
      const key = keys.find(key => t(metrics.find(metric => metric[0] === key)![1]) === item.seriesName)
      const row = rows[item.dataIndex ?? 0]
      return formatAxisTooltip([{ ...item, axisValueLabel: index === 0 ? item.axisValueLabel : '', axisValue: '' }],
        () => row && key ? formatMetric(row, key) : '')
    }).join(''),
  }
  return option
}
const charts = computed(() => [
  { key: 'reported', title: t('traffic.chartReported'), option: chartOption(['upload', 'download'], 'bytes') },
  { key: 'confirmed', title: t('traffic.chartConfirmed'), option: chartOption(['uploadAck', 'downloadAck'], 'bytes') },
  { key: 'activity', title: t('traffic.chartActivity'), option: chartOption(['seeding', 'downloading'], 'hours') },
  { key: 'loss', title: t('traffic.chartLoss'), option: chartOption(['loss', 'lossRatio'], 'loss') },
])

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
        <a-space wrap>
          <DataExportButton category="TRAFFIC" :filters="{}" :disabled="loading || failed" />
          <a-button :loading="loading" @click="load">{{ t('traffic.refresh') }}</a-button>
        </a-space>
      </div>
      <p class="traffic-note">{{ t('traffic.timeNote') }}</p>
      <p class="traffic-note">{{ t('traffic.lossHelp') }}</p>
      <section v-if="!failed && records.length" class="traffic-charts" :aria-label="t('traffic.chartTitle')">
        <div class="traffic-chart-heading">
          <h2>{{ t('traffic.chartTitle') }}</h2>
          <span class="traffic-note">{{ chartRange }} · UTC</span>
        </div>
        <div class="traffic-chart-grid">
          <section v-for="chart in charts" :key="chart.key" class="traffic-chart-panel">
            <h3>{{ chart.title }}</h3>
            <EChart :option="chart.option" height="280px" :loading="loading" :loading-text="t('common.loading')" />
          </section>
        </div>
      </section>
      <TablePager :current="page" :page-size="size" :total="total" :records-length="records.length" @change="page = $event" @page-size-change="changeSize" />
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
.traffic-charts { margin: 20px 0; }
.traffic-chart-heading { display: flex; align-items: baseline; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.traffic-chart-heading h2 { margin: 0; font-size: 18px; color: var(--color-text-1); }
.traffic-chart-heading .traffic-note { margin: 0; }
.traffic-chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.traffic-chart-panel { min-width: 0; padding: 16px; border: 1px solid var(--color-border-2); border-radius: var(--app-radius); }
.traffic-chart-panel h3 { margin: 0 0 16px; font-size: 14px; color: var(--color-text-1); }
.traffic-note { color: var(--color-text-3); line-height: 1.7; margin: 12px 0; }
.traffic-toolbar { display: flex; align-items: flex-start; gap: 16px; margin-bottom: 12px; }
.traffic-toolbar .traffic-note { flex: 1; min-width: 0; margin: 0; }
.traffic-toolbar :deep(.arco-btn) { flex-shrink: 0; }
@media (max-width: 980px) { .traffic-chart-grid { grid-template-columns: minmax(0, 1fr); } .traffic-note { font-size: 13px; } }
@media (max-width: 720px) { .traffic-toolbar { gap: 12px; } .traffic-chart-panel { padding: 12px 4px; } }
</style>
