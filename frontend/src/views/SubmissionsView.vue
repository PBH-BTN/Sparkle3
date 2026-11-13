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

const { t } = useI18n()
const { formatDate, formatBytes } = useFormatters()
const type = ref<SubmissionType>('bans')
const days = ref(3)
const page = ref(1)
const pageSize = ref(20)
const records = ref<SubmissionRecord[]>([])
const total = ref(0)
const loading = ref(false)
const failed = ref(false)
let requestId = 0

const columns = computed<TableColumnData[]>(() => {
  const fields = ['application', 'time', 'peerId', 'client',
    ...(type.value === 'bans' ? ['module'] : []),
    ...(type.value === 'clients' ? ['clientType', 'version'] : ['torrent', 'uploaded', 'downloaded'])]
  const dataIndexes: Record<string, string> = {
    time: 'submittedAt', client: 'clientName', module: 'moduleName',
    version: 'clientVersion', torrent: 'torrentId',
  }
  return fields.map(field => ({
    title: t('submissions.' + field), dataIndex: dataIndexes[field] ?? field,
    slotName: field, width: field === 'peerId' ? 240 : 190, ellipsis: true, tooltip: true,
  }))
})

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
      <a-table v-else :columns="columns" :data="records" :loading="loading" :pagination="false" :scroll="{ x: '100%' }" row-key="id">
        <template #time="{ record }">{{ formatDate(record.submittedAt) }}</template>
        <template #uploaded="{ record }">{{ formatBytes(record.uploaded) }}</template>
        <template #downloaded="{ record }">{{ formatBytes(record.downloaded) }}</template>
        <template #empty><EmptyState :title="t('submissions.empty')" /></template>
      </a-table>
      <TablePager v-if="!failed" :current="page" :page-size="pageSize" :total="total" :records-length="records.length" @change="changePage" @page-size-change="changePageSize" />
    </a-card>
  </div>
</template>

<style scoped>
.submission-filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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
