<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { adminApi } from '@/api/admin'
import type { BanSubmissionEvent } from '@/api/adminTypes'
import PageHeader from '@/components/PageHeader.vue'
import TablePager from '@/components/TablePager.vue'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatNumber } = useFormatters()
const rows = ref<BanSubmissionEvent[]>([])
const loading = ref(false)
const error = ref('')
const current = ref(1)
const total = ref(0)
const pageSize = ref(20)
const filters = reactive({ appId: '', kind: '', status: '' })
const dates = ref<string[]>([])
let requestNumber = 0
let appliedFilters: Omit<Parameters<typeof adminApi.banSubmissions>[0], 'page' | 'size'> = {}

async function load(page = current.value) {
  const request = ++requestNumber
  loading.value = true
  error.value = ''
  try {
    const result = await adminApi.banSubmissions({ ...appliedFilters, page, size: pageSize.value })
    if (request !== requestNumber) return
    rows.value = result.records
    current.value = result.current
    total.value = result.total
  } catch (cause) {
    if (request !== requestNumber) return
    error.value = errorMessage(cause, t('admin.banSubmissions.loadError'))
  } finally {
    if (request === requestNumber) loading.value = false
  }
}

function search() {
  const appId = filters.appId.trim()
  if (appId && (!/^\d{1,19}$/.test(appId) || BigInt(appId) > 9223372036854775807n)) {
    error.value = t('admin.banSubmissions.invalidApp')
    return
  }
  const [start, end] = dates.value ?? []
  appliedFilters = {
    appId,
    kind: filters.kind,
    status: filters.status,
    since: start ? new Date(`${start}T00:00:00`).toISOString() : undefined,
    until: end ? new Date(`${end}T23:59:59.999`).toISOString() : undefined,
  }
  void load(1)
}

function changePageSize(size: number) {
  pageSize.value = size
  void load(1)
}

onMounted(() => load(1))
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('admin.nav.banSubmissions')" :description="t('admin.banSubmissions.description')" />

    <a-form class="surface-card ban-filters" :model="filters" layout="inline" @submit-success="search">
      <a-form-item :label="t('admin.banSubmissions.app')">
        <a-input v-model="filters.appId" allow-clear inputmode="numeric" />
      </a-form-item>
      <a-form-item :label="t('admin.banSubmissions.kind')">
        <a-select v-model="filters.kind" allow-clear>
          <a-option value="replay">{{ t('admin.banSubmissions.replays') }}</a-option>
          <a-option value="repeated">{{ t('admin.banSubmissions.repeated') }}</a-option>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('admin.banSubmissions.status')">
        <a-select v-model="filters.status" allow-clear>
          <a-option v-for="status in ['replayed', 'observing', 'blocking', 'quiet']" :key="status" :value="status">
            {{ t(`admin.banSubmissions.states.${status}`) }}
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('admin.banSubmissions.lastSeen')">
        <a-range-picker v-model="dates" value-format="YYYY-MM-DD" allow-clear />
      </a-form-item>
      <a-button type="primary" html-type="submit" :loading="loading">{{ t('admin.banSubmissions.search') }}</a-button>
    </a-form>

    <a-alert v-if="error" type="error">{{ error }}</a-alert>
    <div class="table-card surface-card">
      <a-table :data="rows" :loading="loading" :pagination="false" :scroll="{ x: 1285 }" row-key="id">
        <template #columns>
          <a-table-column :title="t('admin.banSubmissions.app')" data-index="appId" :width="125" />
          <a-table-column :title="t('admin.banSubmissions.target')" :width="170">
            <template #cell="{ record }">{{ record.peerNetwork }} · {{ record.peerPort }}</template>
          </a-table-column>
          <a-table-column :title="t('admin.banSubmissions.rule')" :width="200">
            <template #cell="{ record }">
              <div class="ban-rule">{{ record.moduleName }}</div>
              <div v-if="record.ruleName" class="ban-rule">{{ record.ruleName }}</div>
            </template>
          </a-table-column>
          <a-table-column :title="t('admin.banSubmissions.status')" :width="125">
            <template #cell="{ record }">
              <a-tag :color="record.status === 'blocking' ? 'orangered' : record.status === 'observing' ? 'arcoblue' : undefined">
                {{ t(`admin.banSubmissions.states.${record.status}`) }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="t('admin.banSubmissions.firstSeen')" :width="160">
            <template #cell="{ record }">{{ formatDate(record.firstSeen) }}</template>
          </a-table-column>
          <a-table-column :title="t('admin.banSubmissions.lastSeen')" :width="160">
            <template #cell="{ record }">{{ formatDate(record.lastSeen) }}</template>
          </a-table-column>
          <a-table-column :title="t('admin.banSubmissions.windows')" :width="115">
            <template #cell="{ record }">{{ formatNumber(record.peakWindows) }}</template>
          </a-table-column>
          <a-table-column :title="t('admin.banSubmissions.replays')" :width="115">
            <template #cell="{ record }">{{ formatNumber(record.replays) }}</template>
          </a-table-column>
          <a-table-column :title="t('admin.banSubmissions.suppressed')" :width="115">
            <template #cell="{ record }">{{ formatNumber(record.suppressed) }}</template>
          </a-table-column>
        </template>
        <template #empty><a-empty :description="t('admin.banSubmissions.empty')" /></template>
      </a-table>
      <TablePager
        class="ban-pagination"
        :current="current"
        :page-size="pageSize"
        :total="total"
        :records-length="rows.length"
        @change="load"
        @page-size-change="changePageSize"
      />
    </div>
  </div>
</template>

<style scoped>
.ban-filters {
  padding: 16px 16px 0;
}

.ban-filters :deep(.arco-input-wrapper),
.ban-filters :deep(.arco-select-view) {
  min-width: 120px;
}

.ban-rule {
  overflow-wrap: anywhere;
}

.ban-pagination {
  padding: 12px;
}

@media (max-width: 720px) {
  .ban-filters {
    display: grid;
    gap: 8px;
    padding-bottom: 16px;
  }
}
</style>
