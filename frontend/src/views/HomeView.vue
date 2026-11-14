<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import type { EChartsCoreOption } from 'echarts/core'
import { Message } from '@arco-design/web-vue'
import { IconApps, IconBug, IconRight } from '@arco-design/web-vue/es/icon'
import EChart from '@/components/EChart.vue'
import TrafficRankings from '@/components/TrafficRankings.vue'
import GlobalAnnouncements from '@/components/GlobalAnnouncements.vue'
import { uiApi } from '@/api'
import type {
  ChartDatum,
  DistributionChart,
  HomeBittorrentTrafficResponse,
  HomeCompanionStatisticsResponse,
  HomeNetworkStatisticsResponse,
  HomeStats,
  StackedAreaChart,
  StatisticsSource,
  TrafficCityDatum,
  TrafficDistribution,
} from '@/api/types'
import { useSessionStore } from '@/stores/session'
import {
  CHART_COLORS,
  CHART_TEXT_STYLE,
  distributionPieOption,
  stackedAreaChartOption,
} from '@/utils/charts'
import { errorMessage, useFormatters } from '@/utils/format'

const router = useRouter()
const session = useSessionStore()
const { t } = useI18n()
const { formatBytes } = useFormatters()
const stats = ref<HomeStats | null>(null)
const loading = ref(true)
const trafficStats = ref<HomeBittorrentTrafficResponse | null>(null)
const trafficLoading = ref(false)
const trafficDays = ref(7)
const networkStats = ref<HomeNetworkStatisticsResponse | null>(null)
const networkLoading = ref(false)
const companionStats = ref<HomeCompanionStatisticsResponse | null>(null)
const companionLoading = ref(false)
const selectedDays = ref(7)
const networkSource = ref<StatisticsSource>('ban')
let statisticsRequestId = 0
let trafficRequestId = 0

function moduleEnabled(module: string) {
  return stats.value?.enabledModules?.[module] === true
}

const banStats = computed(() => [
  { label: t('home.allTime'), value: stats.value?.banHistory.allTime ?? 0 },
  { label: t('home.last30Days'), value: stats.value?.banHistory.last30Days ?? 0 },
  { label: t('home.last7Days'), value: stats.value?.banHistory.last7Days ?? 0 },
  { label: t('home.last24Hours'), value: stats.value?.banHistory.last24Hours ?? 0 },
])

const appStats = computed(() => [
  { label: t('home.last30Days'), value: stats.value?.userApps.last30Days ?? 0 },
  { label: t('home.last14Days'), value: stats.value?.userApps.last14Days ?? 0 },
  { label: t('home.last7Days'), value: stats.value?.userApps.last7Days ?? 0 },
  { label: t('home.last24Hours'), value: stats.value?.userApps.last24Hours ?? 0 },
])

const peerIdOption = computed<EChartsCoreOption>(() =>
  pieOption(networkStats.value?.peerIds),
)
const clientNameOption = computed<EChartsCoreOption>(() =>
  pieOption(networkStats.value?.clientNames),
)
const clientNameTrendOption = computed<EChartsCoreOption>(() =>
  stackedAreaOption(networkStats.value?.clientNameTrend),
)
const geoCityOption = computed<EChartsCoreOption>(() =>
  stackedAreaOption(networkStats.value?.geoCities),
)
const ipVersionOption = computed<EChartsCoreOption>(() =>
  stackedAreaOption(networkStats.value?.ipVersions),
)
const companionVersionOption = computed<EChartsCoreOption>(() =>
  pieOption(companionStats.value?.companionVersions),
)
const companionIpOption = computed<EChartsCoreOption>(() =>
  pieOption(companionStats.value?.companionIpVersions),
)
const companionGeoIpOption = computed<EChartsCoreOption>(() =>
  pieOption(companionStats.value?.companionGeoIp),
)
const trafficUploadCitiesOption = computed<EChartsCoreOption>(() =>
  trafficPieOption(trafficStats.value?.topUploadCities),
)
const trafficDownloadCitiesOption = computed<EChartsCoreOption>(() =>
  trafficPieOption(trafficStats.value?.topDownloadCities),
)

