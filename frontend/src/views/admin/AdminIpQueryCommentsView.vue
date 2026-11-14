<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal, type TableColumnData } from '@arco-design/web-vue'
import {
  IconCheck,
  IconClose,
  IconDelete,
  IconDown,
  IconEdit,
  IconEye,
  IconPlus,
  IconRefresh,
  IconSearch,
  IconSync,
  IconUndo,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import TablePager from '@/components/TablePager.vue'
import { adminApi } from '@/api/admin'
import type {
  AdminCommentCreateInput,
  AdminCommentDetail,
  AdminCommentRow,
  AdminCommentUpdateInput,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()

const loading = ref(true)
const records = ref<AdminCommentRow[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = reactive({
  keyword: '',
  scope: '',
  status: '',
  includeDeleted: false,
})
const moderationStatuses = ['PENDING', 'APPROVED', 'REJECTED', 'FAILED']

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminCommentDetail | null>(null)

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  network: '',
  authorUserId: '',
  content: '',
  moderationStatus: 'APPROVED',
  moderationReason: '',
})

const editorVisible = ref(false)
const editorSaving = ref(false)
const editing = ref<AdminCommentRow | null>(null)
const editorForm = reactive<AdminCommentUpdateInput>({
  revision: undefined,
  content: '',
  moderationStatus: 'APPROVED',
  moderationReason: '',
})

const rejectVisible = ref(false)
const rejecting = ref(false)
const rejectTarget = ref<AdminCommentRow | null>(null)
const rejectForm = reactive({ reason: '' })

const columns = computed<TableColumnData[]>(() => [
  { title: 'ID', dataIndex: 'id', width: 84 },
  { title: t('admin.ipQueryComments.scope'), slotName: 'network', width: 170 },
  { title: t('admin.ipQueryComments.author'), slotName: 'author', width: 170 },
  { title: t('admin.ipQueryComments.content'), slotName: 'content', width: 320 },
  { title: t('admin.common.status'), slotName: 'status', width: 150 },
  { title: t('admin.ipQueryComments.revision'), slotName: 'revision', width: 84 },
  { title: t('admin.ipQueryComments.job'), slotName: 'job', width: 110 },
  { title: t('admin.ipQueryComments.updatedAt'), slotName: 'updatedAt', width: 175 },
  {
    title: t('admin.common.actions'),
    slotName: 'actions',
    width: 260,
    fixed: 'right',
  },
])

function statusLabel(status: string | null | undefined) {
  if (!status) {
    return t('common.notAvailable')
  }
  return t(`admin.ipQueryComments.statuses.${status}`)
}

function statusColor(status: string | null | undefined) {
  const colors: Record<string, string> = {
    APPROVED: 'green',
    PENDING: 'orange',
    RUNNING: 'arcoblue',
    REJECTED: 'red',
    FAILED: 'red',
  }
  return colors[status ?? ''] ?? 'gray'
}

function authorLabel(record: AdminCommentRow) {
  return record.authorNickname || t('admin.ipQueryComments.anonymous')
}

function jobLabel(record: AdminCommentRow) {
  return record.latestJobStatus
    ? statusLabel(record.latestJobStatus)
    : t('admin.ipQueryComments.noJob')
}

function rejectionReason(value: string) {
  return value.trim() || t('admin.ipQueryComments.defaultRejectReason')
}

async function load() {
  loading.value = true
  try {
    const response = await adminApi.ipQueryComments({
      page: page.value,
      size: pageSize.value,
      keyword: filters.keyword.trim() || undefined,
      scope: filters.scope.trim() || undefined,
      status: filters.status || undefined,
      includeDeleted: filters.includeDeleted,
    })
    records.value = response.records
    total.value = response.total
    page.value = response.current
    pageSize.value = response.size
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

function resetFilters() {
  filters.keyword = ''
  filters.scope = ''
  filters.status = ''
  filters.includeDeleted = false
  search()
}

async function afterChange(updated: AdminCommentDetail) {
  if (detailVisible.value && detail.value?.comment.id === updated.comment.id) {
    detail.value = updated
  }
  await load()
}

async function openDetail(record: AdminCommentRow) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await adminApi.ipQueryComment(record.id)
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    detailLoading.value = false
  }
}

function openCreate() {
  createForm.network = ''
  createForm.authorUserId = ''
  createForm.content = ''
  createForm.moderationStatus = 'APPROVED'
  createForm.moderationReason = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.network.trim() || !createForm.content.trim()) {
    Message.warning(t('admin.ipQueryComments.required'))
    return
  }
  creating.value = true
  try {
    const body: AdminCommentCreateInput = {
      network: createForm.network.trim(),
      content: createForm.content,
      authorUserId: createForm.authorUserId.trim() || undefined,
      moderationStatus: createForm.moderationStatus,
      moderationReason:
        createForm.moderationStatus === 'REJECTED'
          ? rejectionReason(createForm.moderationReason)
          : undefined,
    }
    const created = await adminApi.createIpQueryComment(body)
    Message.success(t('admin.ipQueryComments.created'))
    createVisible.value = false
    detail.value = created
    detailVisible.value = true
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    creating.value = false
  }
}

