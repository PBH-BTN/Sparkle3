<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TableColumnData } from '@arco-design/web-vue'
import { uiApi } from '@/api'
import type { SubmissionRecord, SubmissionType } from '@/api/types'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import TablePager from '@/components/TablePager.vue'
import { useFormatters } from '@/utils/format'
import { geoIp, shortModuleName } from '@/utils/peerRecords'
import { IconCode } from '@arco-design/web-vue/es/icon'

const { t } = useI18n()
const { formatDate, formatBytes, formatProgress } = useFormatters()
const type = ref<SubmissionType>('bans')
const days = ref(3)
const page = ref(1)
const pageSize = ref(20)
const records = ref<SubmissionRecord[]>([])
const total = ref(0)
const loading = ref(false)
const failed = ref(false)
const structuredRecord = ref<SubmissionRecord | null>(null)
const structuredVisible = ref(false)
let requestId = 0

const columns = computed<TableColumnData[]>(() => {
  const column = (field: string, label: string, width = 170): TableColumnData => ({
    title: t(label), dataIndex: field, slotName: field, width, ellipsis: true, tooltip: true,
  })
  const source = column('application', 'submissions.application', 190)
  if (type.value === 'clients') {
    return [source, column('submittedAt', 'clientDiscovery.foundAt', 180),
      column('peerId', 'clientDiscovery.peerId', 240), column('clientName', 'clientDiscovery.peerClientName'),
      column('clientType', 'clientDiscovery.clientType'), column('clientVersion', 'clientDiscovery.clientSemver')]
  }
  const prefix = type.value === 'bans' ? 'banHistory.' : 'swarmTracker.'
  const common = [column('torrentId', prefix + 'torrentId', 120), column('peerIp', prefix + 'peerIp'),
    column('peerPort', prefix + 'peerPort', 100), column('peerId', prefix + 'peerId'),
    column('clientName', prefix + 'peerClientName')]
  if (type.value === 'bans') {
    return [source, column('populateTime', 'banHistory.populateTime', 180), ...common,
      column('peerProgress', 'banHistory.progress', 100), column('peerGeoip', 'banHistory.geoIp'),
      column('moduleName', 'banHistory.moduleName', 220), column('rule', 'banHistory.rule', 220),
      column('description', 'banHistory.descriptionField', 300),
      column('reporterProgress', 'swarmTracker.userTaskProgress', 130),
      column('flags', 'swarmTracker.flags', 100), column('uploaded', 'submissions.uploaded'),
      column('downloaded', 'submissions.downloaded'), column('insertTime', 'submissions.time', 180),
      { title: '', slotName: 'details', width: 70, fixed: 'right' }]
  }
  return [source, ...common, column('peerProgress', 'swarmTracker.peerTaskProgress', 130),
    column('downloaded', 'swarmTracker.fromPeerTraffic'), column('uploaded', 'swarmTracker.toPeerTraffic'),
    column('flags', 'swarmTracker.flags', 100), column('firstTimeSeen', 'swarmTracker.firstSeen', 180),
    column('lastTimeSeen', 'swarmTracker.lastSeen', 180), column('userProgress', 'swarmTracker.userTaskProgress', 130)]
})
const tableWidth = computed(() => columns.value.reduce((sum, column) => sum + Number(column.width ?? 170), 0))

function showStructuredData(record: SubmissionRecord) {
  structuredRecord.value = record
  structuredVisible.value = true
}

