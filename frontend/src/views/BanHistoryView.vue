<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import type { EChartsCoreOption } from 'echarts/core'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconCode,
  IconLock,
  IconRefresh,
  IconSearch,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import EChart from '@/components/EChart.vue'
import TablePager from '@/components/TablePager.vue'
import { historyApi } from '@/api'
import { adminApi } from '@/api/admin'
import type {
  BanHistoryQuery,
  BanHistoryRecord,
  BanPeerInsightsResponse,
} from '@/api/types'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useSessionStore } from '@/stores/session'
import { distributionPieOption } from '@/utils/charts'
import { errorMessage, useFormatters } from '@/utils/format'
import { geoIp, shortModuleName } from '@/utils/peerRecords'

const { t } = useI18n()
const router = useRouter()
const session = useSessionStore()
const {
  formatDate,
  formatNumber,
  formatProgress,
} = useFormatters()
const moduleNames = ref<string[]>([])
const structuredRecord = ref<BanHistoryRecord | null>(null)
const structuredVisible = ref(false)
const quickBanVisible = ref(false)
const quickBanSaving = ref(false)
const quickBanUserappId = ref<number | null>(null)
const quickBanReason = ref('')
const peerInsights = ref<BanPeerInsightsResponse | null>(null)
const peerInsightsLoading = ref(false)
const peerInsightDays = ref(7)

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

const columns = computed<TableColumnData[]>(() => {
  const result: TableColumnData[] = [
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
  ]
  if (session.user?.admin) {
    result.splice(1, 0, {
      title: t('banHistory.userAppId'),
      slotName: 'userAppId',
      width: 230,
    })
  }
  return result
})

const peerVersionOption = computed<EChartsCoreOption>(() =>
  distributionPieOption(
    t('banHistory.peerVersionsTitle'),
    (peerInsights.value?.ipVersions ?? []).map((item) => ({
      name: item.name,
      value: item.peers,
    })),
  ),
)

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

function showStructuredData(record: BanHistoryRecord) {
  structuredRecord.value = record
  structuredVisible.value = true
}

function openQuickBan(record: BanHistoryRecord) {
  if (!record.userappId) return
  quickBanUserappId.value = record.userappId
  quickBanReason.value = ''
  quickBanVisible.value = true
}

async function submitQuickBan() {
  if (!quickBanUserappId.value) return
  const reason = quickBanReason.value.trim()
  if (!reason) {
    Message.warning(t('banHistory.disableReasonRequired'))
    return
  }
  quickBanSaving.value = true
  try {
    await adminApi.banApplication(quickBanUserappId.value, reason)
    Message.success(t('banHistory.disabledSuccess'))
    quickBanVisible.value = false
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    quickBanSaving.value = false
  }
}

async function loadPeerInsights() {
  peerInsightsLoading.value = true
  try {
    peerInsights.value = await historyApi.banPeerInsights(peerInsightDays.value)
  } catch {
    peerInsights.value = null
  } finally {
    peerInsightsLoading.value = false
  }
}

onMounted(async () => {
  loadPeerInsights()
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

    <a-row v-if="peerInsights?.enabled" :gutter="[16, 16]">
      <a-col :xs="24" :xl="12">
        <a-card
          class="surface-card chart-card"
          :title="t('banHistory.peerVersionsTitle')"
        >
          <template #extra>
            <a-radio-group
              v-model="peerInsightDays"
              type="button"
              size="small"
              @change="loadPeerInsights"
            >
              <a-radio :value="1">{{ t('banHistory.range1') }}</a-radio>
              <a-radio :value="3">{{ t('banHistory.range3') }}</a-radio>
              <a-radio :value="7">{{ t('banHistory.range7') }}</a-radio>
            </a-radio-group>
          </template>
          <EChart
            v-if="peerInsights.ipVersions?.some((item) => item.peers > 0)"
            :option="peerVersionOption"
            height="330px"
            :loading="peerInsightsLoading"
            :loading-text="t('common.loading')"
          />
          <a-empty v-else :description="t('banHistory.noInsights')" />
        </a-card>
      </a-col>
    </a-row>

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
        :scroll="{ x: session.user?.admin || session.user?.moderator ? 2280 : 2050 }"
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
        <template #userAppId="{ record }">
          <div class="userapp-actions">
            <code>{{ record.userappId ?? 'N/A' }}</code>
            <a-button
              v-if="record.userappId && (session.user?.admin || session.user?.moderator)"
              size="mini"
              status="danger"
              @click="openQuickBan(record)"
            >
              <template #icon><IconLock /></template>
              {{ t('banHistory.quickDisable') }}
            </a-button>
          </div>
        </template>
        <template #torrentId="{ record }">{{ record.torrentId ?? 'N/A' }}</template>
        <template #peerIp="{ record }">
          <div class="peer-ip-cell">
            <code class="mono table-cell-ellipsis" :title="record.peerIp || ''">
              {{ record.peerIp || 'N/A' }}
            </code>
            <a-tooltip v-if="record.peerIp" :content="t('ipQueryPage.search')">
              <a-button
                type="text"
                size="mini"
                :aria-label="t('ipQueryPage.search')"
                @click="router.push({ name: 'ip-query', query: { q: record.peerIp } })"
              >
                <template #icon><IconSearch /></template>
              </a-button>
            </a-tooltip>
          </div>
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
      v-model:visible="quickBanVisible"
      :title="t('banHistory.quickDisableTitle', { id: quickBanUserappId ?? 'N/A' })"
      :footer="false"
      width="560px"
    >
      <a-alert type="warning" show-icon>
        {{ t('banHistory.quickDisableHint') }}
      </a-alert>
      <a-form :model="{ reason: quickBanReason }" layout="vertical" class="quick-ban-form">
        <a-form-item :label="t('banHistory.disableReason')" required>
          <a-textarea
            v-model="quickBanReason"
            :max-length="500"
            show-word-limit
            :auto-size="{ minRows: 4, maxRows: 8 }"
            :placeholder="t('banHistory.disableReasonPlaceholder')"
          />
        </a-form-item>
      </a-form>
      <div class="modal-actions">
        <a-button @click="quickBanVisible = false">{{ t('common.cancel') }}</a-button>
        <a-button status="danger" :loading="quickBanSaving" @click="submitQuickBan">
          {{ t('banHistory.quickDisable') }}
        </a-button>
      </div>
    </a-modal>

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
.peer-ip-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.peer-ip-cell code {
  flex: 1;
  min-width: 0;
}

.peer-ip-cell :deep(.arco-btn) {
  flex-shrink: 0;
}

.module-tag {
  display: inline-flex;
  max-width: 100%;
  vertical-align: middle;
}

.chart-card :deep(.arco-card-body) {
  min-height: 270px;
}

.chart-card :deep(.arco-empty) {
  padding: 70px 0;
}

.geoip-text {
  font-size: 12px;
}

.userapp-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.userapp-actions code {
  min-width: 62px;
  color: var(--color-text-2);
  font-size: 12px;
}

.quick-ban-form {
  margin-top: 18px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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
  border-radius: var(--app-radius);
}
</style>
