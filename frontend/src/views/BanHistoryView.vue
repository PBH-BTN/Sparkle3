<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import type { TableColumnData } from '@arco-design/web-vue'
import { IconCode, IconRefresh, IconSearch } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import TablePager from '@/components/TablePager.vue'
import { historyApi } from '@/api'
import type { BanHistoryQuery, BanHistoryRecord } from '@/api/types'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useFormatters } from '@/utils/format'

const { t } = useI18n()
const {
  formatDate,
  formatNumber,
  formatProgress,
} = useFormatters()
const moduleNames = ref<string[]>([])
const structuredRecord = ref<BanHistoryRecord | null>(null)
const structuredVisible = ref(false)

const form = reactive<{
  timeRange: Array<number | string | Date> | undefined
  torrentId: number | undefined
  peerIp: string
  peerPort: number | undefined
  peerId: string
  peerClientName: string
  moduleName: string
  rule: string
  description: string
}>({
  timeRange: undefined,
  torrentId: undefined,
  peerIp: '',
  peerPort: undefined,
  peerId: '',
  peerClientName: '',
  moduleName: '',
  rule: '',
  description: '',
})

type BanQueryFilters = Omit<BanHistoryQuery, 'page' | 'size' | 'sortBy' | 'sortOrder'>

function buildQuery(): BanQueryFilters {
  return {
    insertTimeStart:
      form.timeRange && form.timeRange[0]
        ? dayjs(form.timeRange[0]).toISOString()
        : undefined,
    insertTimeEnd:
      form.timeRange && form.timeRange[1]
        ? dayjs(form.timeRange[1]).toISOString()
        : undefined,
    torrentId: form.torrentId,
    peerIp: form.peerIp.trim() || undefined,
    peerPort: form.peerPort,
    peerId: form.peerId.trim() || undefined,
    peerClientName: form.peerClientName.trim() || undefined,
    moduleName: form.moduleName || undefined,
    rule: form.rule.trim() || undefined,
    description: form.description.trim() || undefined,
  }
}

const {
  records,
  loading,
  page,
  pageSize,
  total,
  search,
  run,
  changePage,
  changePageSize,
  changeSort,
} = usePagedQuery<BanHistoryRecord, BanQueryFilters>(
  historyApi.queryBanHistory,
  buildQuery,
  { pageSize: 50, sortBy: 'populate_time' },
)

