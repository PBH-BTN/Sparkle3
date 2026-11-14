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
  {
    title: t('userApps.exemptIps'),
    dataIndex: 'exemptIps',
    slotName: 'exemptIps',
    width: 260,
  },
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
      <div class="desktop-applications">
        <a-table
          :columns="columns"
          :data="applications"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1280 }"
          row-key="appId"
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

          <template #exemptIps="{ record }">
            <div v-if="record.exemptIps.length" class="exempt-ip-cell">
              <a-tooltip
                v-for="ip in record.exemptIps.slice(0, 2)"
                :key="ip"
                :content="ip"
              >
                <a-tag color="arcoblue" class="exempt-ip-tag">
                  {{ ip }}
                </a-tag>
              </a-tooltip>
              <a-popover
                v-if="record.exemptIps.length > 2"
                trigger="click"
                position="br"
              >
                <a-tag color="gray" class="exempt-ip-more">
                  +{{ record.exemptIps.length - 2 }}
                </a-tag>
                <template #content>
                  <div class="exempt-ip-popover">
                    <a-tag
                      v-for="ip in record.exemptIps"
                      :key="ip"
                      color="arcoblue"
                    >
                      {{ ip }}
                    </a-tag>
                  </div>
                </template>
              </a-popover>
            </div>
            <span v-else class="exempt-ip-empty">
              {{
                record.btnBypass
                  ? t('userApps.noExemptIps')
                  : t('userApps.bypassNotEnabled')
              }}
            </span>
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
      </div>

      <div class="mobile-applications">
        <a-spin :loading="loading" class="mobile-applications-spin">
          <EmptyState
            v-if="!loading && applications.length === 0"
            :title="t('userApps.emptyTitle')"
            :description="t('userApps.emptyText')"
          />
          <div v-else class="mobile-app-list">
            <article
              v-for="application in applications"
              :key="application.appId"
              class="mobile-app-card"
            >
              <header class="mobile-app-header">
                <div>
                  <h3>{{ application.comment }}</h3>
                  <span>
                    {{ t('common.createdAt') }} · {{ formatDate(application.createdAt) }}
                  </span>
                </div>
                <a-tooltip
                  v-if="application.bannedAt"
                  :content="t('userApps.pausedReason', {
                    reason: application.bannedReason || 'N/A',
                  })"
                >
                  <a-tag color="red">{{ t('common.paused') }}</a-tag>
                </a-tooltip>
                <a-tag v-else color="green">{{ t('common.normal') }}</a-tag>
              </header>

              <div class="mobile-field">
                <span>{{ t('userApps.appId') }}</span>
                <div class="mobile-app-id">
                  <code>{{ application.appId }}</code>
                  <a-button size="mini" type="text" @click="copyText(application.appId)">
                    <template #icon><IconCopy /></template>
                    {{ t('common.copy') }}
                  </a-button>
                </div>
              </div>

              <div class="mobile-field">
                <span>{{ t('common.lastSeenAt') }}</span>
                <strong>{{ formatDate(application.lastSeenAt) }}</strong>
              </div>

              <div class="mobile-field">
                <span>{{ t('userApps.exemptIps') }}</span>
                <div v-if="application.exemptIps.length" class="mobile-exempt-ips">
                  <a-tag
                    v-for="ip in application.exemptIps"
                    :key="ip"
                    color="arcoblue"
                  >
                    {{ ip }}
                  </a-tag>
                </div>
                <strong v-else class="exempt-ip-empty">
                  {{
                    application.btnBypass
                      ? t('userApps.noExemptIps')
                      : t('userApps.bypassNotEnabled')
                  }}
                </strong>
              </div>

              <a-alert
                v-if="application.bannedAt"
                type="error"
                class="mobile-ban-reason"
              >
                {{
                  t('userApps.pausedReason', {
                    reason: application.bannedReason || 'N/A',
                  })
                }}
              </a-alert>

              <footer class="mobile-app-actions">
                <a-popconfirm
                  :content="t('userApps.resetConfirm')"
                  position="top"
                  @ok="resetSecret(application)"
                >
                  <a-button
                    long
                    :loading="actionLoading === application.appId"
                  >
                    <template #icon><IconRefresh /></template>
                    {{ t('userApps.resetSecret') }}
                  </a-button>
                </a-popconfirm>
                <a-popconfirm
                  :content="t('userApps.deleteConfirm')"
                  position="top"
                  @ok="deleteApplication(application)"
                >
                  <a-button
                    long
                    status="danger"
                    :loading="actionLoading === application.appId"
                  >
                    <template #icon><IconDelete /></template>
                    {{ t('userApps.delete') }}
                  </a-button>
                </a-popconfirm>
              </footer>
            </article>
          </div>
        </a-spin>
      </div>
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

.exempt-ip-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.exempt-ip-tag {
  max-width: 145px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exempt-ip-more {
  cursor: pointer;
}

.exempt-ip-popover {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  max-width: 440px;
  max-height: 300px;
  overflow: auto;
}

.exempt-ip-empty {
  color: var(--color-text-3);
  font-size: 12px;
}

.mobile-applications {
  display: none;
}

.mobile-applications-spin {
  display: block;
  width: 100%;
}

.mobile-app-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mobile-app-card {
  padding: 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
}

.mobile-app-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--color-border-2);
}

.mobile-app-header > div {
  min-width: 0;
}

.mobile-app-header h3 {
  margin: 0 0 4px;
  overflow-wrap: anywhere;
  font-size: 16px;
  line-height: 1.4;
}

.mobile-app-header span {
  color: var(--color-text-3);
  font-size: 12px;
}

.mobile-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 14px;
}

.mobile-field > span {
  color: var(--color-text-3);
  font-size: 12px;
}

.mobile-field strong {
  font-size: 13px;
}

.mobile-app-id {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}

.mobile-app-id code {
  min-width: 0;
  overflow-wrap: anywhere;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 1.6;
}

.mobile-exempt-ips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mobile-ban-reason {
  margin-top: 14px;
}

.mobile-app-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}

@media (max-width: 800px) {
  .desktop-applications {
    display: none;
  }

  .mobile-applications {
    display: block;
    padding: 16px;
  }
}
</style>
