<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import type { LineSeriesOption } from 'echarts/charts'
import type { TooltipComponentOption } from 'echarts/components'
import { uiApi } from '@/api'
import type { DailyIpFlags } from '@/api/types'
import EChart from '@/components/EChart.vue'
import { stackedAreaChartOption } from '@/utils/charts'

const metrics = [
  'total', 'incoming', 'peerChoked', 'peerUnchoked', 'clientChoked', 'clientUnchoked',
  'notInterested', 'unknown', 'optimisticUnchoke', 'dht', 'pex', 'lsd',
  'trackerOrOther', 'rc4', 'plaintext', 'utp', 'tcp',
] as const

const { t, n } = useI18n()
const utcToday = () => new Date().toISOString().slice(0, 10)
const range = ref([dayjs(utcToday()).subtract(6, 'day').format('YYYY-MM-DD'), utcToday()])
const records = ref<DailyIpFlags[]>([])
const loading = ref(false)
const failed = ref(false)
const invalidRange = computed(() => {
  const [from, to] = range.value
  return !from || !to || to > utcToday() || to < from || dayjs(to).diff(dayjs(from), 'day') >= 90
})

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

function setDate(index: number, value: unknown) {
  if (typeof value !== 'string') return
  range.value = range.value.map((date, key) => key === index ? value : date)
}

function disableFutureDate(date?: Date) {
  return !!date && dayjs(date).format('YYYY-MM-DD') > utcToday()
}

async function load() {
  const id = ++requestId
  records.value = []
  failed.value = false
  loading.value = !invalidRange.value
  if (!loading.value) return

  try {
    const result = await uiApi.homeIpFlags(range.value[0]!, range.value[1]!)
    if (id === requestId) records.value = result
  } catch {
    if (id === requestId) failed.value = true
  } finally {
    if (id === requestId) loading.value = false
  }
}

watch(range, load, { immediate: true })
</script>

<template>
  <a-card class="surface-card">
    <div class="ip-flags-header">
      <div>
        <h2>{{ t('home.ipFlags.title') }}</h2>
        <p>{{ t('home.ipFlags.description') }}</p>
      </div>
      <div class="ip-flags-range">
        <a-range-picker
          v-model="range"
          value-format="YYYY-MM-DD"
          :allow-clear="false"
          :disabled-date="disableFutureDate"
          :aria-label="t('home.statistics.range')"
        />
      </div>
      <div class="ip-flags-mobile-range">
        <a-date-picker
          :model-value="range[0]"
          value-format="YYYY-MM-DD"
          :allow-clear="false"
          :disabled-date="disableFutureDate"
          :aria-label="t('home.ipFlags.from')"
          @change="(value: unknown) => setDate(0, value)"
        />
        <a-date-picker
          :model-value="range[1]"
          value-format="YYYY-MM-DD"
          :allow-clear="false"
          :disabled-date="disableFutureDate"
          :aria-label="t('home.ipFlags.to')"
          @change="(value: unknown) => setDate(1, value)"
        />
      </div>
    </div>
    <a-alert v-if="invalidRange" type="warning">{{ t('home.ipFlags.invalidRange') }}</a-alert>
    <a-result v-else-if="failed" status="error" :title="t('home.ipFlags.failed')">
      <template #extra><a-button @click="load">{{ t('common.retry') }}</a-button></template>
    </a-result>
    <div v-else-if="loading" class="ip-flags-status" role="status">
      <a-spin :tip="t('common.loading')" />
    </div>
    <a-empty v-else-if="records.length === 0" :description="t('home.ipFlags.empty')" />
    <EChart v-else :option="option" height="390px" />
  </a-card>
</template>

<style scoped>
.ip-flags-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 24px;
  margin-bottom: 16px;
}

.ip-flags-header h2 {
  margin: 0;
  font-size: 18px;
}

.ip-flags-header p {
  margin: 4px 0 0;
  color: var(--color-text-2);
}

.ip-flags-range {
  max-width: 100%;
}

.ip-flags-mobile-range {
  display: none;
}

@media (max-width: 720px) {
  .ip-flags-range {
    display: none;
  }

  .ip-flags-mobile-range {
    display: grid;
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }
}

.ip-flags-status {
  display: grid;
  min-height: 160px;
  place-items: center;
}
</style>
