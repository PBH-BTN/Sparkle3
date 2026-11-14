<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type EChartsCoreOption, type EChartsType } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import type { TooltipComponentOption } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useTheme } from '@/composables/useTheme'
import { formatAxisTooltip, formatItemTooltip, sortedTooltipItems } from '@/utils/chartTooltip'
const { isDark } = useTheme()

// Canvas options cannot resolve CSS variables; map shared neutral colors at render time.
function themedOption(value: unknown): unknown {
  if (typeof value === 'string' && isDark.value) {
    const colors: Record<string, string> = {
      '#4e5969': '#c9cdd4', '#86909c': '#a9aeb8', '#c9cdd4': '#484849',
      '#e5e6eb': '#373739', '#ffffff': '#232324',
    }
    return colors[value.toLowerCase()] ?? value
  }
  if (Array.isArray(value)) return value.map(themedOption)
  if (value && Object.getPrototypeOf(value) === Object.prototype) {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, themedOption(item)]))
  }
  return value
}

use([
  BarChart,
  PieChart,
  LineChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer,
])

const props = withDefaults(
  defineProps<{
    option: EChartsCoreOption
    height?: string
    loading?: boolean
    loadingText?: string
  }>(),
  {
    height: '320px',
    loading: false,
    loadingText: 'Loading…',
  },
)

const container = ref<HTMLDivElement>()
let chart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null

function renderOption() {
  const option = themedOption(props.option) as EChartsCoreOption
  const tooltip = (option.tooltip ?? {}) as TooltipComponentOption
  const formatter = tooltip.formatter
  chart?.setOption({
    ...option,
    backgroundColor: 'transparent',
    tooltip: {
      ...tooltip,
      order: 'valueDesc',
      formatter: (params: unknown, ...args: unknown[]) => {
        const items = sortedTooltipItems(params)
        if (!items.length) return ''
        if (typeof formatter === 'function') {
          return (formatter as (...values: unknown[]) => unknown)(Array.isArray(params) ? items : items[0], ...args)
        }
        return tooltip.trigger === 'axis'
          ? formatAxisTooltip(items, tooltip.valueFormatter)
          : formatItemTooltip(items[0], tooltip.valueFormatter)
      },
    },
  }, true)
}

function renderLoading() {
  if (!chart) return
  if (props.loading) {
    chart.showLoading('default', {
      text: props.loadingText,
      color: '#165DFF',
      textColor: isDark.value ? '#c9cdd4' : '#4E5969',
      maskColor: isDark.value ? 'rgba(35, 35, 36, 0.72)' : 'rgba(255, 255, 255, 0.72)',
    })
  } else {
    chart.hideLoading()
  }
}

onMounted(() => {
  if (!container.value) return
  chart = init(container.value, isDark.value ? 'dark' : undefined, {})
  renderOption()
  renderLoading()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(container.value)
})

watch(isDark, () => {
  if (!container.value || !chart) return
  chart.dispose()
  chart = init(container.value, isDark.value ? 'dark' : undefined)
  renderOption()
  renderLoading()
})
watch(() => props.option, renderOption, { deep: true })
watch(() => props.loading, renderLoading)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="container" class="echart" :style="{ height }" />
</template>

<style scoped>
.echart {
  width: 100%;
  min-width: 0;
}
</style>
