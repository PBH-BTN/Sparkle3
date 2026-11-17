<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LineSeriesOption } from 'echarts/charts'
import type { XAXisComponentOption, YAXisComponentOption } from 'echarts'
import { uiApi } from '@/api'
import type { DailyTraffic } from '@/api/types'
import EChart from '@/components/EChart.vue'
import HomeStatisticsFilters from '@/components/HomeStatisticsFilters.vue'
import { stackedAreaChartOption } from '@/utils/charts'
import { formatAxisTooltip, sortedTooltipItems } from '@/utils/chartTooltip'
import { useFormatters } from '@/utils/format'

const props = defineProps<{
  showReported: boolean
  showConfirmed: boolean
  showActivity: boolean
  showAverageLoss: boolean
  showTotalLoss: boolean
}>()
const { t } = useI18n()
const showTraffic = computed(() => props.showReported || props.showConfirmed || props.showActivity || props.showAverageLoss)
const sectionTitle = computed(() => t(showTraffic.value ? 'home.networkTrafficTitle' : 'home.networkTrafficTotalLoss'))
const { formatBytes, formatHours } = useFormatters()
const days = ref(30)
const records = ref<DailyTraffic[]>([])
const loading = ref(true)
const failed = ref(false)
const metrics = {
  upload: 'home.networkTrafficReportedUpload',
  download: 'home.networkTrafficReportedDownload',
  uploadAck: 'home.networkTrafficConfirmedUpload',
  downloadAck: 'home.networkTrafficConfirmedDownload',
  seeding: 'home.networkTrafficSeeding',
  downloading: 'home.networkTrafficDownloading',
  averageLoss: 'home.networkTrafficAverageLoss',
  lossRatio: 'home.networkTrafficAverageLossRatio',
  totalLoss: 'home.networkTrafficTotalLoss',
} as const
type Metric = keyof typeof metrics

function chartOption(keys: Metric[], unit: 'bytes' | 'hours' | 'loss') {
  const rows = records.value
  const names = keys.map(key => t(metrics[key]))
  const option = stackedAreaChartOption(rows.map(row => row.day), keys.map((key, index) => ({
    name: names[index]!,
    values: rows.map(row => Number(row.metrics[key] ?? 0) / (unit === 'hours' ? 3600 : 1)),
  })))
  const axes: YAXisComponentOption[] = [{
    type: 'value',
    min: 0,
    axisLabel: { color: '#86909C', formatter: unit === 'hours' ? value => formatHours(value * 3600) : formatBytes },
    splitLine: { lineStyle: { color: '#E5E6EB' } },
  }]
  if (unit === 'loss') {
    axes.push({
      type: 'value',
      min: 0,
      axisLabel: { color: '#86909C', formatter: '{value}%' },
      splitLine: { show: false },
    })
  }
  option.yAxis = axes
  const xAxis = option.xAxis as XAXisComponentOption
  option.xAxis = { ...xAxis, axisLabel: { ...xAxis.axisLabel, formatter: (day: string) => day.slice(5) } }
  option.series = (option.series as LineSeriesOption[]).map(series => ({
    ...series,
    stack: undefined,
    areaStyle: undefined,
    smooth: false,
    symbol: rows.length === 1 ? 'circle' : 'none',
    yAxisIndex: series.name === t(metrics.lossRatio) ? 1 : 0,
  }))
  option.tooltip = {
    trigger: 'axis',
    confine: true,
    formatter: (params: unknown) => sortedTooltipItems(params).map((item, index) => {
      const key = keys[names.indexOf(item.seriesName ?? '')]
      const row = rows[item.dataIndex ?? 0]
      if (!key || !row) return ''
      const metric = row.metrics[key] ?? '0'
      const value = key === 'lossRatio'
        ? `${Number(Number(metric).toFixed(2))}%`
        : unit === 'hours' ? formatHours(metric) : formatBytes(key === 'averageLoss' ? Number(metric) : metric)
      return formatAxisTooltip([{ ...item, axisValueLabel: index === 0 ? row.day : '', axisValue: '' }], () => value)
    }).join(''),
  }
  return option
}

const charts = computed(() => {
  return [
    { visible: props.showReported, title: 'home.networkTrafficReportedChart', keys: ['upload', 'download'], unit: 'bytes' },
    { visible: props.showConfirmed, title: 'home.networkTrafficConfirmedChart', keys: ['uploadAck', 'downloadAck'], unit: 'bytes' },
    { visible: props.showActivity, title: 'home.networkTrafficActivityChart', keys: ['seeding', 'downloading'], unit: 'hours' },
    { visible: props.showAverageLoss, title: 'home.networkTrafficLossChart', keys: ['averageLoss', 'lossRatio'], unit: 'loss' },
    { visible: props.showTotalLoss, title: 'home.networkTrafficTotalLoss', keys: ['totalLoss'], unit: 'bytes' },
  ] satisfies { visible: boolean; title: string; keys: Metric[]; unit: Parameters<typeof chartOption>[1] }[]
})

let latestRequest = 0

async function load() {
  const requestId = ++latestRequest
  loading.value = true
  failed.value = false
  try {
    const rows = await uiApi.networkTraffic(days.value)
    if (requestId === latestRequest) records.value = rows
  } catch {
    if (requestId === latestRequest) failed.value = true
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

watch(days, load, { immediate: true })
</script>

<template>
  <section class="network-traffic" :aria-label="sectionTitle">
    <HomeStatisticsFilters
      v-model="days"
      :title="sectionTitle"
      :description="showTraffic ? t('home.networkTrafficDescription') : undefined"
      :ranges="[30, 14, 7]"
    />
    <a-card v-if="loading || failed || !records.length" class="surface-card network-traffic-state">
      <a-spin v-if="loading" />
      <template v-else-if="failed">
        <p>{{ t('traffic.failed') }}</p>
        <a-button @click="load">{{ t('traffic.refresh') }}</a-button>
      </template>
      <a-empty v-else :description="t('home.statistics.noData')" />
    </a-card>
    <div v-else class="network-traffic-charts">
      <a-card v-for="chart in charts.filter(chart => chart.visible)" :key="chart.title" class="surface-card" :title="showTraffic ? t(chart.title) : undefined">
        <EChart :option="chartOption(chart.keys, chart.unit)" height="260px" />
      </a-card>
    </div>
  </section>
</template>

<style scoped>
.network-traffic {
  display: grid;
  gap: 14px;
}

.network-traffic-charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.network-traffic-charts > :last-child:nth-child(odd) {
  grid-column: 1 / -1;
}

.network-traffic-state {
  text-align: center;
}

@media (max-width: 720px) {
  .network-traffic-charts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
