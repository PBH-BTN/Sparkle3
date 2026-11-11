<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EChartsCoreOption } from 'echarts/core'
import type { TableColumnData } from '@arco-design/web-vue'
import { IconRefresh, IconSearch } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import EChart from '@/components/EChart.vue'
import TablePager from '@/components/TablePager.vue'
import { historyApi } from '@/api'
import type {
  SwarmPeerInsightsResponse,
  SwarmTrackerQuery,
  SwarmTrackerRecord,
} from '@/api/types'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useFormatters } from '@/utils/format'

const { t } = useI18n()
const {
  formatDate,
  formatNumber,
  formatProgress,
} = useFormatters()
const insights = ref<SwarmPeerInsightsResponse | null>(null)
const insightsLoading = ref(false)
const insightDays = ref(7)
const form = reactive({
  infoHash: '',
  peerIp: '',
  peerPort: undefined as number | undefined,
  peerId: '',
  peerClientName: '',
  peerProgress: undefined as number | undefined,
  fromPeerTraffic: undefined as number | undefined,
  toPeerTraffic: undefined as number | undefined,
  flags: '',
  userProgress: undefined as number | undefined,
})

type Filters = Omit<SwarmTrackerQuery, 'page' | 'size' | 'sortBy' | 'sortOrder'>

function buildQuery(): Filters {
  return {
    infoHash: form.infoHash.trim() || undefined,
    peerIp: form.peerIp.trim() || undefined,
    peerPort: form.peerPort,
    peerId: form.peerId.trim() || undefined,
    peerClientName: form.peerClientName.trim() || undefined,
    peerProgress: form.peerProgress,
    fromPeerTraffic: form.fromPeerTraffic,
    toPeerTraffic: form.toPeerTraffic,
    flags: form.flags.trim() || undefined,
    userProgress: form.userProgress,
  }
}

const {
  records,
  loading,
  page,
  pageSize,
  total,
  search,
  changePage,
  changePageSize,
  changeSort,
} = usePagedQuery<SwarmTrackerRecord, Filters>(
  historyApi.querySwarmTracker,
  buildQuery,
  { pageSize: 100, sortBy: 'last_time_seen' },
)

const columns = computed<TableColumnData[]>(() => [
  {
    title: t('swarmTracker.torrentId'),
    dataIndex: 'torrent_id',
    slotName: 'torrentId',
    width: 110,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('swarmTracker.peerIp'),
    dataIndex: 'peer_ip',
    slotName: 'peerIp',
    width: 155,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('swarmTracker.peerPort'),
    dataIndex: 'peer_port',
    slotName: 'peerPort',
    width: 105,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('swarmTracker.peerId'),
    dataIndex: 'peer_id',
    slotName: 'peerId',
    width: 180,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  { title: t('swarmTracker.peerClientName'), slotName: 'peerClientName', width: 170 },
  {
    title: t('swarmTracker.peerTaskProgress'),
    dataIndex: 'peer_progress',
    slotName: 'peerProgress',
    width: 130,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('swarmTracker.fromPeerTraffic'),
    dataIndex: 'from_peer_traffic',
    slotName: 'fromPeerTraffic',
    width: 170,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('swarmTracker.toPeerTraffic'),
    dataIndex: 'to_peer_traffic',
    slotName: 'toPeerTraffic',
    width: 170,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  { title: t('swarmTracker.flags'), slotName: 'flags', width: 100 },
  {
    title: t('swarmTracker.firstSeen'),
    dataIndex: 'first_time_seen',
    slotName: 'firstTimeSeen',
    width: 180,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('swarmTracker.lastSeen'),
    dataIndex: 'last_time_seen',
    slotName: 'lastTimeSeen',
    width: 180,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('swarmTracker.userTaskProgress'),
    dataIndex: 'user_progress',
    slotName: 'userProgress',
    width: 130,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
])

const peerPieOption = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'item' },
  color: ['#0e7490', '#f59e0b'],
  series: [
    {
      name: t('swarmTracker.ipVersionsTitle'),
      type: 'pie',
      radius: ['48%', '74%'],
      center: ['50%', '48%'],
      avoidLabelOverlap: true,
      itemStyle: {
        borderRadius: 5,
        borderColor: 'var(--color-bg-2)',
        borderWidth: 2,
      },
      label: {
        formatter: '{b}\n{d}%',
      },
      data: (insights.value?.ipVersions ?? []).map((item) => ({
        name: item.name,
        value: item.peers,
      })),
    },
  ],
}))