function openEdit(record: AdminCommentRow) {
  editing.value = record
  editorForm.revision = record.revision
  editorForm.content = record.content
  editorForm.moderationStatus =
    record.moderationStatus === 'FAILED' ? 'PENDING' : record.moderationStatus
  editorForm.moderationReason = record.moderationReason ?? ''
  editorVisible.value = true
}

async function saveEdit() {
  if (!editing.value) {
    return
  }
  if (!editorForm.content?.trim()) {
    Message.warning(t('admin.ipQueryComments.required'))
    return
  }
  editorSaving.value = true
  try {
    const updated = await adminApi.updateIpQueryComment(editing.value.id, {
      revision: editorForm.revision,
      content: editorForm.content,
      moderationStatus: editorForm.moderationStatus,
      moderationReason:
        editorForm.moderationStatus === 'REJECTED'
          ? rejectionReason(editorForm.moderationReason ?? '')
          : undefined,
    })
    Message.success(t('admin.ipQueryComments.updated'))
    editorVisible.value = false
    await afterChange(updated)
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    editorSaving.value = false
  }
}

function approve(record: AdminCommentRow) {
  Modal.confirm({
    title: t('admin.ipQueryComments.approve'),
    content: t('admin.ipQueryComments.approveConfirm'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        const updated = await adminApi.updateIpQueryComment(record.id, {
          revision: record.revision,
          moderationStatus: 'APPROVED',
        })
        Message.success(t('admin.ipQueryComments.approved'))
        await afterChange(updated)
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
      }
    },
  })
}

function openReject(record: AdminCommentRow) {
  rejectTarget.value = record
  rejectForm.reason = ''
  rejectVisible.value = true
}

async function submitReject() {
  if (!rejectTarget.value) {
    return
  }
  rejecting.value = true
  try {
    const updated = await adminApi.updateIpQueryComment(rejectTarget.value.id, {
      revision: rejectTarget.value.revision,
      moderationStatus: 'REJECTED',
      moderationReason: rejectionReason(rejectForm.reason),
    })
    Message.success(t('admin.ipQueryComments.rejected'))
    rejectVisible.value = false
    await afterChange(updated)
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    rejecting.value = false
  }
}

function reaudit(record: AdminCommentRow) {
  Modal.confirm({
    title: t('admin.ipQueryComments.reaudit'),
    content: t('admin.ipQueryComments.reauditConfirm'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        const updated = await adminApi.reauditIpQueryComment(record.id)
        Message.success(t('admin.ipQueryComments.reaudited'))
        await afterChange(updated)
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
      }
    },
  })
}

function confirmHide(record: AdminCommentRow) {
  Modal.confirm({
    title: t('admin.ipQueryComments.hide'),
    content: t('admin.ipQueryComments.hideConfirm'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        const updated = await adminApi.deleteIpQueryComment(record.id)
        Message.success(t('admin.ipQueryComments.hidden'))
        await afterChange(updated)
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
      }
    },
  })
}

