<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LineSeriesOption } from 'echarts/charts'
import type { XAXisComponentOption, YAXisComponentOption } from 'echarts'
import { uiApi } from '@/api'
import type { DailyTraffic } from '@/api/types'
import EChart from '@/components/EChart.vue'
import { stackedAreaChartOption } from '@/utils/charts'
import { formatAxisTooltip, sortedTooltipItems } from '@/utils/chartTooltip'
import { useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatBytes, formatHours } = useFormatters()
const days = ref(30)
const records = ref<DailyTraffic[]>([])
const loading = ref(true)
const failed = ref(false)
const metrics = {
  upload: 'career.upload',
  download: 'career.download',
  uploadAck: 'career.uploadAck',
  downloadAck: 'career.downloadAck',
  seeding: 'career.seeding',
  downloading: 'career.downloading',
  loss: 'home.networkTrafficAverageLoss',
  lossRatio: 'home.networkTrafficAverageLossRatio',
} as const
type Metric = keyof typeof metrics

const cumulativeRows = computed(() => {
  const totals = { upload: 0n, download: 0n, uploadAck: 0n, downloadAck: 0n, loss: 0n }
  const keys = Object.keys(totals) as (keyof typeof totals)[]
  let seeding = 0
  let downloading = 0
  let lossRatios = 0
  return records.value.slice(-days.value).map(({ day, metrics }, index) => {
    for (const key of keys) totals[key] += BigInt(metrics[key] ?? '0')
    seeding += Number(metrics.seeding ?? '0')
    downloading += Number(metrics.downloading ?? '0')
    const upload = Number(metrics.upload ?? '0')
    if (upload > 0) lossRatios += Number(metrics.loss ?? '0') / upload
    const elapsedDays = index + 1
    return {
      day,
      ...totals,
      seeding,
      downloading,
      loss: (totals.loss + BigInt(Math.floor(elapsedDays / 2))) / BigInt(elapsedDays),
      lossRatio: lossRatios * 100 / elapsedDays,
    }
  })
})

function chartOption(keys: Metric[], unit: 'bytes' | 'hours' | 'loss') {
  const rows = cumulativeRows.value
  const names = keys.map(key => t(metrics[key]))
  const option = stackedAreaChartOption(rows.map(row => row.day), keys.map((key, index) => ({
    name: names[index]!,
    values: rows.map(row => Number(row[key]) / (unit === 'hours' ? 3600 : 1)),
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
      const value = key === 'lossRatio'
        ? `${Number(row[key].toFixed(2))}%`
        : unit === 'hours' ? formatHours(Number(row[key])) : formatBytes(row[key])
      return formatAxisTooltip([{ ...item, axisValueLabel: index === 0 ? row.day : '', axisValue: '' }], () => value)
    }).join(''),
  }
  return option
}

const charts = computed(() => [
  { title: 'traffic.chartReported', option: chartOption(['upload', 'download'], 'bytes') },
  { title: 'traffic.chartConfirmed', option: chartOption(['uploadAck', 'downloadAck'], 'bytes') },
  { title: 'traffic.chartActivity', option: chartOption(['seeding', 'downloading'], 'hours') },
  { title: 'home.networkTrafficLossChart', option: chartOption(['loss', 'lossRatio'], 'loss') },
])

async function load() {
  loading.value = true
  failed.value = false
  try {
    records.value = await uiApi.networkTraffic()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="network-traffic" :aria-label="t('home.networkTrafficTitle')">
    <div class="network-traffic-heading">
      <div>
        <h2>{{ t('home.networkTrafficTitle') }}</h2>
        <p>{{ t('home.networkTrafficDescription') }}</p>
      </div>
      <a-radio-group v-model="days" type="button" :aria-label="t('home.statistics.range')">
        <a-radio :value="30">{{ t('home.last30Days') }}</a-radio>
        <a-radio :value="14">{{ t('home.last14Days') }}</a-radio>
        <a-radio :value="7">{{ t('home.last7Days') }}</a-radio>
      </a-radio-group>
    </div>
    <a-card v-if="loading || failed || !records.length" class="surface-card network-traffic-state">
      <a-spin v-if="loading" />
      <template v-else-if="failed">
        <p>{{ t('traffic.failed') }}</p>
        <a-button @click="load">{{ t('traffic.refresh') }}</a-button>
      </template>
      <a-empty v-else :description="t('home.statistics.noData')" />
    </a-card>
    <div v-else class="network-traffic-charts">
      <a-card v-for="chart in charts" :key="chart.title" class="surface-card" :title="t(chart.title)">
        <EChart :option="chart.option" height="260px" />
      </a-card>
    </div>
  </section>
</template>

<style scoped>
.network-traffic {
  display: grid;
  gap: 16px;
}

.network-traffic-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.network-traffic-heading h2 {
  margin: 0 0 6px;
  color: var(--color-text-1);
  font-size: 22px;
}

.network-traffic-heading p {
  margin: 0;
  color: var(--color-text-3);
}

.network-traffic-charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
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
