<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal, type TableColumnData } from '@arco-design/web-vue'
import {
  IconCopy,
  IconDelete,
  IconDown,
  IconEdit,
  IconLock,
  IconPlus,
  IconRefresh,
  IconSearch,
  IconSafe,
  IconUnlock,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import TablePager from '@/components/TablePager.vue'
import SuspensionModal from '@/components/admin/SuspensionModal.vue'
import { adminApi } from '@/api/admin'
import type {
  AdminApplication,
  AdminApplicationCredentials,
  AdminApplicationInput,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t, locale } = useI18n()
const { formatDate } = useFormatters()
const records = ref<AdminApplication[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const banDays = ref<1 | 3 | 7>()
const countFormatter = computed(() => new Intl.NumberFormat(locale.value))
let latestRequest = 0
const filters = reactive<{
  keyword: string
  ownerId: number | undefined
  status: string
}>({
  keyword: '',
  ownerId: undefined,
  status: '',
})

const editorVisible = ref(false)
const editorSaving = ref(false)
const editingId = ref<number | null>(null)
const editingApplication = ref<AdminApplication | null>(null)
const editor = reactive<{
  ownerId: number | undefined
  comment: string
  btnBypass: boolean
}>({
  ownerId: undefined,
  comment: '',
  btnBypass: false,
})

const credentialsVisible = ref(false)
const credentials = ref<AdminApplicationCredentials | null>(null)
const banVisible = ref(false)
const banSaving = ref(false)
const banApplication = ref<AdminApplication | null>(null)

const columns = computed<TableColumnData[]>(() => [
  { title: t('admin.apps.id'), slotName: 'id', width: 90, align: 'center' },
  { title: t('admin.apps.appId'), slotName: 'appId', width: 180 },
  { title: t('admin.apps.comment'), slotName: 'comment', width: 190 },
  { title: t('admin.apps.owner'), slotName: 'owner', width: 170 },
  ...(banDays.value ? [{ title: t('admin.apps.banCount'), slotName: 'banCount', width: 150 }] : []),
  { title: t('admin.apps.flags'), slotName: 'flags', width: 120 },
  { title: t('admin.common.lastSeenAt'), slotName: 'lastSeenAt', width: 160 },
  { title: t('admin.common.actions'), slotName: 'actions', width: 280 },
])

async function load() {
  const request = ++latestRequest
  loading.value = true
  try {
    const result = await adminApi.applications({
      page: page.value,
      size: pageSize.value,
      keyword: filters.keyword.trim() || undefined,
      ownerId: filters.ownerId,
      status: filters.status || undefined,
      banDays: banDays.value,
    })
    if (request !== latestRequest) return
    records.value = result.records
    total.value = result.total
    page.value = result.current
    pageSize.value = result.size
  } catch (error) {
    if (request !== latestRequest) return
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

function changeBanDays(value: 1 | 3 | 7 | undefined) {
  banDays.value = value
  records.value = []
  total.value = 0
  search()
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.keyword = ''
  filters.ownerId = undefined
  filters.status = ''
  changeBanDays(undefined)
}

function openCreate() {
  editingId.value = null
  editingApplication.value = null
  Object.assign(editor, {
    ownerId: undefined,
    comment: '',
    btnBypass: false,
  })
  editorVisible.value = true
}

function openEdit(application: AdminApplication) {
  editingId.value = application.id
  editingApplication.value = application
  Object.assign(editor, {
    ownerId: application.ownerId,
    comment: application.comment,
    btnBypass: Boolean(application.btnBypass),
  })
  editorVisible.value = true
}

async function saveApplication() {
  if (!editor.ownerId) {
    Message.warning(t('admin.apps.owner'))
    return
  }
  if (!editor.comment.trim()) {
    Message.warning(t('admin.apps.comment'))
    return
  }
  editorSaving.value = true
  const body: AdminApplicationInput = {
    ownerId: editor.ownerId,
    comment: editor.comment.trim(),
    btnBypass: editor.btnBypass,
  }
  try {
    if (editingId.value == null) {
      const result = await adminApi.createApplication(body)
      credentials.value = result
      credentialsVisible.value = true
      Message.success(t('admin.apps.createdSuccess'))
    } else {
      await adminApi.updateApplication(editingId.value, body)
      Message.success(t('admin.common.updated'))
    }
    editorVisible.value = false
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    editorSaving.value = false
  }
}

async function resetSecret(application: AdminApplication) {
  try {
    credentials.value = await adminApi.resetApplicationSecret(application.id)
    credentialsVisible.value = true
    Message.success(t('admin.apps.resetSuccess'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

async function suspendApplication(reason: string, unbanAt: string | null) {
  const application = banApplication.value
  if (!application) return
  banSaving.value = true
  try {
    await adminApi.setApplicationBanned(application.id, true, reason, unbanAt)
    Message.success(t('admin.common.updated'))
    banVisible.value = false
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    banSaving.value = false
  }
}

async function restoreApplication(application: AdminApplication) {
  try {
    await adminApi.setApplicationBanned(application.id, false)
    Message.success(t('admin.common.updated'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

async function deleteApplication(application: AdminApplication) {
  try {
    await adminApi.deleteApplication(application.id)
    Message.success(t('admin.common.deleted'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

async function copy(value: string) {
  await navigator.clipboard.writeText(value)
  Message.success(t('common.copied'))
}

function confirmResetSecret(application: AdminApplication) {
  Modal.confirm({
    title: t('admin.apps.resetSecret'),
    content: t('admin.apps.resetConfirm'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: () => resetSecret(application),
  })
}

function openBan(application: AdminApplication) {
  banApplication.value = application
  banVisible.value = true
}

function confirmToggleBanned(application: AdminApplication) {
  if (!application.bannedAt) {
    openBan(application)
    return
  }
  Modal.confirm({
    title: t('admin.apps.restore'),
    content: application.comment,
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: () => restoreApplication(application),
  })
}

function confirmDeleteApplication(application: AdminApplication) {
  Modal.confirm({
    title: t('admin.common.delete'),
    content: t('admin.apps.deleteConfirm'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: () => deleteApplication(application),
  })
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('admin.apps.title')" :description="t('admin.apps.description')">
      <template #actions>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.apps.create') }}
        </a-button>
      </template>
    </PageHeader>

    <a-card class="surface-card filter-card">
      <a-form :model="filters" layout="vertical" @submit="search">
        <a-row :gutter="16">
          <a-col :xs="24" :md="8">
            <a-form-item :label="t('admin.common.keyword')">
              <a-input v-model="filters.keyword" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="12" :md="5">
            <a-form-item :label="t('admin.apps.owner')">
              <a-input-number v-model="filters.ownerId" :min="1" hide-button allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="12" :md="5">
            <a-form-item :label="t('admin.common.status')">
              <a-select v-model="filters.status" allow-clear>
                <a-option value="active">{{ t('admin.common.active') }}</a-option>
                <a-option value="banned">{{ t('admin.common.banned') }}</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="6">
            <a-form-item :label="t('admin.apps.banRanking')">
              <a-select :model-value="banDays" :placeholder="t('admin.apps.creationOrder')" allow-clear
                @change="(value) => changeBanDays(value as 1 | 3 | 7 | undefined)">
                <a-option v-for="days in [7, 3, 1]" :key="days" :value="days">
                  {{ t('admin.apps.banWindow', { count: days }, days) }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <div class="filter-actions">
          <a-button @click="resetFilters">
            <template #icon><IconRefresh /></template>
            {{ t('common.reset') }}
          </a-button>
          <a-button type="primary" html-type="submit">
            <template #icon><IconSearch /></template>
            {{ t('admin.common.search') }}
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
        :scroll="{ x: banDays ? 1340 : 1190 }"
        row-key="id"
        stripe
      >
        <template #empty><a-empty :description="t('common.noData')" /></template>
        <template #id="{ record }">
          <code class="primary-id">{{ record.id }}</code>
        </template>
        <template #appId="{ record }">
          <div class="app-id">
            <code>{{ record.appId }}</code>
            <a-tooltip :content="t('common.copy')">
              <a-button type="text" size="mini" @click="copy(record.appId)">
                <template #icon><IconCopy /></template>
              </a-button>
            </a-tooltip>
          </div>
        </template>
        <template #comment="{ record }">
          <div class="comment-cell">
            <span class="truncate" :title="record.comment">{{ record.comment }}</span>
            <small>{{ formatDate(record.createdAt) }}</small>
          </div>
        </template>
        <template #owner="{ record }">
          <div class="owner-cell">
            <strong>{{ record.ownerNickname }} #{{ record.ownerId }}</strong>
            <span>{{ record.ownerEmail }}</span>
          </div>
        </template>
        <template #banCount="{ record }">
          {{ record.banCount == null ? '—' : countFormatter.format(BigInt(record.banCount)) }}
        </template>
        <template #flags="{ record }">
          <div class="flag-cell">
            <a-tooltip v-if="record.bannedAt" :content="[record.bannedReason, record.unbanAt && t('common.suspensionEndsAt', { time: formatDate(record.unbanAt) })].filter(Boolean).join(' · ')">
              <a-tag color="red">{{ t('admin.common.banned') }}</a-tag>
            </a-tooltip>
            <a-tag v-else color="green">{{ t('admin.common.active') }}</a-tag>
            <span>
              Bypass:
              {{ record.btnBypass ? t('admin.common.enabled') : t('admin.common.disabled') }}
            </span>
          </div>
        </template>
        <template #lastSeenAt="{ record }">
          <span class="nowrap">{{ formatDate(record.lastSeenAt) }}</span>
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button v-if="!record.bannedAt" type="text" size="small" status="danger"
              @click="confirmToggleBanned(record)">
              <template #icon><IconLock /></template>
              {{ t('admin.apps.suspend') }}
            </a-button>
            <a-button type="text" size="small" @click="openEdit(record)">
              <template #icon><IconEdit /></template>
              {{ t('admin.common.edit') }}
            </a-button>
            <a-dropdown trigger="click" position="br">
              <a-button type="text" size="small">
                <template #icon><IconDown /></template>
                {{ t('admin.common.more') }}
              </a-button>
              <template #content>
                <a-doption @click="confirmResetSecret(record)">
                  <template #icon><IconSafe /></template>
                  {{ t('admin.apps.resetSecret') }}
                </a-doption>
                <a-doption v-if="record.bannedAt" @click="openBan(record)">
                  <template #icon><IconLock /></template>
                  {{ t('admin.suspension.edit') }}
                </a-doption>
                <a-doption @click="confirmToggleBanned(record)">
                  <template #icon>
                    <IconUnlock v-if="record.bannedAt" />
                    <IconLock v-else />
                  </template>
                  {{ record.bannedAt ? t('admin.apps.restore') : t('admin.apps.suspend') }}
                </a-doption>
                <a-doption class="danger-option" @click="confirmDeleteApplication(record)">
                  <template #icon><IconDelete /></template>
                  {{ t('admin.common.delete') }}
                </a-doption>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </a-table>
      <TablePager
        :current="page"
        :page-size="pageSize"
        :total="total"
        :records-length="records.length"
        @change="(value) => { page = value; load() }"
        @page-size-change="(value) => { pageSize = value; page = 1; load() }"
      />
    </a-card>

    <a-modal
      v-model:visible="editorVisible"
      :title="editingId == null ? t('admin.apps.create') : t('admin.apps.edit')"
      :footer="false"
      width="620px"
    >
      <a-form :model="editor" layout="vertical">
        <a-row :gutter="16">
          <a-col :xs="24" :md="10">
            <a-form-item :label="t('admin.apps.owner')" required>
              <a-input-number v-model="editor.ownerId" :min="1" hide-button />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="14">
            <a-form-item :label="t('admin.apps.comment')" required>
              <a-input v-model="editor.comment" :max-length="100" show-word-limit />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item :label="t('admin.apps.bypass')">
              <a-switch v-model="editor.btnBypass" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-descriptions
          v-if="editingApplication"
          :column="1"
          bordered
          size="small"
          class="application-details"
        >
          <a-descriptions-item :label="t('admin.apps.installationId')">
            <code>{{ editingApplication.installationId || 'N/A' }}</code>
          </a-descriptions-item>
          <a-descriptions-item :label="t('admin.apps.userAgent')">
            <code>{{ editingApplication.lastUserAgent || 'N/A' }}</code>
          </a-descriptions-item>
        </a-descriptions>
        <div class="modal-actions">
          <a-button @click="editorVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button type="primary" :loading="editorSaving" @click="saveApplication">
            {{ t('admin.common.save') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <SuspensionModal
      v-model:visible="banVisible"
      :title="t(banApplication?.bannedAt ? 'admin.suspension.edit' : 'admin.apps.suspend')"
      :subject="banApplication?.comment ?? ''"
      :submit-label="t(banApplication?.bannedAt ? 'admin.common.save' : 'admin.apps.suspend')"
      :saving="banSaving"
      :initial-reason="banApplication?.bannedReason"
      :initial-unban-at="banApplication?.unbanAt"
      @submit="suspendApplication"
    />

    <a-modal
      v-model:visible="credentialsVisible"
      :title="t('admin.apps.credentialsTitle')"
      :footer="false"
      width="680px"
    >
      <a-alert type="warning" show-icon>{{ t('admin.apps.credentialsWarning') }}</a-alert>
      <div v-if="credentials" class="credentials">
        <div>
          <span>{{ t('admin.apps.appId') }}</span>
          <code>{{ credentials.appId }}</code>
          <a-button size="small" @click="copy(credentials.appId)">
            <template #icon><IconCopy /></template>
          </a-button>
        </div>
        <div>
          <span>{{ t('userApps.appSecret') }}</span>
          <code>{{ credentials.appSecret }}</code>
          <a-button size="small" type="primary" @click="copy(credentials.appSecret)">
            <template #icon><IconCopy /></template>
          </a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.primary-id {
  color: var(--color-text-2);
  font-size: 12px;
}

.app-id {
  display: flex;
  align-items: center;
  gap: 4px;
}

.app-id code {
  font-size: 12px;
}

.truncate {
  display: inline-block;
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.owner-cell {
  display: flex;
  flex-direction: column;
}

.owner-cell span {
  color: var(--color-text-3);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.comment-cell {
  display: flex;
  flex-direction: column;
}

.comment-cell small {
  color: var(--color-text-3);
  font-size: 11px;
}

.flag-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.flag-cell span {
  color: var(--color-text-3);
  font-size: 11px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.credentials {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 18px;
}

.credentials > div {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}

.credentials span {
  color: var(--color-text-3);
  font-size: 12px;
}

.credentials code {
  overflow-wrap: anywhere;
  font-size: 13px;
}

.application-details {
  margin-bottom: 18px;
}

.danger-option {
  color: rgb(var(--red-6));
}
</style>
