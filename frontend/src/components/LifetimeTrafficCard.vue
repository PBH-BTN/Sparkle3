<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
const counters = [
  { key: 'days', color: 'blue', hint: 'member' },
  { key: 'ban', color: 'red', hint: 'total' },
  { key: 'feature', color: 'orange', hint: 'total' },
  { key: 'swarm', color: 'purple', hint: 'total' },
  { key: 'banRecent', color: 'red', hint: 'recent' },
  { key: 'featureRecent', color: 'orange', hint: 'recent' },
  { key: 'swarmRecent', color: 'purple', hint: 'recent' },
  { key: 'seeding', color: 'green', hint: 'torrentTime' },
  { key: 'downloading', color: 'cyan', hint: 'torrentTime' },
  { key: 'observation', color: 'blue', hint: 'observed' },
]
const { t } = useI18n()
const { formatBytes, formatDate } = useFormatters()
const metrics = [
  { field: 'sentTrafficSelfReport', label: 'career.upload', color: 'green', hint: 'upload' },
  { field: 'receivedTrafficSelfReport', label: 'career.download', color: 'cyan', hint: 'download' },
  { field: 'sentTrafficOtherAck', label: 'career.uploadAck', color: 'green', hint: 'confirmed' },
  { field: 'receivedTrafficOtherAck', label: 'career.downloadAck', color: 'cyan', hint: 'confirmed' },
] as const
const empty = computed(() => props.traffic && metrics.every(({ field }) => props.traffic?.[field] === '0'))
</script>

<template>
  <section class="lifetime-traffic-card" :aria-label="t('lifetimeTraffic.title')">
    <h3 class="career-title">{{ t('lifetimeTraffic.title') }}</h3>
    <p class="traffic-note">{{ t('career.disclaimer') }}</p>
    <p class="traffic-note">{{ t('career.method') }}</p>
    <p v-if="failed" class="traffic-note">{{ t('lifetimeTraffic.unavailable') }}</p>
    <dl v-if="career || traffic" class="traffic-metrics">
      <template v-if="career">
        <div v-for="metric in counters" :key="metric.key" class="traffic-metric" :class="'traffic-metric--' + metric.color">
          <dt>{{ t('career.' + metric.key) }}</dt>
          <dd>{{ metric.key === 'seeding' || metric.key === 'downloading' ? (Number(career.metrics[metric.key] || '0') / 3600).toFixed(2) + ' h' : BigInt(career.metrics[metric.key] || '0').toLocaleString() }}</dd>
          <div class="metric-hint">{{ t('careerCard.' + metric.hint) }}</div>
        </div>
      </template>
      <template v-if="traffic">
        <div v-for="metric in metrics" :key="metric.field" class="traffic-metric" :class="'traffic-metric--' + metric.color">
          <dt>{{ t(metric.label) }}</dt>
          <dd :title="traffic[metric.field] + ' ' + t('units.bytes')">{{ formatBytes(traffic[metric.field]) }}</dd>
          <div class="metric-hint">{{ t('careerCard.' + metric.hint) }}</div>
        </div>
      </template>
    </dl>
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
.career-title { margin: 0 0 10px; color: var(--color-text-1); font-size: 18px; font-weight: 600; }
.traffic-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 20px 0 16px; }
.traffic-metric { position: relative; display: flex; flex-direction: column; min-width: 0; min-height: 158px; padding: 20px; overflow: hidden; background: var(--color-bg-2); border: 1px solid var(--color-border-2); border-radius: 12px; }
.traffic-metric::after { position: absolute; right: -28px; bottom: -36px; width: 108px; height: 108px; border-radius: 50%; background: currentColor; opacity: 0.07; content: ''; pointer-events: none; }
.traffic-metric dt { color: var(--color-text-3); font-size: 13px; line-height: 1.6; }
.traffic-metric dd { z-index: 1; margin: 10px 0 24px; font-size: clamp(24px, 2.4vw, 30px); font-weight: 700; line-height: 1.25; letter-spacing: -0.03em; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
.metric-hint { z-index: 1; margin-top: auto; color: var(--color-text-3); font-size: 12px; line-height: 1.6; }
.traffic-note { margin: 4px 0 0; color: var(--color-text-3); font-size: 13px; line-height: 1.7; }
.traffic-metric--red { color: rgb(var(--red-6)); }
.traffic-metric--blue { color: rgb(var(--blue-6)); }
.traffic-metric--green { color: rgb(var(--green-6)); }
.traffic-metric--cyan { color: #0891b2; }
.traffic-metric--purple { color: #7c3aed; }
.traffic-metric--orange { color: rgb(var(--orange-6)); }
@media (max-width: 980px) { .traffic-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 720px) { .traffic-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } .traffic-metric { padding: 16px; min-height: 148px; } }
@media (max-width: 360px) { .traffic-metrics { grid-template-columns: 1fr; } }
</style>
