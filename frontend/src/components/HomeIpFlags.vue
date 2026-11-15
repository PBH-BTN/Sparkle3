<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import type { LineSeriesOption } from 'echarts/charts'
import type { TooltipComponentOption } from 'echarts/components'
import { uiApi } from '@/api'
import type { DailyIpFlags } from '@/api/types'
import EChart from '@/components/EChart.vue'
import HomeStatisticsFilters from '@/components/HomeStatisticsFilters.vue'
import { stackedAreaChartOption } from '@/utils/charts'

const metrics = [
  'total', 'incoming', 'peerChoked', 'peerUnchoked', 'clientChoked', 'clientUnchoked',
  'notInterested', 'unknown', 'optimisticUnchoke', 'dht', 'pex', 'lsd',
  'trackerOrOther', 'rc4', 'plaintext', 'utp', 'tcp',
] as const

const { t, n } = useI18n()
const days = ref(7)
const range = ref<[string, string]>(dateRange(days.value))
const records = ref<DailyIpFlags[]>([])
const loading = ref(false)
const failed = ref(false)

function dateRange(days: number): [string, string] {
  const end = new Date()
  const to = end.toISOString().slice(0, 10)
  end.setUTCDate(end.getUTCDate() - days + 1)
  return [end.toISOString().slice(0, 10), to]
}

const option = computed(() => {
  const [from, to] = range.value
  const start = dayjs(from)
  const days = Array.from({ length: dayjs(to).diff(start, 'day') + 1 },
    (_, index) => start.add(index, 'day').format('YYYY-MM-DD'))
  const byDay = new Map(records.value.map(row => [row.day, row.counts]))
  const names = metrics.map(key => t(`home.ipFlags.metrics.${key}`))
  const chart = stackedAreaChartOption(days, names.map(name => ({ name, values: [] })))
  chart.tooltip = {
    ...chart.tooltip as TooltipComponentOption,
    valueFormatter: (value: unknown) => typeof value === 'number' ? n(value) : String(value ?? ''),
  }
  chart.series = (chart.series as LineSeriesOption[]).map((series, index) => ({
    ...series,
    stack: undefined,
    smooth: false,
    symbol: 'circle',
    symbolSize: 4,
    areaStyle: index === 0 ? { opacity: 0.12 } : undefined,
    data: days.map(day => {
      const counts = byDay.get(day)
      return counts ? counts[metrics[index]!] ?? 0 : null
    }),
  }))
  return chart
})

let requestId = 0

async function load() {
  const id = ++requestId
  range.value = dateRange(days.value)
  const [from, to] = range.value
  records.value = []
  failed.value = false
  loading.value = true

  try {
    const result = await uiApi.homeIpFlags(from, to)
    if (id === requestId) records.value = result
  } catch {
    if (id === requestId) failed.value = true
  } finally {
    if (id === requestId) loading.value = false
  }
}

watch(days, load, { immediate: true })
</script>

<template>
  <section class="ip-flags">
    <HomeStatisticsFilters
      v-model="days"
      :title="t('home.ipFlags.title')"
      :description="t('home.ipFlags.description')"
      :ranges="[30, 14, 7, 3, 1]"
      calendar-days
    />
    <a-card class="surface-card">
      <a-result v-if="failed" status="error" :title="t('home.ipFlags.failed')">
        <template #extra><a-button @click="load">{{ t('common.retry') }}</a-button></template>
      </a-result>
      <div v-else-if="loading" class="ip-flags-status" role="status">
        <a-spin :tip="t('common.loading')" />
      </div>
      <a-empty v-else-if="records.length === 0" :description="t('home.ipFlags.empty')" />
      <EChart v-else :option="option" height="390px" />
    </a-card>
  </section>
</template>

<style scoped>
.ip-flags {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ip-flags-status {
  display: grid;
  min-height: 160px;
  place-items: center;
}
</style>
