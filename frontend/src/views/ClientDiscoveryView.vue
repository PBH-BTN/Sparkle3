<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TableColumnData } from '@arco-design/web-vue'
import { IconRefresh, IconSearch } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import TablePager from '@/components/TablePager.vue'
import { historyApi } from '@/api'
import type { ClientDiscoveryQuery, ClientDiscoveryRecord } from '@/api/types'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const clientTypes = ref<string[]>([])
const form = reactive({
  peerId: '',
  peerClientName: '',
  clientType: '',
  clientSemver: '',
})

type Filters = Omit<ClientDiscoveryQuery, 'page' | 'size' | 'sortBy' | 'sortOrder'>

function buildQuery(): Filters {
  return {
    peerId: form.peerId.trim() || undefined,
    peerClientName: form.peerClientName.trim() || undefined,
    clientType: form.clientType || undefined,
    clientSemver: form.clientSemver.trim() || undefined,
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
} = usePagedQuery<ClientDiscoveryRecord, Filters>(
  historyApi.queryClientDiscovery,
  buildQuery,
  { pageSize: 50, sortBy: 'found_at' },
)

const columns = computed<TableColumnData[]>(() => [
  {
    title: t('clientDiscovery.peerId'),
    dataIndex: 'peer_id',
    slotName: 'peerId',
    width: 300,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('clientDiscovery.peerClientName'),
    dataIndex: 'peer_client_name',
    slotName: 'peerClientName',
    width: 240,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('clientDiscovery.clientType'),
    dataIndex: 'client_type',
    slotName: 'clientType',
    width: 180,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('clientDiscovery.clientSemver'),
    dataIndex: 'client_semver',
    slotName: 'clientSemver',
    width: 180,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
  {
    title: t('clientDiscovery.foundAt'),
    dataIndex: 'found_at',
    slotName: 'foundAt',
    width: 190,
    sortable: { sortDirections: ['ascend', 'descend'] },
  },
])

function reset() {
  form.peerId = ''
  form.peerClientName = ''
  form.clientType = ''
  form.clientSemver = ''
  search()
}

onMounted(async () => {
  const [optionsResult] = await Promise.allSettled([
    historyApi.clientDiscoveryOptions(),
    run(),
  ])
  if (optionsResult.status === 'fulfilled') {
    clientTypes.value = optionsResult.value.clientTypes
  }
})
</script>

<template>
  <div class="page-stack">
    <PageHeader
      :title="t('clientDiscovery.title')"
      :description="t('clientDiscovery.description')"
    />

    <a-card class="surface-card filter-card">
      <a-form :model="form" layout="vertical" @submit.prevent="search">
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('clientDiscovery.peerId')">
              <a-input
                v-model="form.peerId"
                :placeholder="t('clientDiscovery.fuzzyHint')"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('clientDiscovery.peerClientName')">
              <a-input
                v-model="form.peerClientName"
                :placeholder="t('clientDiscovery.fuzzyHint')"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('clientDiscovery.clientType')">
              <a-select
                v-model="form.clientType"
                :placeholder="t('clientDiscovery.allTypes')"
                allow-search
                allow-clear
              >
                <a-option
                  v-for="clientType in clientTypes"
                  :key="clientType"
                  :value="clientType"
                >
                  {{ clientType }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item :label="t('clientDiscovery.clientSemver')">
              <a-input
                v-model="form.clientSemver"
                :placeholder="t('clientDiscovery.fuzzyHint')"
                allow-clear
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
        :scroll="{ x: 1100 }"
        row-key="hash"
        stripe
        @sorter-change="changeSort"
      >
        <template #empty>
          <EmptyState
            :title="t('clientDiscovery.emptyTitle')"
            :description="t('clientDiscovery.emptyText')"
          />
        </template>
        <template #peerId="{ record }">
          <code class="mono">{{ record.peerId || 'N/A' }}</code>
        </template>
        <template #peerClientName="{ record }">
          {{ record.peerClientName || 'N/A' }}
        </template>
        <template #clientType="{ record }">
          <a-tag v-if="record.clientType" color="arcoblue">{{ record.clientType }}</a-tag>
          <span v-else class="muted">N/A</span>
        </template>
        <template #clientSemver="{ record }">
          {{ record.clientSemver || 'N/A' }}
        </template>
        <template #foundAt="{ record }">{{ formatDate(record.foundAt) }}</template>
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
