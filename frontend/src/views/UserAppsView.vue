<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconCopy,
  IconDelete,
  IconPlus,
  IconRefresh,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { uiApi } from '@/api'
import type { ApplicationCredentials, UserApplication } from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const router = useRouter()
const { t } = useI18n()
const { formatDate } = useFormatters()
const applications = ref<UserApplication[]>([])
const loading = ref(true)
const actionLoading = ref<string | null>(null)

const columns = computed<TableColumnData[]>(() => [
  { title: t('userApps.appId'), dataIndex: 'appId', slotName: 'appId', width: 250 },
  { title: t('userApps.comment'), dataIndex: 'comment', slotName: 'comment', width: 240 },
  { title: t('common.lastSeenAt'), dataIndex: 'lastSeenAt', slotName: 'lastSeenAt', width: 190 },
  { title: t('userApps.status'), dataIndex: 'bannedAt', slotName: 'status', width: 110 },
  { title: t('common.actions'), slotName: 'actions', width: 220, fixed: 'right' },
])

const credentialStorageKey = 'sparkle-application-credentials'

async function loadApplications() {
  loading.value = true
  try {
    applications.value = await uiApi.userApps()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function copyText(value: string) {
  await navigator.clipboard.writeText(value)
  Message.success(t('common.copied'))
}

async function resetSecret(application: UserApplication) {
  actionLoading.value = application.appId
  try {
    const credentials = await uiApi.resetUserAppSecret(application.appId)
    saveCredentials(credentials)
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    actionLoading.value = null
  }
}

async function deleteApplication(application: UserApplication) {
  actionLoading.value = application.appId
  try {
    await uiApi.deleteUserApp(application.appId)
    Message.success(t('userApps.deletedSuccess'))
    await loadApplications()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    actionLoading.value = null
  }
}

function saveCredentials(credentials: ApplicationCredentials) {
  sessionStorage.setItem(credentialStorageKey, JSON.stringify(credentials))
  router.push('/userapp/created')
}

onMounted(loadApplications)
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('userApps.title')" :description="t('userApps.description')">
      <template #actions>
        <a-button type="primary" @click="router.push('/userapp/create')">
          <template #icon><IconPlus /></template>
          {{ t('userApps.create') }}
        </a-button>
      </template>
    </PageHeader>

    <a-card class="surface-card table-card">
      <a-table
        :columns="columns"
        :data="applications"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1050 }"
        row-key="id"
        stripe
      >
        <template #empty>
          <EmptyState :title="t('userApps.emptyTitle')" :description="t('userApps.emptyText')" />
        </template>

        <template #appId="{ record }">
          <div class="app-id-cell">
            <code class="mono">{{ record.appId }}</code>
            <a-tooltip :content="t('common.copy')">
              <a-button size="mini" type="text" @click="copyText(record.appId)">
                <template #icon><IconCopy /></template>
              </a-button>
            </a-tooltip>
          </div>
        </template>

        <template #comment="{ record }">
          <span class="app-comment" :title="record.comment">{{ record.comment }}</span>
        </template>

        <template #lastSeenAt="{ record }">
          {{ formatDate(record.lastSeenAt) }}
        </template>

        <template #status="{ record }">
          <a-tooltip
            v-if="record.bannedAt"
            :content="t('userApps.pausedReason', { reason: record.bannedReason || 'N/A' })"
          >
            <a-tag color="red">{{ t('common.paused') }}</a-tag>
          </a-tooltip>
          <a-tag v-else color="green">{{ t('common.normal') }}</a-tag>
        </template>

        <template #actions="{ record }">
          <a-space>
            <a-popconfirm
              :content="t('userApps.resetConfirm')"
              position="left"
              @ok="resetSecret(record)"
            >
              <a-button
                type="text"
                size="small"
                :loading="actionLoading === record.appId"
              >
                <template #icon><IconRefresh /></template>
                {{ t('userApps.resetSecret') }}
              </a-button>
            </a-popconfirm>
            <a-popconfirm
              :content="t('userApps.deleteConfirm')"
              position="left"
              @ok="deleteApplication(record)"
            >
              <a-button
                status="danger"
                type="text"
                size="small"
                :loading="actionLoading === record.appId"
              >
                <template #icon><IconDelete /></template>
                {{ t('userApps.delete') }}
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.app-id-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.app-comment {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
