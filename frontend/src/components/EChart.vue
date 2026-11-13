<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type EChartsCoreOption, type EChartsType } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([
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
  chart?.setOption(props.option, true)
}

function renderLoading() {
  if (!chart) return
  if (props.loading) {
    chart.showLoading('default', {
      text: props.loadingText,
      color: '#165DFF',
      textColor: '#4E5969',
      maskColor: 'rgba(255, 255, 255, 0.72)',
    })
  } else {
    chart.hideLoading()
  }
}

onMounted(() => {
  if (!container.value) return
  chart = init(container.value)
  renderOption()
  renderLoading()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(container.value)
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
