<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import { IconEmail, IconRefresh, IconSearch } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import TablePager from '@/components/TablePager.vue'
import { adminApi } from '@/api/admin'
import type { UserContactEmailAudit } from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(true)
const keyword = ref('')
const records = ref<UserContactEmailAudit[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const columns = computed<TableColumnData[]>(() => [
  { title: t('admin.userContactEmails.sentAt'), dataIndex: 'sentAt', width: 180 },
  { title: t('admin.userContactEmails.sender'), slotName: 'sender', width: 220 },
  { title: t('admin.userContactEmails.recipient'), slotName: 'recipient', width: 220 },
  { title: t('admin.userContactEmails.subject'), dataIndex: 'subject', width: 240 },
  { title: t('admin.userContactEmails.senderIp'), dataIndex: 'senderIp', width: 150 },
  { title: t('admin.userContactEmails.senderUserAgent'), slotName: 'userAgent', width: 300 },
])

async function load() {
  loading.value = true
  try {
    const response = await adminApi.userContactEmails(
      page.value,
      pageSize.value,
      keyword.value,
    )
    records.value = response.records
    total.value = response.total
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function changePage(value: number) {
  page.value = value
  load()
}

function changePageSize(value: number) {
  pageSize.value = value
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader
      :title="t('admin.userContactEmails.title')"
      :description="t('admin.userContactEmails.description')"
    >
      <template #actions>
        <a-input
          v-model="keyword"
          :placeholder="t('admin.userContactEmails.searchPlaceholder')"
          allow-clear
          class="audit-search"
          @press-enter="search"
        >
          <template #prefix><IconSearch /></template>
        </a-input>
        <a-button type="primary" @click="search">
          <template #icon><IconSearch /></template>
          {{ t('admin.common.search') }}
        </a-button>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <a-card class="surface-card">
      <template #title>
        <span class="card-title">
          <IconEmail />
          {{ t('admin.userContactEmails.auditTitle') }}
        </span>
      </template>
      <a-table
        :columns="columns"
        :data="records"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1300 }"
        row-key="id"
      >
        <template #sentAt="{ record }">
          {{ formatDate(record.sentAt) }}
        </template>
        <template #sender="{ record }">
          <div class="user-cell">
            <strong>{{ record.senderNickname || t('common.notAvailable') }}</strong>
            <span>UID {{ record.senderId }}</span>
            <span>{{ record.senderEmail || t('common.notAvailable') }}</span>
          </div>
        </template>
        <template #recipient="{ record }">
          <div class="user-cell">
            <strong>{{ record.recipientNickname || t('common.notAvailable') }}</strong>
            <span>UID {{ record.recipientId }}</span>
            <span>{{ record.recipientEmail || t('common.notAvailable') }}</span>
          </div>
        </template>
        <template #userAgent="{ record }">
          <span class="user-agent">{{ record.senderUserAgent || t('common.notAvailable') }}</span>
        </template>
        <template #empty>
          <a-empty :description="t('admin.userContactEmails.noData')" />
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
.audit-search {
  width: 260px;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.user-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.user-cell span {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-agent {
  display: block;
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .audit-search {
    width: 100%;
  }
}
</style>