async function load() {
  const id = ++requestId
  loading.value = true
  failed.value = false
  records.value = []
  total.value = 0
  try {
    const result = await uiApi.submissions({ type: type.value, days: days.value, page: page.value, size: pageSize.value })
    if (id !== requestId) return
    records.value = result.records
    total.value = result.total
  } catch {
    if (id === requestId) failed.value = true
  } finally {
    if (id === requestId) loading.value = false
  }
}
function search() {
  page.value = 1
  void load()
}
function changePage(value: number) {
  page.value = value
  void load()
}
function changePageSize(value: number) {
  pageSize.value = value
  search()
}
watch([type, days], search)
onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('submissions.title')" :description="t('submissions.description')" />
    <a-card class="surface-card" :bordered="false">
      <div class="submission-filters">
        <a-radio-group v-model="days" type="button" :aria-label="t('submissions.range')">
          <a-radio v-for="value in [1, 3, 7]" :key="value" :value="value">
            {{ t('submissions.days', { days: value }) }}
          </a-radio>
        </a-radio-group>
        <a-button :loading="loading" @click="load">{{ t('submissions.refresh') }}</a-button>
      </div>
      <a-tabs v-model:active-key="type">
        <a-tab-pane v-for="value in (['bans', 'clients', 'swarm'] as const)" :key="value" :title="t('submissions.' + value)" />
      </a-tabs>
      <p class="submission-note">{{ t('submissions.note') }}</p>
      <a-alert v-if="failed" type="error">
        {{ t('submissions.failed') }}
        <template #action><a-button size="small" @click="load">{{ t('submissions.retry') }}</a-button></template>
      </a-alert>
      <a-table v-else :columns="columns" :data="records" :loading="loading" :pagination="false" :scroll="{ x: tableWidth }" row-key="id" stripe>
        <template v-for="field in ['submittedAt', 'insertTime', 'populateTime', 'firstTimeSeen', 'lastTimeSeen']" :key="field" #[field]="{ record }">
          {{ formatDate(record[field]) }}
        </template>
        <template v-for="field in ['peerProgress', 'reporterProgress', 'userProgress']" :key="field" #[field]="{ record }">
          {{ formatProgress(record[field]) }}
        </template>
        <template #peerPort="{ record }">{{ record.peerPort ?? 'N/A' }}</template>
        <template #peerIp="{ record }"><code class="mono">{{ record.peerIp || 'N/A' }}</code></template>
        <template #peerId="{ record }"><span class="table-cell-ellipsis mono" :title="record.peerId || ''">{{ record.peerId || 'N/A' }}</span></template>
        <template #flags="{ record }"><code class="mono">{{ record.flags || 'N/A' }}</code></template>
        <template #peerGeoip="{ record }"><span class="table-cell-ellipsis" :title="geoIp(record)">{{ geoIp(record) }}</span></template>
        <template #moduleName="{ record }">
          <a-tooltip :content="record.moduleName || 'N/A'">
            <a-tag color="arcoblue" nowrap>{{ shortModuleName(record.moduleName) }}</a-tag>
          </a-tooltip>
        </template>
        <template #details="{ record }">
          <a-tooltip :content="t('banHistory.structuredData')">
            <a-button type="text" size="mini" :disabled="!record.structuredData" @click="showStructuredData(record)">
              <template #icon><IconCode /></template>
            </a-button>
          </a-tooltip>
        </template>
        <template #uploaded="{ record }">{{ formatBytes(record.uploaded) }}</template>
        <template #downloaded="{ record }">{{ formatBytes(record.downloaded) }}</template>
        <template #empty><EmptyState :title="t('submissions.empty')" /></template>
      </a-table>
      <TablePager v-if="!failed" :current="page" :page-size="pageSize" :total="total" :records-length="records.length" @change="changePage" @page-size-change="changePageSize" />
    </a-card>
    <a-modal v-model:visible="structuredVisible" :title="t('banHistory.structuredData')" :footer="false" width="760px">
      <pre class="structured-data">{{ JSON.stringify(structuredRecord?.structuredData, null, 2) }}</pre>
    </a-modal>
  </div>
</template>

<style scoped>
.submission-filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.structured-data {
  max-height: 60vh;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.submission-note {
  color: var(--color-text-3);
  margin: 0 0 16px;
  line-height: 1.6;
}
@media (max-width: 980px) {
  .submission-filters { flex-wrap: wrap; }
}
@media (max-width: 720px) {
  .submission-filters { align-items: stretch; flex-direction: column; }
}
</style>
