<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LifetimeTraffic } from '@/api/types'
import { useFormatters } from '@/utils/format'

const props = defineProps<{ traffic: LifetimeTraffic | null; hidden?: boolean }>()
const { t } = useI18n()
const { formatBytes, formatDate } = useFormatters()
const metrics = [
  { field: 'sentTrafficSelfReport', label: 'ranking.sentSelf' },
  { field: 'receivedTrafficSelfReport', label: 'ranking.receivedSelf' },
  { field: 'sentTrafficOtherAck', label: 'ranking.sentAck' },
  { field: 'receivedTrafficOtherAck', label: 'ranking.receivedAck' },
] as const
const empty = computed(() => props.traffic && metrics.every(({ field }) => props.traffic?.[field] === '0'))
</script>

<template>
  <a-card class="surface-card lifetime-traffic-card" :title="t('lifetimeTraffic.title')">
    <template v-if="traffic">
      <dl class="traffic-metrics">
        <div v-for="metric in metrics" :key="metric.field" class="traffic-metric">
          <dt>{{ t(metric.label) }}</dt>
          <dd :title="`${traffic[metric.field]} ${t('units.bytes')}`">
            {{ formatBytes(traffic[metric.field]) }}
          </dd>
        </div>
      </dl>
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
  </a-card>
</template>

<style scoped>
.traffic-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  margin: 0 0 16px;
}

.traffic-metric {
  min-width: 0;
}

.traffic-metric dt,
.traffic-note {
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.6;
}

.traffic-metric dd {
  margin: 6px 0 0;
  color: var(--color-text-1);
  font-size: 22px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.traffic-note {
  margin: 4px 0 0;
}

@media (max-width: 980px) {
  .traffic-metrics {
    gap: 16px;
  }

  .traffic-metric dd {
    font-size: 20px;
  }
}

@media (max-width: 720px) {
  .traffic-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