function chartLabel(name: string) {
  if (name === 'Unknown') return t('home.statistics.unknown')
  if (name === 'Other') return t('home.statistics.other')
  return name
}

function hasDistribution(chart: DistributionChart | undefined) {
  return Boolean(chart?.data.some((item) => item.value > 0))
}

function hasStackedData(chart: StackedAreaChart | undefined) {
  return Boolean(
    chart?.series.some((series) => series.values.some((value) => value > 0)),
  )
}

function hasTrafficDistribution(chart: TrafficDistribution | undefined) {
  return Boolean(
    chart?.data.some((item) => {
      if (item.name === 'Unknown' || item.name === 'Other') return false
      try {
        return BigInt(item.bytes) > 0n
      } catch {
        return false
      }
    }),
  )
}

function compactTrafficCities(chart: TrafficDistribution | undefined) {
  const items = chart?.data ?? []
  const named = items.filter((item) => item.name !== 'Other' && item.name !== 'Unknown')
  const visible = named.slice(0, 15)
  let total = 0n
  let unknown = 0n
  try {
    total = BigInt(chart?.totalBytes ?? '0')
  } catch {
    // Fall through to zero when a stale cache entry has an invalid total.
  }
  for (const item of items) {
    try {
      if (item.name === 'Unknown') {
        unknown += BigInt(item.bytes)
      }
    } catch {
      // Ignore malformed cache entries and keep the remaining chart usable.
    }
  }
  const knownTotal = total - unknown
  let shown = 0n
  for (const item of visible) {
    try {
      shown += BigInt(item.bytes)
    } catch {
      // Ignore malformed cache entries and keep the remaining chart usable.
    }
  }
  const otherBytes = knownTotal - shown
  const compact: TrafficCityDatum[] = visible.map((item) => ({ ...item }))
  if (otherBytes > 0n) {
    compact.push({ name: 'Other', bytes: otherBytes.toString() })
  }
  return compact
}

function trafficTopRows(chart: TrafficDistribution | undefined) {
  return (chart?.data ?? [])
    .filter((item) => item.name !== 'Other' && item.name !== 'Unknown')
    .slice(0, 30)
}

