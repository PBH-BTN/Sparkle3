<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import CareerMetricCard from '@/components/CareerMetricCard.vue'
import { uiApi } from '@/api'
import { useI18n } from 'vue-i18n'
import type { LifetimeTraffic } from '@/api/types'
import { useFormatters } from '@/utils/format'

const props = defineProps<{ userId: string; traffic: LifetimeTraffic | null; hidden?: boolean }>()
const career = ref<Awaited<ReturnType<typeof uiApi.career>>>(null)
const failed = ref(false)
watch(() => props.userId, async (uid) => {
  career.value = null
  failed.value = false
  try { career.value = await uiApi.career(uid) } catch { failed.value = true }
}, { immediate: true })
const contributionGroups = [
  { total: 'ban', recent: 'banRecent', color: 'red', unit: 'records' },
  { total: 'feature', recent: 'featureRecent', color: 'orange', unit: 'records' },
  { total: 'swarm', recent: 'swarmRecent', color: 'purple', unit: 'peers' },
]
function counterValue(key: string) {
  const value = career.value?.metrics[key] || '0'
  return key === 'seeding' || key === 'downloading'
    ? (Number(value) / 3600).toFixed(2)
    : BigInt(value).toLocaleString()
}
const { t } = useI18n()
const { formatBytes, formatDate } = useFormatters()
const metrics = [
  { field: 'sentTrafficSelfReport', label: 'career.upload', color: 'green', hint: 'upload' },
  { field: 'receivedTrafficSelfReport', label: 'career.download', color: 'cyan', hint: 'download' },
  { field: 'sentTrafficOtherAck', label: 'career.uploadAck', color: 'green', hint: 'confirmed' },
  { field: 'receivedTrafficOtherAck', label: 'career.downloadAck', color: 'cyan', hint: 'confirmed' },
] as const
const trafficCards = computed(() => props.traffic ? metrics.map(metric => {
  const formatted = formatBytes(props.traffic?.[metric.field])
  const split = formatted.lastIndexOf(' ')
  return { ...metric, value: formatted.slice(0, split), unit: formatted.slice(split + 1) }
}) : [])
const empty = computed(() => props.traffic && metrics.every(({ field }) => props.traffic?.[field] === '0'))
</script>

<template>
  <section class="lifetime-traffic-card" :aria-label="t('lifetimeTraffic.title')">
    <h3 class="career-title">{{ t('lifetimeTraffic.title') }}</h3>
    <p class="traffic-note">{{ t('career.disclaimer') }}</p>
    <p class="traffic-note">{{ t('career.method') }}</p>
    <p v-if="failed" class="traffic-note">{{ t('lifetimeTraffic.unavailable') }}</p>
    <div v-if="career" class="contribution-grid">
      <div class="metric-stack metric-stack--intro">
        <CareerMetricCard :label="t('career.days')" :value="counterValue('days')" :unit="t('careerUnits.days')" color="blue" />
        <CareerMetricCard :label="t('career.seeding')" :value="counterValue('seeding')" :unit="t('careerUnits.hours')" color="green" />
      </div>
      <div v-for="group in contributionGroups" :key="group.total" class="metric-stack">
        <CareerMetricCard :label="t('career.' + group.total)" :value="counterValue(group.total)" :unit="t('careerUnits.' + group.unit)" :color="group.color" />
        <CareerMetricCard :label="t('career.' + group.recent)" :value="counterValue(group.recent)" :unit="t('careerUnits.' + group.unit)" :color="group.color" />
      </div>
    </div>
    <div v-if="career || traffic" class="other-metrics">
      <template v-if="career">
        <CareerMetricCard :label="t('career.downloading')" :value="counterValue('downloading')" :unit="t('careerUnits.hours')" color="cyan" />
        <CareerMetricCard :label="t('career.observation')" :value="counterValue('observation')" :unit="t('careerUnits.times')" color="blue" />
      </template>
      <CareerMetricCard v-for="metric in trafficCards" :key="metric.field" :label="t(metric.label)" :value="metric.value" :unit="metric.unit" :color="metric.color" />
    </div>
    <p v-if="career?.lastObservedAt" class="traffic-note">{{ t('career.latest', { time: formatDate(career.lastObservedAt), name: career.observerName || t('common.anonymous') }) }}</p>
    <template v-if="traffic">
      <p v-if="empty" class="traffic-note">{{ t('lifetimeTraffic.empty') }}</p>
      <p class="traffic-note">
        {{ t(traffic.historyPartial ? 'lifetimeTraffic.partialSince' : 'lifetimeTraffic.since', {
          time: formatDate(traffic.trackedSince),
        }) }}
      </p>
      <p v-if="traffic.historyPartial" class="traffic-note">
        {{ t('lifetimeTraffic.partialHistory') }}
      </p>
      <p class="traffic-note">
        {{ t('lifetimeTraffic.updatedAt', { time: formatDate(traffic.updatedAt) }) }}
      </p>
    </template>
    <p v-else class="traffic-note">
      {{ t(hidden ? 'lifetimeTraffic.hidden' : 'lifetimeTraffic.unavailable') }}
    </p>
  </section>
</template>

<style scoped>
.career-title {
  margin: 0 0 10px;
  color: var(--color-text-1);
  font-size: 18px;
  font-weight: 600;
}
.contribution-grid,
.other-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 20px 0 16px;
}
.contribution-grid { grid-template-rows: repeat(2, minmax(158px, auto)); }
.metric-stack {
  grid-row: span 2;
  display: grid;
  gap: 14px;
  min-width: 0;
  grid-template-rows: subgrid;
}
.traffic-note {
  margin: 4px 0 0;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.7;
}
@media (max-width: 980px) {
  .contribution-grid,
  .other-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .metric-stack--intro { grid-row: auto; grid-column: 1 / -1; grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: auto; }
}
@media (max-width: 720px) {
  .contribution-grid,
  .other-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .metric-stack { gap: 10px; }
}
@media (max-width: 360px) {
  .contribution-grid,
  .other-metrics,
  .metric-stack--intro { grid-template-columns: 1fr; }
}
</style>
