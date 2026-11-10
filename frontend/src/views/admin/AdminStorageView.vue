<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconDelete,
  IconRefresh,
  IconSafe,
  IconSearch,
  IconSettings,
  IconStorage,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  BanHistoryDeletePreview,
  StorageOverview,
  TableStorage,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatBytes, formatDate, formatNumber } = useFormatters()
const overview = ref<StorageOverview | null>(null)
const loading = ref(true)
const search = ref('')
const typeFilter = ref<'all' | 'hypertable' | 'regular'>('all')

const drawerVisible = ref(false)
const selectedTable = ref<TableStorage | null>(null)
const compressionSaving = ref(false)
const retentionSaving = ref(false)
const compressionForm = reactive({
  enabled: false,
  compressAfterDays: 30,
  scheduleHours: 12,
  segmentBy: '',
  orderBy: '',
})
const retentionForm = reactive({
  enabled: false,
  dropAfterDays: 180,
  scheduleHours: 24,
})

const cleanupVisible = ref(false)
const cleanupPreview = ref<BanHistoryDeletePreview | null>(null)
const cleanupLoading = ref(false)
const cleanupDeleting = ref(false)
const cleanupForm = reactive<{
  cutoff: number | undefined
  confirmation: string
}>({
  cutoff: dayjs().subtract(90, 'day').valueOf(),
  confirmation: '',
})

const filteredTables = computed(() => {
  const term = search.value.trim().toLowerCase()
  return (overview.value?.tables ?? []).filter((table) => {
    if (typeFilter.value === 'hypertable' && !table.hypertable) return false
    if (typeFilter.value === 'regular' && table.hypertable) return false
    const qualifiedName = `${table.schemaName}.${table.tableName}`.toLowerCase()
    return !term || qualifiedName.includes(term)
  })
})

const columns = computed<TableColumnData[]>(() => [
  { title: t('admin.storage.table'), slotName: 'table', width: 240 },
  { title: t('admin.storage.type'), slotName: 'type', width: 130 },
  { title: t('admin.storage.rows'), dataIndex: 'estimatedRows', slotName: 'rows', width: 105 },
  {
    title: t('admin.storage.uncompressedSize'),
    dataIndex: 'uncompressedBytes',
    slotName: 'uncompressed',
    width: 125,
  },
  {
    title: t('admin.storage.compressedSize'),
    dataIndex: 'compressedBytes',
    slotName: 'compressed',
    width: 125,
  },
  { title: t('admin.storage.total'), dataIndex: 'totalBytes', slotName: 'total', width: 115 },
  { title: t('admin.storage.chunks'), dataIndex: 'chunks', slotName: 'chunks', width: 85 },
  { title: t('admin.storage.policies'), slotName: 'policies', width: 160 },
])