function pieOption(chart: DistributionChart | undefined): EChartsCoreOption {
  const data: ChartDatum[] = (chart?.data ?? []).map((item) => ({
    name: chartLabel(item.name),
    value: item.value,
  }))
  return distributionPieOption('', data)
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function trafficPieOption(chart: TrafficDistribution | undefined): EChartsCoreOption {
  const compact = compactTrafficCities(chart)
  const values = compact.map((item) => {
    try {
      return BigInt(item.bytes)
    } catch {
      return 0n
    }
  })
  const maximum = values.reduce((current, value) => (value > current ? value : current), 0n)
  // Pie slices only need relative values. Keeping them below 2^31 also avoids
  // ECharts internal integer-conversion warnings for very large byte counts.
  const safeMaximum = 1_000_000_000n
  let divisor = 1n
  while (maximum / divisor > safeMaximum) {
    divisor *= 1024n
  }

  const data = compact.map((item, index) => ({
    name: chartLabel(item.name),
    value: Number(values[index]) / Number(divisor),
    bytes: item.bytes,
  }))
  return {
    color: CHART_COLORS,
    textStyle: CHART_TEXT_STYLE,
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: unknown) => {
        const item = params as {
          name?: string
          percent?: number
          marker?: string
          data?: { bytes?: string }
        }
        return `${item.marker ?? ''}${escapeHtml(item.name ?? '')}<br/>${formatBytes(
          item.data?.bytes,
        )} (${item.percent ?? 0}%)`
      },
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
        type: 'pie',
        radius: ['38%', '62%'],
        center: ['50%', '43%'],
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

function formatBucket(value: string) {
  const date = dayjs(value)
  return selectedDays.value === 1 ? date.format('HH:mm') : date.format('MM-DD')
}

function stackedAreaOption(chart: StackedAreaChart | undefined): EChartsCoreOption {
  return stackedAreaChartOption(
    (chart?.categories ?? []).map(formatBucket),
    (chart?.series ?? []).map((series) => ({
      name: chartLabel(series.name),
      values: series.values,
    })),
  )
}

async function loadHomeStats() {
  loading.value = true
  try {
    stats.value = await uiApi.home()
    if (moduleEnabled('network-charts')) void loadNetworkStats()
    if (moduleEnabled('companion-charts')) void loadCompanionStats()
    if (stats.value.bittorrentTrafficEnabled) {
      void loadTrafficStats()
    } else {
      trafficRequestId++
      trafficStats.value = null
      trafficLoading.value = false
    }
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    loading.value = false
  }
}

async function loadNetworkStats() {
  if (!moduleEnabled('network-charts')) return
  const requestId = ++statisticsRequestId
  networkLoading.value = true
  try {
    const result = await uiApi.homeNetworkStatistics(selectedDays.value, networkSource.value)
    if (requestId === statisticsRequestId) {
      networkStats.value = result
    }
  } catch (error) {
    if (requestId === statisticsRequestId) {
      Message.error(errorMessage(error, t('home.statistics.loadFailed')))
    }
  } finally {
    if (requestId === statisticsRequestId) {
      networkLoading.value = false
    }
  }
}

async function loadTrafficStats() {
  if (stats.value?.bittorrentTrafficEnabled !== true) return
  const requestId = ++trafficRequestId
  trafficLoading.value = true
  try {
    const result = await uiApi.homeBittorrentTrafficStatistics(trafficDays.value)
    if (requestId === trafficRequestId) {
      trafficStats.value = result
    }
  } catch (error) {
    if (requestId === trafficRequestId) {
      Message.error(errorMessage(error, t('home.statistics.loadFailed')))
    }
  } finally {
    if (requestId === trafficRequestId) {
      trafficLoading.value = false
    }
  }
}

async function loadCompanionStats() {
  if (!moduleEnabled('companion-charts')) return
  companionLoading.value = true
  try {
    companionStats.value = await uiApi.homeCompanionStatistics()
  } catch (error) {
    Message.error(errorMessage(error, t('home.statistics.loadFailed')))
  } finally {
    companionLoading.value = false
  }
}

watch([selectedDays, networkSource], loadNetworkStats)
watch(trafficDays, loadTrafficStats)

onMounted(() => {
  loadHomeStats()
})
</script>

<template>
  <div class="page-stack">
    <a-alert
      v-if="session.user?.bannedAt"
      type="error"
      show-icon
      :title="t('home.accountPaused')"
    >
      {{ t('home.accountPausedText', { reason: session.user.bannedReason || 'N/A' }) }}
    </a-alert>

    <GlobalAnnouncements placement="HOME_HERO" variant="hero" />

    <a-card class="hero-card" :bordered="false">
      <div class="hero-copy">
        <span class="hero-eyebrow">{{ t('home.heroEyebrow') }}</span>
        <h1>{{ t('home.heroTitle') }}</h1>
        <h2>{{ t('home.heroSubtitle') }}</h2>
        <p>{{ t('home.heroText') }}</p>
        <a-button type="primary" size="large" @click="router.push('/userapp')">
          <template #icon><IconApps /></template>
          {{ t('home.createApp') }}
          <IconRight />
        </a-button>
      </div>
      <div class="hero-graphic" aria-hidden="true">
        <span class="hero-ring hero-ring--one" />
        <span class="hero-ring hero-ring--two" />
        <span class="hero-dot hero-dot--one" />
        <span class="hero-dot hero-dot--two" />
        <span class="hero-dot hero-dot--three" />
      </div>
    </a-card>

    <section v-if="moduleEnabled('ban-history') || moduleEnabled('active-apps')" class="stats-section">
      <div class="section-heading">
        <div>
          <h2>{{ t('home.statistics.overviewTitle') }}</h2>
          <p>{{ t('home.statistics.overviewDescription') }}</p>
        </div>
      </div>
      <a-spin :loading="loading" class="stats-spin">
        <a-row :gutter="[16, 16]">
          <a-col v-if="moduleEnabled('ban-history')" :xs="24" :lg="moduleEnabled('active-apps') ? 12 : 24">
            <a-card class="surface-card stats-card">
              <template #title>
                <span class="stats-title">
                  <span class="stats-icon"><IconBug /></span>
                  {{ t('home.banHistory') }}
                </span>
              </template>
              <a-row :gutter="[12, 12]">
                <a-col v-for="item in banStats" :key="item.label" :xs="12" :md="6">
                  <a-statistic
                    :title="item.label"
                    :value="item.value"
                    :precision="0"
                    show-group-separator
                    :animation="true"
                  />
                </a-col>
              </a-row>
            </a-card>
          </a-col>
          <a-col v-if="moduleEnabled('active-apps')" :xs="24" :lg="moduleEnabled('ban-history') ? 12 : 24">
            <a-card class="surface-card stats-card">
              <template #title>
                <span class="stats-title">
                  <span class="stats-icon stats-icon--apps"><IconApps /></span>
                  {{ t('home.activeApps') }}
                </span>
              </template>
              <a-row :gutter="[12, 12]">
                <a-col v-for="item in appStats" :key="item.label" :xs="12" :md="6">
                  <a-statistic
                    :title="item.label"
                    :value="item.value"
                    :precision="0"
                    show-group-separator
                    :animation="true"
                  />
                </a-col>
              </a-row>
            </a-card>
          </a-col>
        </a-row>
      </a-spin>
    </section>

    <section v-if="stats?.bittorrentTrafficEnabled" class="stats-section">
      <div class="section-heading">
        <div>
          <h2>{{ t('home.statistics.trafficTitle') }}</h2>
          <p>{{ t('home.statistics.trafficDescription') }}</p>
          <p class="traffic-geo-note">
            {{ t('home.statistics.trafficGeoUnknownNote') }}
          </p>
        </div>
      </div>

      <a-card class="surface-card chart-controls">
        <div class="chart-control-row">
          <div class="chart-control">
            <span>{{ t('home.statistics.range') }}</span>
            <a-radio-group v-model="trafficDays" type="button">
              <a-radio :value="7">{{ t('home.last7Days') }}</a-radio>
              <a-radio :value="3">{{ t('home.statistics.last3Days') }}</a-radio>
              <a-radio :value="1">{{ t('home.last24Hours') }}</a-radio>
            </a-radio-group>
          </div>
          <span v-if="trafficStats" class="chart-updated">
            {{
              t('home.statistics.updatedAt', {
                time: dayjs(trafficStats.generatedAt).format('HH:mm:ss'),
              })
            }}
          </span>
        </div>
      </a-card>

      <a-spin :loading="trafficLoading" class="traffic-spin">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :md="8">
            <a-card class="surface-card traffic-metric traffic-metric--total">
              <span class="traffic-metric-label">{{ t('home.statistics.totalTransfer') }}</span>
              <strong>{{ formatBytes(trafficStats?.totals.transferBytes) }}</strong>
              <span class="traffic-metric-hint">
                {{ t('home.statistics.totalTransferHint') }}
              </span>
            </a-card>
          </a-col>
          <a-col :xs="24" :md="8">
            <a-card class="surface-card traffic-metric traffic-metric--download">
              <span class="traffic-metric-label">{{ t('home.statistics.totalDownload') }}</span>
              <strong>{{ formatBytes(trafficStats?.totals.downloadBytes) }}</strong>
              <span class="traffic-metric-hint">
                {{ t('home.statistics.totalDownloadHint') }}
              </span>
            </a-card>
          </a-col>
          <a-col :xs="24" :md="8">
            <a-card class="surface-card traffic-metric traffic-metric--upload">
              <span class="traffic-metric-label">{{ t('home.statistics.totalUpload') }}</span>
              <strong>{{ formatBytes(trafficStats?.totals.uploadBytes) }}</strong>
              <span class="traffic-metric-hint">
                {{ t('home.statistics.totalUploadHint') }}
              </span>
            </a-card>
          </a-col>

          <a-col :xs="24" :xl="12">
            <a-card
              class="surface-card chart-card traffic-chart-card"
              :title="t('home.statistics.topUploadCities')"
            >
              <template #extra>
                <a-tag color="arcoblue">{{ t('home.statistics.topFifteenPlusOther') }}</a-tag>
              </template>
              <EChart
                v-if="hasTrafficDistribution(trafficStats?.topUploadCities)"
                :option="trafficUploadCitiesOption"
                height="370px"
                :loading="trafficLoading"
                :loading-text="t('common.loading')"
              />
              <a-empty v-else :description="t('home.statistics.noData')" />
              <details v-if="hasTrafficDistribution(trafficStats?.topUploadCities)">
                <summary>{{ t('home.statistics.viewTop30') }}</summary>
                <ol class="traffic-ranking-list">
                  <li
                    v-for="(item, index) in trafficTopRows(trafficStats?.topUploadCities)"
                    :key="item.name"
                  >
                    <span>{{ index + 1 }}. {{ chartLabel(item.name) }}</span>
                    <strong>{{ formatBytes(item.bytes) }}</strong>
                  </li>
                </ol>
              </details>
            </a-card>
          </a-col>
          <a-col :xs="24" :xl="12">
            <a-card
              class="surface-card chart-card traffic-chart-card"
              :title="t('home.statistics.topDownloadCities')"
            >
              <template #extra>
                <a-tag color="green">{{ t('home.statistics.topFifteenPlusOther') }}</a-tag>
              </template>
              <EChart
                v-if="hasTrafficDistribution(trafficStats?.topDownloadCities)"
                :option="trafficDownloadCitiesOption"
                height="370px"
                :loading="trafficLoading"
                :loading-text="t('common.loading')"
              />
              <a-empty v-else :description="t('home.statistics.noData')" />
              <details v-if="hasTrafficDistribution(trafficStats?.topDownloadCities)">
                <summary>{{ t('home.statistics.viewTop30') }}</summary>
                <ol class="traffic-ranking-list">
                  <li
                    v-for="(item, index) in trafficTopRows(trafficStats?.topDownloadCities)"
                    :key="item.name"
                  >
                    <span>{{ index + 1 }}. {{ chartLabel(item.name) }}</span>
                    <strong>{{ formatBytes(item.bytes) }}</strong>
                  </li>
                </ol>
              </details>
            </a-card>
          </a-col>
        </a-row>
      </a-spin>
      <TrafficRankings :days="trafficDays" />
    </section>

    <section v-if="moduleEnabled('network-charts')" class="stats-section">
      <div class="section-heading">
        <div>
          <h2>{{ t('home.statistics.networkTitle') }}</h2>
          <p>{{ t('home.statistics.networkDescription') }}</p>
        </div>
      </div>

      <a-card class="surface-card chart-controls">
        <div class="chart-control-row">
          <div class="chart-control">
            <span>{{ t('home.statistics.range') }}</span>
            <a-radio-group v-model="selectedDays" type="button">
              <a-radio :value="30">{{ t('home.last30Days') }}</a-radio>
              <a-radio :value="14">{{ t('home.last14Days') }}</a-radio>
              <a-radio :value="7">{{ t('home.last7Days') }}</a-radio>
              <a-radio :value="1">{{ t('home.last24Hours') }}</a-radio>
            </a-radio-group>
          </div>
          <div class="chart-control">
            <span>{{ t('home.statistics.source') }}</span>
            <a-select v-model="networkSource" class="source-select">
              <a-option value="ban">{{ t('home.statistics.sources.ban') }}</a-option>
              <a-option value="all">{{ t('home.statistics.sources.all') }}</a-option>
            </a-select>
          </div>
          <span v-if="networkStats" class="chart-updated">
            {{
              t('home.statistics.updatedAt', {
                time: dayjs(networkStats.generatedAt).format('HH:mm:ss'),
              })
            }}
          </span>
        </div>
      </a-card>

      <a-spin :loading="networkLoading" class="network-spin">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :xl="12">
            <a-card class="surface-card chart-card" :title="t('home.statistics.peerIds')">
              <template #extra>
                <a-tag color="arcoblue">{{ t('home.statistics.topItems') }}</a-tag>
              </template>
              <EChart
                v-if="hasDistribution(networkStats?.peerIds)"
                :option="peerIdOption"
                height="330px"
                :loading="networkLoading"
                :loading-text="t('common.loading')"
              />
              <a-empty v-else :description="t('home.statistics.noData')" />
            </a-card>
          </a-col>
          <a-col :xs="24" :xl="12">
            <a-card class="surface-card chart-card" :title="t('home.statistics.clientNames')">
              <template #extra>
                <a-tag color="green">{{ t('home.statistics.topItems') }}</a-tag>
              </template>
              <EChart
                v-if="hasDistribution(networkStats?.clientNames)"
                :option="clientNameOption"
                height="330px"
                :loading="networkLoading"
                :loading-text="t('common.loading')"
              />
              <a-empty v-else :description="t('home.statistics.noData')" />
            </a-card>
          </a-col>
          <a-col :span="24">
            <a-card
              class="surface-card chart-card"
              :title="t('home.statistics.clientNameTrend')"
            >
              <template #extra>
                <a-tag color="green">{{ t('home.statistics.topItems') }}</a-tag>
              </template>
              <EChart
                v-if="hasStackedData(networkStats?.clientNameTrend)"
                :option="clientNameTrendOption"
                height="360px"
                :loading="networkLoading"
                :loading-text="t('common.loading')"
              />
              <a-empty v-else :description="t('home.statistics.noData')" />
            </a-card>
          </a-col>
          <a-col :span="24">
            <a-card class="surface-card chart-card" :title="t('home.statistics.geoCities')">
              <template #extra>
                <a-tag color="purple">{{ t('home.statistics.topCities') }}</a-tag>
              </template>
              <EChart
                v-if="hasStackedData(networkStats?.geoCities)"
                :option="geoCityOption"
                height="360px"
                :loading="networkLoading"
                :loading-text="t('common.loading')"
              />
              <a-empty v-else :description="t('home.statistics.noData')" />
            </a-card>
          </a-col>
          <a-col :span="24">
            <a-card class="surface-card chart-card" :title="t('home.statistics.ipVersions')">
              <template #extra>
                <a-tag color="orange">{{ t('home.statistics.addressStack') }}</a-tag>
              </template>
              <EChart
                v-if="hasStackedData(networkStats?.ipVersions)"
                :option="ipVersionOption"
                height="320px"
                :loading="networkLoading"
                :loading-text="t('common.loading')"
              />
              <a-empty v-else :description="t('home.statistics.noData')" />
            </a-card>
          </a-col>
        </a-row>
      </a-spin>
    </section>

    <section v-if="moduleEnabled('companion-charts')" class="stats-section">
      <div class="section-heading">
        <div>
          <h2>{{ t('home.statistics.companionTitle') }}</h2>
          <p>{{ t('home.statistics.companionDescription') }}</p>
        </div>
      </div>
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :xl="8">
          <a-card class="surface-card chart-card" :title="t('home.statistics.companionVersions')">
            <EChart
              v-if="hasDistribution(companionStats?.companionVersions)"
              :option="companionVersionOption"
              height="340px"
              :loading="companionLoading"
              :loading-text="t('common.loading')"
            />
            <a-empty v-else :description="t('home.statistics.noData')" />
          </a-card>
        </a-col>
        <a-col :xs="24" :xl="8">
          <a-card class="surface-card chart-card" :title="t('home.statistics.companionIpVersions')">
            <EChart
              v-if="hasDistribution(companionStats?.companionIpVersions)"
              :option="companionIpOption"
              height="340px"
              :loading="companionLoading"
              :loading-text="t('common.loading')"
            />
            <a-empty v-else :description="t('home.statistics.noData')" />
          </a-card>
        </a-col>
        <a-col :xs="24" :xl="8">
          <a-card class="surface-card chart-card" :title="t('home.statistics.companionGeoIp')">
            <EChart
              v-if="hasDistribution(companionStats?.companionGeoIp)"
              :option="companionGeoIpOption"
              height="340px"
              :loading="companionLoading"
              :loading-text="t('common.loading')"
            />
            <a-empty v-else :description="t('home.statistics.noData')" />
          </a-card>
        </a-col>
      </a-row>
    </section>
  </div>
</template>

<style scoped>
.hero-card {
  position: relative;
  overflow: hidden;
  color: var(--color-white);
  background: linear-gradient(120deg, rgb(var(--primary-7)), rgb(var(--primary-5)));
  border-radius: var(--app-radius);
}

/* Keep the blue identity without a bright surface against the dark page. */
body[arco-theme='dark'] .hero-card {
  background: linear-gradient(
    120deg,
    color-mix(in srgb, rgb(var(--primary-7)) 35%, var(--color-bg-2)),
    color-mix(in srgb, rgb(var(--primary-5)) 38%, var(--color-bg-2))
  );
}

body[arco-theme='dark'] .hero-eyebrow,
body[arco-theme='dark'] .hero-copy :deep(.arco-btn) {
  color: var(--color-text-1);
  background: var(--color-bg-2);
  border-color: var(--color-border-3);
}

body[arco-theme='dark'] .hero-copy :deep(.arco-btn:hover) {
  background: var(--color-fill-3);
}

body[arco-theme='dark'] .hero-ring {
  border-color: rgb(255 255 255 / 12%);
}

body[arco-theme='dark'] .hero-dot {
  background: var(--color-text-3);
  box-shadow: 0 0 0 8px rgb(255 255 255 / 5%);
}

.hero-card :deep(.arco-card-body) {
  position: relative;
  min-height: 260px;
  padding: 44px;
}

.hero-copy {
  position: relative;
  z-index: 2;
  max-width: 640px;
}

.hero-eyebrow {
  display: inline-flex;
  padding: 3px 10px;
  color: rgb(var(--primary-7));
  font-size: 12px;
  font-weight: 600;
  background: rgb(255 255 255 / 88%);
  border-radius: var(--app-radius);
}

.hero-copy h1 {
  margin: 18px 0 2px;
  color: var(--color-white);
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.hero-copy h2 {
  margin: 0 0 14px;
  color: var(--color-white);
  font-size: 20px;
  font-weight: 500;
  opacity: 0.9;
}

.hero-copy p {
  margin: 0 0 28px;
  color: var(--color-white);
  font-size: 15px;
  line-height: 1.7;
  opacity: 0.88;
}

.hero-copy :deep(.arco-btn) {
  color: rgb(var(--primary-6));
  background: white;
  border-color: white;
}

.hero-copy :deep(.arco-btn .arco-icon) {
  color: rgb(var(--primary-6));
}

.hero-graphic {
  position: absolute;
  top: 0;
  right: 0;
  width: 46%;
  height: 100%;
  pointer-events: none;
}

.hero-ring {
  position: absolute;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 50%;
}

.hero-ring--one {
  top: -100px;
  right: -30px;
  width: 380px;
  height: 380px;
}

.hero-ring--two {
  top: -20px;
  right: 130px;
  width: 220px;
  height: 220px;
}

.hero-dot {
  position: absolute;
  width: 10px;
  height: 10px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 0 0 8px rgb(255 255 255 / 10%);
}

.hero-dot--one {
  top: 68px;
  right: 270px;
}

.hero-dot--two {
  right: 70px;
  bottom: 62px;
}

.hero-dot--three {
  top: 120px;
  right: 72px;
  width: 6px;
  height: 6px;
}

.stats-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.section-heading h2 {
  margin: 8px 0 3px;
  color: var(--color-text-1);
  font-size: 20px;
  font-weight: 600;
}

.section-heading p {
  margin: 0;
  color: var(--color-text-3);
  line-height: 1.6;
}

.stats-spin,
.network-spin,
.traffic-spin {
  display: block;
  width: 100%;
}

.traffic-metric :deep(.arco-card-body) {
  display: flex;
  flex-direction: column;
  min-height: 142px;
  padding: 22px;
}

.traffic-geo-note {
  color: rgb(var(--orange-6));
  font-size: 12px;
}

.traffic-metric-label {
  color: var(--color-text-3);
  font-size: 13px;
  font-weight: 500;
}

.traffic-metric strong {
  margin: 12px 0 8px;
  color: var(--color-text-1);
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 650;
  letter-spacing: -0.02em;
  overflow-wrap: anywhere;
}

.traffic-metric-hint {
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.55;
}

.traffic-metric--total strong {
  color: rgb(var(--primary-6));
}

.traffic-metric--download strong {
  color: rgb(var(--primary-5));
}

.traffic-metric--upload strong {
  color: rgb(var(--green-6));
}

.traffic-chart-card :deep(.arco-card-body) {
  min-height: 300px;
}

.traffic-chart-card details {
  margin-top: 10px;
  border-top: 1px solid var(--color-border-2);
}

.traffic-chart-card summary {
  padding: 12px 2px 4px;
  color: rgb(var(--primary-6));
  font-size: 12px;
  cursor: pointer;
}

.traffic-ranking-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 5px 24px;
  max-height: 280px;
  padding: 8px 2px 4px 22px;
  margin: 0;
  overflow: auto;
  color: var(--color-text-2);
  font-size: 12px;
}

.traffic-ranking-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.traffic-ranking-list span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.traffic-ranking-list strong {
  flex: 0 0 auto;
  color: var(--color-text-1);
  font-weight: 500;
}

.stats-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.stats-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
  border-radius: var(--app-radius);
}

.stats-icon--apps {
  color: rgb(var(--green-6));
  background: var(--color-success-light-1);
}

.stats-card :deep(.arco-statistic-title) {
  color: var(--color-text-3);
  font-size: 12px;
}

.stats-card :deep(.arco-statistic-value) {
  font-size: 24px;
}

.chart-controls :deep(.arco-card-body) {
  padding: 14px 18px;
}

.chart-control-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px 24px;
}

.chart-control {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chart-control > span {
  color: var(--color-text-3);
  font-size: 12px;
  white-space: nowrap;
}

.source-select {
  width: 170px;
}

.chart-updated {
  margin-left: auto;
  color: var(--color-text-3);
  font-size: 12px;
}

.chart-card :deep(.arco-card-body) {
  min-height: 270px;
}

.chart-card :deep(.arco-empty) {
  padding: 70px 0;
}

@media (max-width: 720px) {
  .hero-card :deep(.arco-card-body) {
    min-height: 0;
    padding: 28px 22px;
  }

  .hero-copy h1 {
    font-size: 32px;
  }

  .hero-copy p {
    margin-bottom: 22px;
  }

  .hero-graphic {
    opacity: 0.45;
  }

  .chart-control,
  .chart-control-row {
    align-items: stretch;
  }

  .chart-control {
    width: 100%;
    flex-direction: column;
  }

  .chart-control :deep(.arco-radio-group),
  .source-select {
    width: 100%;
  }

  .chart-control :deep(.arco-radio-button) {
    flex: 1;
    text-align: center;
  }

  .chart-updated {
    width: 100%;
    margin-left: 0;
  }

  .traffic-ranking-list {
    grid-template-columns: 1fr;
  }

}
</style>