const peerDistributionOption = computed<EChartsCoreOption>(() => {
  const categories = (insights.value?.categories ?? []).map((value) =>
    formatDate(value),
  )
  return {
    tooltip: {
      trigger: 'axis',
      confine: true,
    },
    legend: {
      type: 'scroll',
      top: 0,
      textStyle: { fontSize: 10 },
    },
    grid: {
      left: 54,
      right: 20,
      top: 54,
      bottom: 54,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: categories,
      axisLabel: {
        hideOverlap: true,
        fontSize: 10,
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      name: t('swarmTracker.peerCount'),
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', height: 16, bottom: 8 },
    ],
    series: (insights.value?.series ?? []).map((item) => ({
      name: item.torrentId,
      type: 'line',
      stack: 'peer-count',
      smooth: 0.2,
      showSymbol: false,
      emphasis: { focus: 'series' },
      areaStyle: { opacity: 0.18 },
      lineStyle: { width: 1.2 },
      data: item.values,
    })),
  }
})

function reset() {
  form.infoHash = ''
  form.peerIp = ''
  form.peerPort = undefined
  form.peerId = ''
  form.peerClientName = ''
  form.peerProgress = undefined
  form.fromPeerTraffic = undefined
  form.toPeerTraffic = undefined
  form.flags = ''
  form.userProgress = undefined
  search()
}

async function loadInsights() {
  insightsLoading.value = true
  try {
    insights.value = await historyApi.swarmPeerInsights(insightDays.value)
  } catch {
    insights.value = null
  } finally {
    insightsLoading.value = false
  }
}

onMounted(() => {
  search()
  loadInsights()
})
</script>

<template>
  <div class="page-stack">
    <PageHeader
      :title="t('swarmTracker.title')"
      :description="t('swarmTracker.description')"
    />

    <a-card v-if="insights?.enabled" class="surface-card chart-controls">
      <div class="chart-controls-row">
        <div class="insights-title">
          <strong>{{ t('swarmTracker.insightsTitle') }}</strong>
          <small>{{ t('swarmTracker.insightsDescription') }}</small>
        </div>
        <a-radio-group
          v-model="insightDays"
          type="button"
          size="small"
          @change="loadInsights"
        >
          <a-radio :value="1">{{ t('swarmTracker.range1') }}</a-radio>
          <a-radio :value="3">{{ t('swarmTracker.range3') }}</a-radio>
          <a-radio :value="7">{{ t('swarmTracker.range7') }}</a-radio>
        </a-radio-group>
      </div>
    </a-card>

    <a-spin
      v-if="insights?.enabled"
      :loading="insightsLoading"
      class="insights-spin"
    >
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :xl="12">
          <a-card
            class="surface-card chart-card"
            :title="t('swarmTracker.ipVersionsTitle')"
          >
            <template #extra>
              <a-tag color="arcoblue">{{ t('swarmTracker.peerCount') }}</a-tag>
            </template>
            <EChart
              v-if="insights.ipVersions?.some((item) => item.peers > 0)"
              :option="peerPieOption"
              height="330px"
              :loading="insightsLoading"
              :loading-text="t('common.loading')"
            />
            <a-empty v-else :description="t('swarmTracker.noInsights')" />
          </a-card>
        </a-col>
        <a-col :xs="24" :xl="12">
          <a-card
            class="surface-card chart-card"
            :title="t('swarmTracker.insightsTitle')"
          >
            <template #extra>
              <a-tag color="green">{{ t('swarmTracker.top50Hint') }}</a-tag>
            </template>
            <EChart
              v-if="insights.series?.length"
              :option="peerDistributionOption"
              height="330px"
              :loading="insightsLoading"
              :loading-text="t('common.loading')"
            />
            <a-empty v-else :description="t('swarmTracker.noInsights')" />
          </a-card>
        </a-col>
      </a-row>
    </a-spin>

    <a-card class="surface-card filter-card">
      <a-form :model="form" layout="vertical" @submit="search">
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.infoHash')">
              <a-input v-model="form.infoHash" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.peerIp')">
              <a-input v-model="form.peerIp" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.peerPort')">
              <a-input-number
                v-model="form.peerPort"
                :min="0"
                :max="65535"
                hide-button
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.peerId')">
              <a-input v-model="form.peerId" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.peerClientName')">
              <a-input v-model="form.peerClientName" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.peerProgress')">
              <a-input-number
                v-model="form.peerProgress"
                :placeholder="t('swarmTracker.trafficRange')"
                :min="0"
                :max="1"
                :step="0.01"
                hide-button
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.fromPeerTraffic')">
              <a-input-number
                v-model="form.fromPeerTraffic"
                :placeholder="t('swarmTracker.trafficBytes')"
                :min="0"
                hide-button
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.toPeerTraffic')">
              <a-input-number
                v-model="form.toPeerTraffic"
                :placeholder="t('swarmTracker.trafficBytes')"
                :min="0"
                hide-button
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.userProgress')">
              <a-input-number
                v-model="form.userProgress"
                :placeholder="t('swarmTracker.trafficRange')"
                :min="0"
                :max="1"
                :step="0.01"
                hide-button
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item :label="t('swarmTracker.flags')">
              <a-input v-model="form.flags" allow-clear />
            </a-form-item>
          </a-col>
        </a-row>
        <div class="filter-actions">
          <a-button @click="reset">
            <template #icon><IconRefresh /></template>
            {{ t('common.reset') }}
          </a-button>
          <a-button type="primary" html-type="submit">
            <template #icon><IconSearch /></template>
            {{ t('common.query') }}
          </a-button>
        </div>
      </a-form>
    </a-card>

    <a-card class="surface-card table-card">
      <a-table
        :columns="columns"
        :data="records"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 2100 }"
        row-key="id"
        stripe
        @sorter-change="changeSort"
      >
        <template #empty>
          <EmptyState
            :title="t('swarmTracker.emptyTitle')"
            :description="t('swarmTracker.emptyText')"
          />
        </template>
        <template #torrentId="{ record }">{{ record.torrentId }}</template>
        <template #peerIp="{ record }">
          <code class="mono">{{ record.peerIp || 'N/A' }}</code>
        </template>
        <template #peerPort="{ record }">{{ record.peerPort ?? 'N/A' }}</template>
        <template #peerId="{ record }">
          <span class="text-truncate mono" :title="record.peerId || ''">
            {{ record.peerId || 'N/A' }}
          </span>
        </template>
        <template #peerClientName="{ record }">
          {{ record.peerClientName || 'N/A' }}
        </template>
        <template #peerProgress="{ record }">
          {{ formatProgress(record.peerProgress) }}
        </template>
        <template #fromPeerTraffic="{ record }">
          {{ formatNumber(record.fromPeerTraffic) }}
        </template>
        <template #toPeerTraffic="{ record }">
          {{ formatNumber(record.toPeerTraffic) }}
        </template>
        <template #flags="{ record }">
          <code class="mono">{{ record.flags || 'N/A' }}</code>
        </template>
        <template #firstTimeSeen="{ record }">
          {{ formatDate(record.firstTimeSeen) }}
        </template>
        <template #lastTimeSeen="{ record }">
          {{ formatDate(record.lastTimeSeen) }}
        </template>
        <template #userProgress="{ record }">
          {{ formatProgress(record.userProgress) }}
        </template>
      </a-table>
      <TablePager
        :current="page"
        :page-size="pageSize"
        :total="total"
        :records-length="records.length"
        @change="changePage"
        @page-size-change="changePageSize"
      />
    </a-card>
  </div>
</template>

<style scoped>
.chart-controls-row,
.insights-title {
  display: flex;
}

.chart-controls-row {
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.insights-title {
  flex-direction: column;
  gap: 3px;
}

.insights-title strong {
  font-size: 14px;
}

.insights-title small {
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 400;
}

.insights-spin {
  display: block;
  width: 100%;
}

.chart-card :deep(.arco-card-body) {
  min-height: 270px;
}

.chart-card :deep(.arco-empty) {
  padding: 70px 0;
}

@media (max-width: 720px) {
  .chart-controls-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
