import type { EChartsCoreOption } from 'echarts/core'

export const CHART_COLORS = [
  '#165DFF',
  '#14C9C9',
  '#F7BA1E',
  '#F53F3F',
  '#722ED1',
  '#00B42A',
  '#FF7D00',
  '#3491FA',
  '#D91AD9',
  '#0FC6C2',
  '#FADC19',
  '#CB2634',
  '#B37FEB',
  '#9FDB1D',
  '#6C5CE7',
  '#86909C',
]

export const CHART_TEXT_STYLE = {
  fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", sans-serif',
}

export interface ChartDatum {
  name: string
  value: number
}

export interface StackedChartSeries {
  name: string
  values: number[]
}

export function distributionPieOption(
  seriesName: string,
  data: ChartDatum[],
): EChartsCoreOption {
  return {
    color: CHART_COLORS,
    textStyle: CHART_TEXT_STYLE,
    tooltip: {
      trigger: 'item',
      confine: true,
      valueFormatter: (value: unknown) => Number(value).toLocaleString(),
    },
    legend: {
      type: 'scroll',
      bottom: 0,
      left: 8,
      right: 8,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: {
        color: '#4E5969',
        fontSize: 11,
      },
    },
    series: [
      {
        name: seriesName,
        type: 'pie',
        radius: ['40%', '64%'],
        center: ['50%', '43%'],
        minAngle: 2,
        avoidLabelOverlap: true,
        itemStyle: {
          borderColor: '#FFFFFF',
          borderWidth: 2,
          borderRadius: 5,
        },
        label: {
          show: false,
        },
        emphasis: {
          label: {
            show: true,
            fontWeight: 600,
          },
        },
        data,
      },
    ],
  }
}

export function stackedAreaChartOption(
  categories: string[],
  series: StackedChartSeries[],
): EChartsCoreOption {
  return {
    color: CHART_COLORS,
    textStyle: CHART_TEXT_STYLE,
    tooltip: {
      trigger: 'axis',
      confine: true,
    },
    legend: {
      type: 'scroll',
      top: 0,
      left: 8,
      right: 8,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: {
        color: '#4E5969',
        fontSize: 11,
      },
    },
    grid: {
      top: 42,
      right: 16,
      bottom: 16,
      left: 10,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: categories,
      axisLine: {
        lineStyle: { color: '#C9CDD4' },
      },
      axisLabel: {
        color: '#86909C',
        hideOverlap: true,
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: {
        color: '#86909C',
      },
      splitLine: {
        lineStyle: { color: '#E5E6EB' },
      },
    },
    series: series.map((item) => ({
      name: item.name,
      type: 'line',
      stack: 'total',
      smooth: 0.25,
      symbol: 'none',
      lineStyle: { width: 1.5 },
      areaStyle: { opacity: 0.2 },
      emphasis: { focus: 'series' },
      data: item.values,
    })),
  }
}