function restore(record: AdminCommentRow) {
  Modal.confirm({
    title: t('admin.ipQueryComments.restore'),
    content: t('admin.ipQueryComments.restoreConfirm'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        const updated = await adminApi.restoreIpQueryComment(record.id)
        Message.success(t('admin.ipQueryComments.restored'))
        await afterChange(updated)
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
      }
    },
  })
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader
      :title="t('admin.ipQueryComments.title')"
      :description="t('admin.ipQueryComments.description')"
    >
      <template #actions>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.ipQueryComments.create') }}
        </a-button>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <a-card class="surface-card filter-card">
      <a-form :model="filters" layout="vertical" @submit="search">
        <a-row :gutter="16">
          <a-col :xs="24" :md="8">
            <a-form-item :label="t('admin.ipQueryComments.keyword')">
              <a-input
                v-model="filters.keyword"
                allow-clear
                :placeholder="t('admin.ipQueryComments.keywordPlaceholder')"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="7">
            <a-form-item :label="t('admin.ipQueryComments.scope')">
              <a-input
                v-model="filters.scope"
                allow-clear
                placeholder="192.0.2.0/24"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="5">
            <a-form-item :label="t('admin.common.status')">
              <a-select
                v-model="filters.status"
                allow-clear
                :placeholder="t('admin.common.all')"
              >
                <a-option
                  v-for="status in moderationStatuses"
                  :key="status"
                  :value="status"
                >
                  {{ statusLabel(status) }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="4">
            <a-form-item :label="t('admin.ipQueryComments.includeDeleted')">
              <a-switch v-model="filters.includeDeleted" />
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
        :scroll="{ x: 1450 }"
        row-key="id"
        stripe
      >
        <template #empty>
          <a-empty :description="t('common.noData')" />
        </template>
        <template #network="{ record }">
          <code class="network-cell">{{ record.network }}</code>
        </template>
        <template #author="{ record }">
          <div class="author-cell">
            <strong>{{ authorLabel(record) }}</strong>
            <span v-if="record.authorUserId">UID {{ record.authorUserId }}</span>
            <span v-else>{{ t('admin.ipQueryComments.anonymous') }}</span>
          </div>
        </template>
        <template #content="{ record }">
          <a-tooltip :content="record.content">
            <span class="table-cell-ellipsis comment-preview">
              {{ record.content }}
            </span>
          </a-tooltip>
        </template>
        <template #status="{ record }">
          <a-space wrap>
            <a-tag :color="statusColor(record.moderationStatus)">
              {{ statusLabel(record.moderationStatus) }}
            </a-tag>
            <a-tag v-if="record.deleted" color="gray">
              {{ t('admin.ipQueryComments.deletedTag') }}
            </a-tag>
            <a-tag v-if="record.anonymized" color="gray">
              {{ t('admin.ipQueryComments.anonymizedTag') }}
            </a-tag>
          </a-space>
        </template>
        <template #revision="{ record }">v{{ record.revision }}</template>
        <template #job="{ record }">
          <a-tag :color="statusColor(record.latestJobStatus)">
            {{ jobLabel(record) }}
          </a-tag>
        </template>
        <template #updatedAt="{ record }">
          {{ formatDate(record.updatedAt) }}
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="openDetail(record)">
              <template #icon><IconEye /></template>
              {{ t('admin.ipQueryComments.detail') }}
            </a-button>
            <a-button
              type="text"
              size="small"
              :disabled="record.deleted"
              @click="openEdit(record)"
            >
              <template #icon><IconEdit /></template>
              {{ t('admin.common.edit') }}
            </a-button>
            <a-dropdown trigger="click" position="br">
              <a-button type="text" size="small">
                <template #icon><IconDown /></template>
                {{ t('admin.common.more') }}
              </a-button>
              <template #content>
                <a-doption
                  :disabled="record.deleted"
                  @click="approve(record)"
                >
                  <template #icon><IconCheck /></template>
                  {{ t('admin.ipQueryComments.approve') }}
                </a-doption>
                <a-doption
                  :disabled="record.deleted"
                  @click="openReject(record)"
                >
                  <template #icon><IconClose /></template>
                  {{ t('admin.ipQueryComments.reject') }}
                </a-doption>
                <a-doption
                  :disabled="record.deleted"
                  @click="reaudit(record)"
                >
                  <template #icon><IconSync /></template>
                  {{ t('admin.ipQueryComments.reaudit') }}
                </a-doption>
                <a-doption
                  v-if="!record.deleted"
                  class="danger-option"
                  @click="confirmHide(record)"
                >
                  <template #icon><IconDelete /></template>
                  {{ t('admin.ipQueryComments.hide') }}
                </a-doption>
                <a-doption v-else @click="restore(record)">
                  <template #icon><IconUndo /></template>
                  {{ t('admin.ipQueryComments.restore') }}
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

    <a-drawer
      v-model:visible="detailVisible"
      :title="
        t('admin.ipQueryComments.detailTitle', {
          id: detail?.comment.id ?? '',
        })
      "
      :width="680"
      :footer="false"
    >
      <a-spin :loading="detailLoading">
        <template v-if="detail">
          <a-space wrap class="detail-tags">
            <a-tag :color="statusColor(detail.comment.moderationStatus)">
              {{ statusLabel(detail.comment.moderationStatus) }}
            </a-tag>
            <a-tag v-if="detail.comment.deleted" color="gray">
              {{ t('admin.ipQueryComments.deletedTag') }}
            </a-tag>
            <a-tag v-if="detail.comment.anonymized" color="gray">
              {{ t('admin.ipQueryComments.anonymizedTag') }}
            </a-tag>
            <a-tag color="arcoblue">v{{ detail.comment.revision }}</a-tag>
          </a-space>

          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item :label="t('admin.ipQueryComments.scope')">
              <code>{{ detail.comment.network }}</code>
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.ipQueryComments.author')">
              {{ authorLabel(detail.comment) }}
              <span v-if="detail.comment.authorUserId" class="muted">
                (UID {{ detail.comment.authorUserId }})
              </span>
            </a-descriptions-item>
            <a-descriptions-item
              v-if="detail.comment.parentCommentId"
              :label="t('admin.ipQueryComments.parentComment')"
            >
              #{{ detail.comment.parentCommentId }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.ipQueryComments.job')">
              {{ jobLabel(detail.comment) }}
            </a-descriptions-item>
            <a-descriptions-item
              :label="t('admin.ipQueryComments.reviewGeneration')"
            >
              {{ detail.comment.reviewGeneration }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.ipQueryComments.authorIp')">
              {{ detail.comment.authorIp || t('common.notAvailable') }}
            </a-descriptions-item>
            <a-descriptions-item
              :label="t('admin.ipQueryComments.authorUserAgent')"
            >
              <span class="wrap-text">
                {{ detail.comment.authorUserAgent || t('common.notAvailable') }}
              </span>
            </a-descriptions-item>
            <a-descriptions-item :label="t('admin.common.createdAt')">
              {{ formatDate(detail.comment.createdAt) }}
            </a-descriptions-item>
            <a-descriptions-item
              :label="t('admin.ipQueryComments.updatedAt')"
            >
              {{ formatDate(detail.comment.updatedAt) }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="detail.comment.moderationReason"
              :label="t('admin.ipQueryComments.moderationReason')"
            >
              {{ detail.comment.moderationReason }}
            </a-descriptions-item>
          </a-descriptions>

          <h4>{{ t('admin.ipQueryComments.content') }}</h4>
          <pre class="comment-content">{{ detail.comment.content }}</pre>

          <h4>
            {{
              t('admin.ipQueryComments.revisions', {
                count: detail.revisions.length,
              })
            }}
          </h4>
          <a-timeline v-if="detail.revisions.length">
            <a-timeline-item
              v-for="revision in detail.revisions"
              :key="revision.revision"
            >
              <div class="revision-item">
                <div class="revision-head">
                  <strong>v{{ revision.revision }}</strong>
                  <a-tag :color="statusColor(revision.moderationStatus)">
                    {{ statusLabel(revision.moderationStatus) }}
                  </a-tag>
                  <span class="muted">{{ formatDate(revision.createdAt) }}</span>
                </div>
                <div class="muted revision-meta">
                  {{
                    revision.editorNickname
                      || (revision.editorUserId
                        ? `UID ${revision.editorUserId}`
                        : t('admin.ipQueryComments.systemEditor'))
                  }}
                  <span v-if="revision.editorIp"> · {{ revision.editorIp }}</span>
                </div>
                <div v-if="revision.moderationReason" class="revision-reason">
                  {{ revision.moderationReason }}
                </div>
                <pre class="revision-content">{{ revision.content }}</pre>
              </div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else :description="t('common.noData')" />
        </template>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:visible="createVisible"
      :title="t('admin.ipQueryComments.createTitle')"
      :footer="false"
      width="660px"
    >
      <a-form :model="createForm" layout="vertical">
        <a-alert type="info" show-icon>
          {{ t('admin.ipQueryComments.createHint') }}
        </a-alert>
        <a-row :gutter="16">
          <a-col :xs="24" :md="14">
            <a-form-item :label="t('admin.ipQueryComments.scope')" required>
              <a-input
                v-model="createForm.network"
                placeholder="192.0.2.0/24"
              />
              <template #extra>
                {{ t('admin.ipQueryComments.scopeHint') }}
              </template>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="10">
            <a-form-item :label="t('admin.ipQueryComments.authorUserId')">
              <a-input
                v-model="createForm.authorUserId"
                :placeholder="t('admin.ipQueryComments.authorUserIdHint')"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item :label="t('admin.ipQueryComments.content')" required>
          <a-textarea
            v-model="createForm.content"
            :auto-size="{ minRows: 6, maxRows: 16 }"
          />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :xs="24" :md="10">
            <a-form-item :label="t('admin.ipQueryComments.moderationStatus')">
              <a-select v-model="createForm.moderationStatus">
                <a-option value="APPROVED">
                  {{ statusLabel('APPROVED') }}
                </a-option>
                <a-option value="PENDING">
                  {{ statusLabel('PENDING') }}
                </a-option>
                <a-option value="REJECTED">
                  {{ statusLabel('REJECTED') }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col
            v-if="createForm.moderationStatus === 'REJECTED'"
            :xs="24"
            :md="14"
          >
            <a-form-item
              :label="t('admin.ipQueryComments.moderationReason')"
            >
              <a-input
                v-model="createForm.moderationReason"
                :placeholder="t('admin.ipQueryComments.reasonPlaceholder')"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <p class="form-hint">{{ t('admin.ipQueryComments.pendingHint') }}</p>
        <div class="modal-actions">
          <a-button @click="createVisible = false">
            {{ t('admin.common.cancel') }}
          </a-button>
          <a-button type="primary" :loading="creating" @click="submitCreate">
            {{ t('admin.common.create') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="editorVisible"
      :title="t('admin.ipQueryComments.editTitle', { id: editing?.id ?? '' })"
      :footer="false"
      width="660px"
    >
      <a-form :model="editorForm" layout="vertical">
        <a-alert type="info" show-icon>
          {{ t('admin.ipQueryComments.editHint') }}
        </a-alert>
        <a-form-item :label="t('admin.ipQueryComments.content')" required>
          <a-textarea
            v-model="editorForm.content"
            :auto-size="{ minRows: 8, maxRows: 20 }"
          />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :xs="24" :md="10">
            <a-form-item :label="t('admin.ipQueryComments.moderationStatus')">
              <a-select v-model="editorForm.moderationStatus">
                <a-option value="APPROVED">
                  {{ statusLabel('APPROVED') }}
                </a-option>
                <a-option value="PENDING">
                  {{ statusLabel('PENDING') }}
                </a-option>
                <a-option value="REJECTED">
                  {{ statusLabel('REJECTED') }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col
            v-if="editorForm.moderationStatus === 'REJECTED'"
            :xs="24"
            :md="14"
          >
            <a-form-item
              :label="t('admin.ipQueryComments.moderationReason')"
            >
              <a-input
                v-model="editorForm.moderationReason"
                :placeholder="t('admin.ipQueryComments.reasonPlaceholder')"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <div class="modal-actions">
          <a-button @click="editorVisible = false">
            {{ t('admin.common.cancel') }}
          </a-button>
          <a-button type="primary" :loading="editorSaving" @click="saveEdit">
            {{ t('admin.common.save') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="rejectVisible"
      :title="t('admin.ipQueryComments.rejectTitle', { id: rejectTarget?.id ?? '' })"
      :footer="false"
      width="520px"
    >
      <a-form :model="rejectForm" layout="vertical">
        <a-form-item :label="t('admin.ipQueryComments.moderationReason')">
          <a-textarea
            v-model="rejectForm.reason"
            :auto-size="{ minRows: 3, maxRows: 8 }"
            :placeholder="t('admin.ipQueryComments.reasonPlaceholder')"
          />
          <template #extra>
            {{ t('admin.ipQueryComments.rejectHint') }}
          </template>
        </a-form-item>
        <div class="modal-actions">
          <a-button @click="rejectVisible = false">
            {{ t('admin.common.cancel') }}
          </a-button>
          <a-button
            type="primary"
            status="danger"
            :loading="rejecting"
            @click="submitReject"
          >
            {{ t('admin.ipQueryComments.reject') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.network-cell {
  font-size: 12px;
}

.author-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.author-cell span {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.comment-preview {
  display: block;
  color: var(--color-text-2);
}

.detail-tags {
  margin-bottom: 16px;
}

.muted {
  color: var(--color-text-3);
  font-size: 12px;
}

.wrap-text {
  word-break: break-all;
}

.comment-content,
.revision-content {
  margin: 8px 0 20px;
  padding: 12px 14px;
  color: var(--color-text-1);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--color-fill-2);
  border-radius: var(--app-radius);
}

.revision-content {
  margin: 8px 0 0;
}

.revision-item {
  padding-bottom: 4px;
}

.revision-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.revision-meta {
  margin-top: 4px;
}

.revision-reason {
  margin-top: 6px;
  color: rgb(var(--danger-6));
  font-size: 12px;
}

.form-hint {
  margin: 0 0 12px;
  color: var(--color-text-3);
  font-size: 12px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

:deep(.danger-option) {
  color: rgb(var(--danger-6));
}
</style>