const columns = computed<TableColumnData[]>(() => [
  {
    title: t('banHistory.populateTime'),
    dataIndex: 'populate_time',
    slotName: 'insertTime',
    width: 180,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('banHistory.torrentId'),
    dataIndex: 'torrent_id',
    slotName: 'torrentId',
    width: 120,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('banHistory.peerIp'),
    dataIndex: 'peer_ip',
    slotName: 'peerIp',
    width: 170,
    ellipsis: true,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('banHistory.peerPort'),
    dataIndex: 'peer_port',
    slotName: 'peerPort',
    width: 100,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('banHistory.peerId'),
    dataIndex: 'peer_id',
    slotName: 'peerId',
    width: 170,
    ellipsis: true,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('banHistory.peerClientName'),
    dataIndex: 'peer_client_name',
    slotName: 'peerClientName',
    width: 170,
    ellipsis: true,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('banHistory.progress'),
    dataIndex: 'peer_progress',
    slotName: 'progress',
    width: 100,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  { title: t('banHistory.geoIp'), slotName: 'geoIp', width: 170, ellipsis: true },
  {
    title: t('banHistory.moduleName'),
    dataIndex: 'module_name',
    slotName: 'moduleName',
    width: 220,
    ellipsis: true,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('banHistory.rule'),
    dataIndex: 'rule',
    slotName: 'rule',
    width: 220,
    ellipsis: true,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('banHistory.descriptionField'),
    dataIndex: 'description',
    slotName: 'descriptionField',
    width: 300,
    ellipsis: true,
  },
  { title: '', slotName: 'details', width: 70, fixed: 'right' },
])

function reset() {
  form.timeRange = undefined
  form.torrentId = undefined
  form.peerIp = ''
  form.peerPort = undefined
  form.peerId = ''
  form.peerClientName = ''
  form.moduleName = ''
  form.rule = ''
  form.description = ''
  search()
}

function shortModuleName(name: string | null) {
  if (!name) return 'N/A'
  const prefix = 'com.ghostchu.peerbanhelper.module.impl.rule.'
  return name.startsWith(prefix) ? name.slice(prefix.length) : name
}

function geoIp(record: BanHistoryRecord) {
  if (!record.peerGeoip) return 'N/A'
  const parts = [
    record.peerGeoip.countryIso,
    record.peerGeoip.cityCnProvince,
    record.peerGeoip.cityCnCity,
    record.peerGeoip.isp,
  ].filter(Boolean)
  return parts.length ? parts.join(', ') : 'N/A'
}

function showStructuredData(record: BanHistoryRecord) {
  structuredRecord.value = record
  structuredVisible.value = true
}

onMounted(async () => {
  const [optionsResult] = await Promise.allSettled([
    historyApi.banHistoryOptions(),
    run(),
  ])
  if (optionsResult.status === 'fulfilled') {
    moduleNames.value = optionsResult.value.moduleNames
  }
})
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('banHistory.title')" :description="t('banHistory.description')" />

    <a-card class="surface-card filter-card">
      <a-form :model="form" layout="vertical" @submit="search">
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('banHistory.torrentId')">
              <a-input-number v-model="form.torrentId" :min="0" hide-button allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('banHistory.peerIp')">
              <a-input v-model="form.peerIp" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('banHistory.peerPort')">
              <a-input-number
                v-model="form.peerPort"
                :min="0"
                :max="65535"
                hide-button
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('banHistory.peerId')">
              <a-input v-model="form.peerId" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('banHistory.peerClientName')">
              <a-input v-model="form.peerClientName" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('banHistory.moduleName')">
              <a-select
                v-model="form.moduleName"
                :placeholder="t('banHistory.allModules')"
                allow-search
                allow-clear
              >
                <a-option
                  v-for="moduleName in moduleNames"
                  :key="moduleName"
                  :value="moduleName"
                >
                  {{ shortModuleName(moduleName) }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('banHistory.rule')">
              <a-input v-model="form.rule" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('banHistory.descriptionField')">
              <a-input v-model="form.description" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="24" :lg="12">
            <a-form-item :label="t('banHistory.timeRange')">
              <a-range-picker
                v-model="form.timeRange"
                show-time
                value-format="timestamp"
                style="width: 100%"
              />
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
        :scroll="{ x: 2050 }"
        row-key="id"
        stripe
        @sorter-change="changeSort"
      >
        <template #empty>
          <EmptyState
            :title="t('banHistory.emptyTitle')"
            :description="t('banHistory.emptyText')"
          />
        </template>
        <template #insertTime="{ record }">{{ formatDate(record.insertTime) }}</template>
        <template #torrentId="{ record }">{{ record.torrentId ?? 'N/A' }}</template>
        <template #peerIp="{ record }">
          <code class="mono table-cell-ellipsis" :title="record.peerIp || ''">
            {{ record.peerIp || 'N/A' }}
          </code>
        </template>
        <template #peerPort="{ record }">{{ record.peerPort ?? 'N/A' }}</template>
        <template #peerId="{ record }">
          <span class="table-cell-ellipsis mono" :title="record.peerId || ''">
            {{ record.peerId || 'N/A' }}
          </span>
        </template>
        <template #peerClientName="{ record }">
          <span class="table-cell-ellipsis" :title="record.peerClientName || ''">
            {{ record.peerClientName || 'N/A' }}
          </span>
        </template>
        <template #progress="{ record }">{{ formatProgress(record.peerProgress) }}</template>
        <template #geoIp="{ record }">
          <span class="geoip-text table-cell-ellipsis" :title="geoIp(record)">
            {{ geoIp(record) }}
          </span>
        </template>
        <template #moduleName="{ record }">
          <a-tooltip :content="record.moduleName || 'N/A'">
            <a-tag color="arcoblue" nowrap class="module-tag">
              {{ shortModuleName(record.moduleName) }}
            </a-tag>
          </a-tooltip>
        </template>
        <template #rule="{ record }">
          <span class="table-cell-ellipsis" :title="record.rule || ''">
            {{ record.rule || 'N/A' }}
          </span>
        </template>
        <template #descriptionField="{ record }">
          <span class="table-cell-ellipsis" :title="record.description || ''">
            {{ record.description || 'N/A' }}
          </span>
        </template>
        <template #details="{ record }">
          <a-tooltip :content="t('banHistory.structuredData')">
            <a-button
              type="text"
              size="mini"
              :disabled="!record.structuredData"
              @click="showStructuredData(record)"
            >
              <template #icon><IconCode /></template>
            </a-button>
          </a-tooltip>
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

    <a-modal
      v-model:visible="structuredVisible"
      :title="t('banHistory.structuredData')"
      :footer="false"
      width="760px"
    >
      <pre class="structured-data">{{ JSON.stringify(structuredRecord?.structuredData, null, 2) }}</pre>
    </a-modal>
  </div>
</template>

<style scoped>
.table-card :deep(.arco-table-td) {
  overflow: hidden;
}

.table-cell-ellipsis {
  display: block;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.module-tag {
  display: inline-flex;
  max-width: 100%;
  vertical-align: middle;
}

.geoip-text {
  font-size: 12px;
}

.structured-data {
  max-height: 520px;
  padding: 16px;
  overflow: auto;
  color: var(--color-text-2);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  background: var(--color-fill-1);
  border-radius: 6px;
}
</style>
