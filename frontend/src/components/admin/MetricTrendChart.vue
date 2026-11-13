<script setup lang="ts">
import { computed, useId } from 'vue'

const props = defineProps<{
  points: Array<{ timestamp: number; value: number }>
  emptyText: string
}>()

const gradientId = `metric-gradient-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
const width = 320
const height = 92
const padding = 6

const normalizedPoints = computed(() =>
  props.points.map((point) => ({
    ...point,
    value: Math.max(0, Math.min(1, point.value)),
  })),
)

const path = computed(() => {
  const points = normalizedPoints.value
  if (points.length === 0) return ''
  const step = points.length === 1 ? 0 : (width - padding * 2) / (points.length - 1)
  return points
    .map((point, index) => {
      const x = padding + step * index
      const y = padding + (1 - point.value) * (height - padding * 2)
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
    })
    .join(' ')
})

const areaPath = computed(() => {
  if (!path.value) return ''
  const lastX = padding + (width - padding * 2)
  return `${path.value} L ${lastX} ${height - padding} L ${padding} ${height - padding} Z`
})

const startLabel = computed(() =>
  props.points[0]
    ? new Intl.DateTimeFormat(undefined, { month: '2-digit', day: '2-digit' }).format(
        new Date(props.points[0].timestamp),
      )
    : '',
)

const endLabel = computed(() =>
  props.points.length
    ? new Intl.DateTimeFormat(undefined, { month: '2-digit', day: '2-digit' }).format(
        new Date(props.points[props.points.length - 1].timestamp),
      )
    : '',
)
</script>

<template>
  <div class="trend-chart">
    <a-empty v-if="points.length === 0" :description="emptyText" />
    <template v-else>
      <svg :viewBox="`0 0 ${width} ${height}`" preserveAspectRatio="none" role="img">
        <defs>
          <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="rgb(var(--primary-5))" stop-opacity="0.24" />
            <stop offset="100%" stop-color="rgb(var(--primary-5))" stop-opacity="0" />
          </linearGradient>
        </defs>
        <path :d="areaPath" :fill="`url(#${gradientId})`" />
        <path
          :d="path"
          fill="none"
          stroke="rgb(var(--primary-6))"
          stroke-width="2"
          vector-effect="non-scaling-stroke"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      <div class="chart-axis">
        <span>{{ startLabel }}</span>
        <span>{{ endLabel }}</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.trend-chart {
  min-height: 116px;
}

svg {
  display: block;
  width: 100%;
  height: 92px;
  overflow: visible;
}

.chart-axis {
  display: flex;
  justify-content: space-between;
  margin-top: 2px;
  color: var(--color-text-3);
  font-size: 10px;
}

:deep(.arco-empty) {
  padding: 18px 0;
}
</style>