async function load() {
  loading.value = true
  try {
    overview.value = await adminApi.storage()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function openManage(table: TableStorage) {
  selectedTable.value = table
  compressionForm.enabled = table.compressionPolicy.enabled
  compressionForm.compressAfterDays = Math.max(
    1,
    Math.round(table.compressionPolicy.afterDays ?? 30),
  )
  compressionForm.scheduleHours = Math.max(
    1,
    Math.round(table.compressionPolicy.scheduleHours ?? 12),
  )
  compressionForm.segmentBy = table.segmentBy ?? ''
  compressionForm.orderBy =
    table.orderBy || (table.timeColumn ? `${table.timeColumn} DESC` : '')
  retentionForm.enabled = table.retentionPolicy.enabled
  retentionForm.dropAfterDays = Math.max(
    1,
    Math.round(table.retentionPolicy.afterDays ?? 180),
  )
  retentionForm.scheduleHours = Math.max(
    1,
    Math.round(table.retentionPolicy.scheduleHours ?? 24),
  )
  drawerVisible.value = true
}

async function saveCompressionPolicy() {
  if (!selectedTable.value) return
  if (!compressionForm.orderBy.trim()) {
    Message.warning(t('admin.storage.invalid'))
    return
  }
  compressionSaving.value = true
  try {
    const updated = await adminApi.updateCompressionPolicy(
      selectedTable.value.schemaName,
      selectedTable.value.tableName,
      {
        enabled: compressionForm.enabled,
        compressAfterDays: compressionForm.compressAfterDays,
        scheduleHours: compressionForm.scheduleHours,
        segmentBy: compressionForm.segmentBy,
        orderBy: compressionForm.orderBy,
      },
    )
    selectedTable.value = updated
    Message.success(t('admin.storage.policyUpdated'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    compressionSaving.value = false
  }
}

async function saveRetentionPolicy() {
  if (!selectedTable.value) return
  retentionSaving.value = true
  try {
    const updated = await adminApi.updateRetentionPolicy(
      selectedTable.value.schemaName,
      selectedTable.value.tableName,
      {
        enabled: retentionForm.enabled,
        dropAfterDays: retentionForm.dropAfterDays,
        scheduleHours: retentionForm.scheduleHours,
      },
    )
    selectedTable.value = updated
    Message.success(t('admin.storage.policyUpdated'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    retentionSaving.value = false
  }
}

function openCleanup() {
  cleanupVisible.value = true
  cleanupPreview.value = null
  cleanupForm.confirmation = ''
}

async function previewCleanup() {
  if (!cleanupForm.cutoff) return
  cleanupLoading.value = true
  try {
    cleanupPreview.value = await adminApi.previewBanHistoryDelete(
      dayjs(cleanupForm.cutoff).toISOString(),
    )
    cleanupForm.confirmation = ''
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    cleanupLoading.value = false
  }
}

async function deleteBanHistory() {
  if (!cleanupForm.cutoff || !cleanupPreview.value) return
  if (cleanupForm.confirmation !== t('admin.storage.confirmationPhrase')) {
    Message.warning(t('admin.storage.confirmationMismatch'))
    return
  }
  cleanupDeleting.value = true
  try {
    const result = await adminApi.deleteBanHistory(
      dayjs(cleanupForm.cutoff).toISOString(),
      cleanupForm.confirmation,
    )
    const detail = cleanupPreview.value.hypertable
      ? t('admin.storage.affectedChunks', { count: result.affectedChunks })
      : t('admin.storage.deletedRows', { count: result.deletedRows ?? 0 })
    Message.success(`${t('admin.storage.deleted')} ${detail}`)
    cleanupVisible.value = false
    cleanupPreview.value = null
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    cleanupDeleting.value = false
  }
}

function modeLabel(mode: string) {
  return t(`admin.storage.compressionMode.${mode}`)
}

function policyLabel(policy: TableStorage['compressionPolicy']) {
  return policy.enabled
    ? policy.after || t('duration.days', { count: Math.round(policy.afterDays ?? 0) })
    : t('admin.storage.noPolicy')
}

watch(
  () => cleanupForm.cutoff,
  () => {
    cleanupPreview.value = null
    cleanupForm.confirmation = ''
  },
)

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('admin.storage.title')" :description="t('admin.storage.description')">
      <template #actions>
        <a-button status="danger" @click="openCleanup">
          <template #icon><IconDelete /></template>
          {{ t('admin.storage.banHistoryTitle') }}
        </a-button>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.storage.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :loading="loading" class="storage-spin">
      <template v-if="overview">
        <div class="summary-grid">
          <div class="summary-card">
            <span>{{ t('admin.storage.timescaleVersion') }}</span>
            <strong>{{ overview.timescaleVersion || t('admin.storage.notInstalled') }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('admin.storage.tableCount') }}</span>
            <strong>{{ formatNumber(overview.tableCount) }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('admin.storage.hypertableCount') }}</span>
            <strong>{{ formatNumber(overview.hypertableCount) }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('admin.storage.totalSize') }}</span>
            <strong>{{ formatBytes(overview.totalBytes) }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('admin.storage.uncompressedSize') }}</span>
            <strong>{{ formatBytes(overview.uncompressedBytes) }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('admin.storage.compressedSize') }}</span>
            <strong>{{ formatBytes(overview.compressedBytes) }}</strong>
          </div>
        </div>

        <a-card class="surface-card filter-card">
          <a-form :model="{ search, typeFilter }" layout="vertical">
            <a-row :gutter="16">
              <a-col :xs="24" :md="14">
                <a-form-item :label="t('admin.storage.search')">
                  <a-input v-model="search" allow-clear>
                    <template #prefix><IconSearch /></template>
                  </a-input>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-item :label="t('admin.storage.type')">
                  <a-select v-model="typeFilter">
                    <a-option value="all">{{ t('admin.storage.allTypes') }}</a-option>
                    <a-option value="hypertable">{{ t('admin.storage.hypertable') }}</a-option>
                    <a-option value="regular">{{ t('admin.storage.regularTable') }}</a-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-card>

        <a-card class="surface-card table-card">
          <a-table
            :columns="columns"
            :data="filteredTables"
            :pagination="false"
            :scroll="{ x: 1200, y: 620 }"
            row-key="qualifiedName"
            stripe
          >
            <template #empty><a-empty :description="t('admin.storage.noData')" /></template>
            <template #table="{ record }">
              <div class="table-name">
                <div>
                  <strong>{{ record.tableName }}</strong>
                  <span>{{ record.schemaName }}</span>
                </div>
                <a-button
                  v-if="record.hypertable"
                  type="text"
                  size="mini"
                  @click="openManage(record)"
                >
                  <template #icon><IconSettings /></template>
                  {{ t('admin.storage.action') }}
                </a-button>
              </div>
            </template>
            <template #type="{ record }">
              <div class="type-cell">
                <a-tag :color="record.hypertable ? 'arcoblue' : 'gray'">
                  {{ record.hypertable ? t('admin.storage.hypertable') : t('admin.storage.regularTable') }}
                </a-tag>
                <span v-if="record.hypertable">{{ modeLabel(record.compressionMode) }}</span>
              </div>
            </template>
            <template #rows="{ record }">{{ formatNumber(record.estimatedRows) }}</template>
            <template #uncompressed="{ record }">{{ formatBytes(record.uncompressedBytes) }}</template>
            <template #compressed="{ record }">
              {{ record.hypertable ? formatBytes(record.compressedBytes) : '—' }}
            </template>
            <template #total="{ record }">
              <strong>{{ formatBytes(record.totalBytes) }}</strong>
            </template>
            <template #chunks="{ record }">
              <span v-if="record.hypertable">
                {{ record.compressedChunks }}/{{ record.chunks }}
              </span>
              <span v-else>—</span>
            </template>
            <template #policies="{ record }">
              <div v-if="record.hypertable" class="policy-tags">
                <a-tooltip :content="`${t('admin.storage.compressionPolicy')}: ${policyLabel(record.compressionPolicy)}`">
                  <a-tag :color="record.compressionPolicy.enabled ? 'green' : 'gray'">
                    {{ t('admin.storage.shortCompression') }}
                    {{ record.compressionPolicy.enabled ? 'ON' : 'OFF' }}
                  </a-tag>
                </a-tooltip>
                <a-tooltip :content="`${t('admin.storage.retentionPolicy')}: ${policyLabel(record.retentionPolicy)}`">
                  <a-tag :color="record.retentionPolicy.enabled ? 'orange' : 'gray'">
                    {{ t('admin.storage.shortRetention') }}
                    {{ record.retentionPolicy.enabled ? 'ON' : 'OFF' }}
                  </a-tag>
                </a-tooltip>
              </div>
              <span v-else>—</span>
            </template>
          </a-table>
          <div class="storage-note">
            <IconStorage />
            {{ t('admin.storage.storageRefreshNote') }}
          </div>
        </a-card>
      </template>
    </a-spin>

    <a-drawer
      v-model:visible="drawerVisible"
      :title="t('admin.storage.manageTitle', {
        table: selectedTable ? `${selectedTable.schemaName}.${selectedTable.tableName}` : '',
      })"
      :width="780"
      :footer="false"
    >
      <template v-if="selectedTable">
        <a-card class="surface-card stats-card">
          <template #title>{{ t('admin.storage.tableStats') }}</template>
          <a-descriptions :column="2" bordered size="small">
            <a-descriptions-item :label="t('admin.storage.heap')">
              {{ formatBytes(selectedTable.heapBytes) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.storage.index')">
              {{ formatBytes(selectedTable.indexBytes) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.storage.toast')">
              {{ formatBytes(selectedTable.toastBytes) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.storage.total')">
              {{ formatBytes(selectedTable.totalBytes) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.storage.uncompressedSize')">
              {{ formatBytes(selectedTable.uncompressedBytes) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.storage.compressedSize')">
              {{ formatBytes(selectedTable.compressedBytes) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.storage.chunks')" :span="2">
              {{
                t('admin.storage.chunkSummary', {
                  total: selectedTable.chunks,
                  compressed: selectedTable.compressedChunks,
                })
              }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card class="surface-card policy-card">
          <template #title>{{ t('admin.storage.compressionPolicy') }}</template>
          <p class="policy-hint">{{ t('admin.storage.compressionPolicyHint') }}</p>
          <div v-if="selectedTable.compressionPolicy.enabled" class="current-policy">
            <a-tag color="green">
              {{ selectedTable.compressionPolicy.after || '—' }}
            </a-tag>
            <span v-if="selectedTable.compressionPolicy.nextStart">
              {{
                t('admin.storage.nextRun', {
                  time: formatDate(selectedTable.compressionPolicy.nextStart),
                })
              }}
            </span>
          </div>
          <a-form :model="compressionForm" layout="vertical">
            <a-form-item :label="t('admin.storage.enableCompressionPolicy')">
              <a-switch v-model="compressionForm.enabled" />
            </a-form-item>
            <a-row :gutter="16">
              <a-col :xs="24" :md="8">
                <a-form-item :label="t('admin.storage.compressAfterDays')">
                  <a-input-number
                    v-model="compressionForm.compressAfterDays"
                    :min="1"
                    :max="36500"
                  />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-item :label="t('admin.storage.scheduleHours')">
                  <a-input-number
                    v-model="compressionForm.scheduleHours"
                    :min="1"
                    :max="720"
                  />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-item :label="t('admin.storage.segmentBy')">
                  <a-input v-model="compressionForm.segmentBy" placeholder="userapps_id, torrent_id" />
                </a-form-item>
              </a-col>
              <a-col :xs="24">
                <a-form-item :label="t('admin.storage.orderBy')">
                  <a-input v-model="compressionForm.orderBy" placeholder="populate_time DESC" />
                </a-form-item>
              </a-col>
            </a-row>
            <div class="policy-actions">
              <a-button type="primary" :loading="compressionSaving" @click="saveCompressionPolicy">
                {{ t('admin.storage.saveCompression') }}
              </a-button>
            </div>
          </a-form>
        </a-card>

        <a-card class="surface-card policy-card">
          <template #title>{{ t('admin.storage.retentionPolicy') }}</template>
          <p class="policy-hint">{{ t('admin.storage.retentionPolicyHint') }}</p>
          <div v-if="selectedTable.retentionPolicy.enabled" class="current-policy">
            <a-tag color="orange">
              {{ selectedTable.retentionPolicy.after || '—' }}
            </a-tag>
            <span v-if="selectedTable.retentionPolicy.nextStart">
              {{
                t('admin.storage.nextRun', {
                  time: formatDate(selectedTable.retentionPolicy.nextStart),
                })
              }}
            </span>
          </div>
          <a-form :model="retentionForm" layout="vertical">
            <a-form-item :label="t('admin.storage.enableRetentionPolicy')">
              <a-switch v-model="retentionForm.enabled" />
            </a-form-item>
            <a-row :gutter="16">
              <a-col :xs="24" :md="12">
                <a-form-item :label="t('admin.storage.dropAfterDays')">
                  <a-input-number
                    v-model="retentionForm.dropAfterDays"
                    :min="1"
                    :max="36500"
                  />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item :label="t('admin.storage.scheduleHours')">
                  <a-input-number
                    v-model="retentionForm.scheduleHours"
                    :min="1"
                    :max="720"
                  />
                </a-form-item>
              </a-col>
            </a-row>
            <div class="policy-actions">
              <a-button type="primary" :loading="retentionSaving" @click="saveRetentionPolicy">
                {{ t('admin.storage.saveRetention') }}
              </a-button>
            </div>
          </a-form>
        </a-card>
      </template>
    </a-drawer>

    <a-modal
      v-model:visible="cleanupVisible"
      :title="t('admin.storage.banHistoryTitle')"
      :footer="false"
      width="680px"
      unmount-on-close
    >
      <a-alert type="warning" show-icon>
        <template #title>{{ t('admin.storage.deleteWarning') }}</template>
        {{ t('admin.storage.banHistoryDescription') }}
      </a-alert>
      <a-form :model="cleanupForm" layout="vertical" class="cleanup-form">
        <a-form-item :label="t('admin.storage.cutoff')">
          <a-date-picker
            v-model="cleanupForm.cutoff"
            show-time
            value-format="timestamp"
            style="width: 100%"
          />
        </a-form-item>
        <a-button :loading="cleanupLoading" @click="previewCleanup">
          <template #icon><IconSafe /></template>
          {{ t('admin.storage.preview') }}
        </a-button>

        <div v-if="cleanupPreview" class="cleanup-preview">
          <p v-if="cleanupPreview.hypertable">
            {{
              t('admin.storage.chunkPreview', {
                chunks: cleanupPreview.chunks,
                compressed: cleanupPreview.compressedChunks,
              })
            }}
          </p>
          <p v-else>
            {{ t('admin.storage.rowPreview', { rows: cleanupPreview.estimatedRows ?? 0 }) }}
          </p>
          <p v-if="cleanupPreview.oldestRangeStart && cleanupPreview.newestRangeEnd">
            {{
              t('admin.storage.rangePreview', {
                start: formatDate(cleanupPreview.oldestRangeStart),
                end: formatDate(cleanupPreview.newestRangeEnd),
              })
            }}
          </p>
          <a-form-item
            :label="t('admin.storage.confirmationLabel', {
              phrase: t('admin.storage.confirmationPhrase'),
            })"
          >
            <a-input v-model="cleanupForm.confirmation" />
          </a-form-item>
          <a-button
            status="danger"
            type="primary"
            long
            :loading="cleanupDeleting"
            :disabled="cleanupForm.confirmation !== t('admin.storage.confirmationPhrase')"
            @click="deleteBanHistory"
          >
            <template #icon><IconDelete /></template>
            {{ t('admin.storage.deleteAction') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.storage-spin {
  display: block;
  width: 100%;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  padding: 15px 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.summary-card span {
  color: var(--color-text-3);
  font-size: 11px;
}

.summary-card strong {
  margin-top: 7px;
  color: var(--color-text-1);
  font-size: 20px;
}

.table-name {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.table-name > div {
  display: flex;
  flex-direction: column;
}

.table-name span {
  color: var(--color-text-3);
  font-size: 11px;
}

.type-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.type-cell span {
  color: var(--color-text-3);
  font-size: 10px;
}

.policy-tags {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.storage-note {
  padding: 12px 20px;
  color: var(--color-text-3);
  font-size: 12px;
  border-top: 1px solid var(--color-border-2);
}

.stats-card {
  margin-bottom: 16px;
}

.policy-card {
  margin-top: 16px;
}

.policy-hint {
  margin: 0 0 18px;
  color: var(--color-text-3);
  line-height: 1.65;
}

.policy-actions {
  display: flex;
  justify-content: flex-end;
}

.current-policy {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -4px 0 16px;
  color: var(--color-text-3);
  font-size: 12px;
}

.policy-card :deep(.arco-input-number) {
  width: 100%;
}

.cleanup-form {
  margin-top: 18px;
}

.cleanup-preview {
  padding: 16px;
  margin-top: 16px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 7px;
}

.cleanup-preview p {
  margin: 0 0 10px;
  color: var(--color-text-2);
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-card strong {
    font-size: 17px;
  }
}
</style>
