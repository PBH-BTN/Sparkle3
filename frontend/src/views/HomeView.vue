<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import type { EChartsCoreOption } from 'echarts/core'
import { Message } from '@arco-design/web-vue'
import { IconApps, IconBug, IconRight } from '@arco-design/web-vue/es/icon'
import EChart from '@/components/EChart.vue'
import GlobalAnnouncements from '@/components/GlobalAnnouncements.vue'
import { uiApi } from '@/api'
import type {
  ChartDatum,
  DistributionChart,
  HomeCompanionStatisticsResponse,
  HomeNetworkStatisticsResponse,
  HomeStats,
  StackedAreaChart,
  StatisticsSource,
} from '@/api/types'
import { useSessionStore } from '@/stores/session'
import { errorMessage } from '@/utils/format'

const CHART_COLORS = [
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

const router = useRouter()
const session = useSessionStore()
const { t } = useI18n()
const stats = ref<HomeStats | null>(null)
const loading = ref(true)
const networkStats = ref<HomeNetworkStatisticsResponse | null>(null)
const networkLoading = ref(false)
const companionStats = ref<HomeCompanionStatisticsResponse | null>(null)
const companionLoading = ref(false)
const selectedDays = ref(7)
const networkSource = ref<StatisticsSource>('ban')
let statisticsRequestId = 0

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

function pieOption(chart: DistributionChart | undefined): EChartsCoreOption {
  const data: ChartDatum[] = (chart?.data ?? []).map((item) => ({
    name: chartLabel(item.name),
    value: item.value,
  }))
  return {
    color: CHART_COLORS,
    textStyle: {
      fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", sans-serif',
    },
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

function formatBucket(value: string) {
  const date = dayjs(value)
  return selectedDays.value === 1 ? date.format('HH:mm') : date.format('MM-DD')
}

function stackedAreaOption(chart: StackedAreaChart | undefined): EChartsCoreOption {
  return {
    color: CHART_COLORS,
    textStyle: {
      fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", sans-serif',
    },
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
      data: (chart?.categories ?? []).map(formatBucket),
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
    series: (chart?.series ?? []).map((series) => ({
      name: chartLabel(series.name),
      type: 'line',
      stack: 'total',
      smooth: 0.25,
      symbol: 'none',
      lineStyle: { width: 1.5 },
      areaStyle: { opacity: 0.2 },
      emphasis: { focus: 'series' },
      data: series.values,
    })),
  }
}

async function loadHomeStats() {
  loading.value = true
  try {
    stats.value = await uiApi.home()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    loading.value = false
  }
}

async function loadNetworkStats() {
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

async function loadCompanionStats() {
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

onMounted(() => {
  loadHomeStats()
  loadNetworkStats()
  loadCompanionStats()
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

    <section class="stats-section">
      <div class="section-heading">
        <div>
          <h2>{{ t('home.statistics.overviewTitle') }}</h2>
          <p>{{ t('home.statistics.overviewDescription') }}</p>
        </div>
      </div>
      <a-spin :loading="loading" class="stats-spin">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :lg="12">
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
          <a-col :xs="24" :lg="12">
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

    <section class="stats-section">
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

    <section class="stats-section">
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
  background:
    linear-gradient(120deg, rgb(var(--primary-7)), rgb(var(--primary-5)));
  border-radius: 10px;
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
  border-radius: 20px;
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
.network-spin {
  display: block;
  width: 100%;
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
  border-radius: 6px;
}

.stats-icon--apps {
  color: rgb(var(--green-6));
  background: rgb(var(--green-1));
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
}
</style>
