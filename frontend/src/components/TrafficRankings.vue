<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EChartsCoreOption } from 'echarts/core'
import { homeTrafficRankings } from '@/api'
import type { TrafficRankingDatum, TrafficRankingsResponse } from '@/api/types'
import EChart from '@/components/EChart.vue'
import { useFormatters } from '@/utils/format'
import { CHART_COLORS, CHART_TEXT_STYLE } from '@/utils/charts'

const props = defineProps<{ days: number }>()
const { t } = useI18n()
const { formatBytes } = useFormatters()
const data = ref<TrafficRankingsResponse | null>(null)
const loading = ref(false)
const failed = ref(false)
const pages = ref([1, 1])
let requestId = 0
async function load() {
  const id = ++requestId
  loading.value = true
  failed.value = false
  data.value = null
  pages.value = [1, 1]
  try {
    const result = await homeTrafficRankings(props.days)
    if (id === requestId) data.value = result
  } catch {
    if (id === requestId) failed.value = true
  } finally {
    if (id === requestId) loading.value = false
  }
}
watch(() => props.days, load, { immediate: true })
const charts = computed(() => [
  { title: t('home.statistics.trafficPeerIds'), rows: data.value?.peerIds ?? [] },
  { title: t('home.statistics.trafficClients'), rows: data.value?.clientNames ?? [] },
])
function option(rows: TrafficRankingDatum[], index: number): EChartsCoreOption {
  const visible = rows.slice(((pages.value[index] ?? 1) - 1) * 10, (pages.value[index] ?? 1) * 10)
  const maximum = rows.reduce((max, row) => {
    const total = BigInt(row.uploadBytes) + BigInt(row.downloadBytes)
    return total > max ? total : max
  }, 0n)
  let divisor = 1n
  while (maximum / divisor > 1_000_000_000n) divisor *= 1024n
  const directions = [t('home.statistics.trafficToPeer'), t('home.statistics.trafficFromPeer')]
  return {
    color: CHART_COLORS,
    textStyle: CHART_TEXT_STYLE,
    grid: { left: 8, right: 24, top: 42, bottom: 35, containLabel: true },
    legend: { data: directions, type: 'scroll', top: 0 },
    tooltip: {
      trigger: 'item', confine: true, renderMode: 'richText',
      formatter: (params: unknown) => {
        const item = params as { name: string; seriesName: string; data: { bytes: string } }
        return item.name + '\n' + item.seriesName + ': ' + formatBytes(item.data.bytes)
      },
    },
    xAxis: { type: 'value', axisLabel: { formatter: (value: number) => formatBytes((BigInt(Math.round(value)) * divisor).toString()) } },
    yAxis: { type: 'category', inverse: true, data: visible.map(row => row.name === 'Unknown' ? t('home.statistics.unknown') : row.name), axisLabel: { width: 120, overflow: 'truncate' } },
    series: directions.map((name, direction) => ({
      name, type: 'bar', barMaxWidth: 13,
      data: visible.map(row => {
        const bytes = direction === 0 ? row.uploadBytes : row.downloadBytes
        const value = BigInt(bytes)
        return { value: Number(value / divisor) + Number(value % divisor) / Number(divisor), bytes }
      }),
    })),
  }
}
</script>

<template>
  <div>
    <p class="rankings-note">{{ t('home.statistics.trafficRankingsNote') }}</p>
    <p v-if="data?.generatedAt" class="rankings-note">{{ t('home.statistics.updatedAt', { time: new Date(data.generatedAt).toLocaleString() }) }}</p>
    <p v-if="data && !data.generatedAt" class="rankings-note">{{ t('home.trafficNeverUpdated') }}</p>
    <a-alert v-if="failed" type="warning">{{ t('home.statistics.loadFailed') }} <a-button size="mini" @click="load">{{ t('common.retry') }}</a-button></a-alert>
    <a-row :gutter="[16, 16]">
      <a-col v-for="(chart, index) in charts" :key="index" :xs="24" :lg="12">
        <a-card class="surface-card" :title="chart.title">
          <EChart v-if="loading || chart.rows.length" :option="option(chart.rows, index)" height="520px" :loading="loading" :loading-text="t('common.loading')" />
          <a-empty v-else />
          <a-pagination v-if="chart.rows.length > 10" v-model:current="pages[index]" :total="chart.rows.length" :page-size="10" size="small" simple />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style scoped>
.rankings-note { color: var(--color-text-3); font-size: 12px; }
</style>
