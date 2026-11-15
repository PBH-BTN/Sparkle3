<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  title: string
  description?: string
  ranges: readonly (1 | 3 | 7 | 14 | 30)[]
  calendarDays?: boolean
}>()

const range = defineModel<number>({ required: true })
const { t } = useI18n()
const labels = {
  1: 'home.last24Hours',
  3: 'home.statistics.last3Days',
  7: 'home.last7Days',
  14: 'home.last14Days',
  30: 'home.last30Days',
}
</script>

<template>
  <div class="statistics-heading">
    <h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
  </div>
  <a-card class="surface-card statistics-filters">
    <div class="statistics-filter-row">
      <div class="statistics-filter-field">
        <span>{{ t('home.statistics.range') }}</span>
        <a-select v-model="range" :aria-label="t('home.statistics.range')">
          <a-option v-for="item in ranges" :key="item" :value="item">
            {{ t(item === 1 && calendarDays ? 'home.statistics.todayUtc' : labels[item]) }}
          </a-option>
        </a-select>
      </div>
      <slot />
      <div v-if="$slots.status" class="statistics-filter-status">
        <slot name="status" />
      </div>
    </div>
  </a-card>
</template>

<style scoped>
.statistics-heading h2 {
  margin: 8px 0 3px;
  color: var(--color-text-1);
  font-size: 20px;
  font-weight: 600;
}

.statistics-heading p {
  margin: 0;
  color: var(--color-text-3);
  line-height: 1.6;
}

.statistics-filters :deep(.arco-card-body) {
  padding: 14px 18px;
}

.statistics-filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px 16px;
}

.statistics-filter-field,
.statistics-filter-row :slotted(.statistics-filter-field) {
  display: grid;
  gap: 6px;
  min-width: 170px;
  color: var(--color-text-3);
  font-size: 12px;
}

.statistics-filter-status {
  margin-inline-start: auto;
  align-self: center;
  color: var(--color-text-3);
  font-size: 12px;
}

@media (max-width: 720px) {
  .statistics-filter-field,
  .statistics-filter-row :slotted(.statistics-filter-field) {
    width: 100%;
    min-width: 0;
  }

  .statistics-filter-status {
    width: 100%;
    margin-inline-start: 0;
  }
}
</style>
