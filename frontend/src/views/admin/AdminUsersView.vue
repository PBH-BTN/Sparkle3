<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconDelete,
  IconEdit,
  IconLock,
  IconPlus,
  IconRefresh,
  IconSearch,
  IconTrophy,
  IconUnlock,
  IconUser,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import TablePager from '@/components/TablePager.vue'
import { adminApi } from '@/api/admin'
import type { AdminUser, AdminUserInput } from '@/api/adminTypes'
import { useSessionStore } from '@/stores/session'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatPointBytes } = useFormatters()
const session = useSessionStore()
const records = ref<AdminUser[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = reactive({
  keyword: '',
  role: '',
  status: '',
})

const editorVisible = ref(false)
const editorSaving = ref(false)
const editingId = ref<string | null>(null)
const editorForm = reactive<AdminUserInput>({
  email: '',
  nickname: '',
  avatarUrl: '/img/anonymous-user.png',
  role: 'user',
  avatarVisibility: 'BLURRED',
  usernameVisibility: 'BLURRED',
  pointsVisibility: 'SHOW',
})

const banVisible = ref(false)
const banSaving = ref(false)
const banUser = ref<AdminUser | null>(null)
const banReason = ref('')

const pointsVisible = ref(false)
const pointsSaving = ref(false)
const pointsUser = ref<AdminUser | null>(null)
const pointsMode = ref<'add' | 'deduct'>('add')
const pointsAmount = ref<number | undefined>(undefined)
const pointsReason = ref('')

function showPointRaw(value: string | number | null | undefined) {
  if (value == null) return false
  try {
    const points = BigInt(String(value))
    return (points < 0n ? -points : points) >= 1024n
  } catch {
    return false
  }
}

const columns = computed<TableColumnData[]>(() => [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: t('admin.users.nickname'), slotName: 'user', width: 190 },
  { title: t('admin.users.email'), dataIndex: 'email', slotName: 'email', width: 210 },
  { title: t('admin.users.access'), slotName: 'access', width: 120 },
  { title: t('admin.users.points'), slotName: 'points', width: 140 },
  { title: t('admin.users.registerAt'), slotName: 'registerAt', width: 150 },
  { title: t('admin.users.lastLoginAt'), slotName: 'lastLoginAt', width: 150 },
  { title: t('admin.common.actions'), slotName: 'actions', width: 280 },
])

async function load() {
  loading.value = true
  try {
    const result = await adminApi.users({
      page: page.value,
      size: pageSize.value,
      keyword: filters.keyword.trim() || undefined,
      role: filters.role || undefined,
      status: filters.status || undefined,
    })
    records.value = result.records
    total.value = result.total
    page.value = result.current
    pageSize.value = result.size
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
  filters.role = ''
  filters.status = ''
  search()
}

function openCreate() {
  editingId.value = null
  Object.assign(editorForm, {
    email: '',
    nickname: '',
    avatarUrl: '/img/anonymous-user.png',
    role: 'user',
    avatarVisibility: 'BLURRED',
    usernameVisibility: 'BLURRED',
    pointsVisibility: 'SHOW',
  })
  editorVisible.value = true
}

function openEdit(user: AdminUser) {
  editingId.value = user.id
  Object.assign(editorForm, {
    email: user.email,
    nickname: user.nickname,
    avatarUrl: user.avatarUrl,
    role: user.role === 'admin' || user.role === 'moderator' ? user.role : 'user',
    avatarVisibility: user.avatarVisibility,
    usernameVisibility: user.usernameVisibility,
    pointsVisibility: user.pointsVisibility,
  })
  editorVisible.value = true
}

async function saveUser() {
  if (!editorForm.email.includes('@')) {
    Message.warning(t('admin.users.emailRequired'))
    return
  }
  if (!editorForm.nickname.trim()) {
    Message.warning(t('admin.users.nicknameRequired'))
    return
  }
  editorSaving.value = true
  try {
    if (editingId.value == null) {
      await adminApi.createUser(editorForm)
      Message.success(t('admin.common.created'))
    } else {
      await adminApi.updateUser(editingId.value, editorForm)
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

function openBan(user: AdminUser) {
  banUser.value = user
  banReason.value = user.bannedReason ?? ''
  banVisible.value = true
}

async function confirmBan() {
  if (!banUser.value) return
  banSaving.value = true
  try {
    await adminApi.setUserBanned(banUser.value.id, true, banReason.value.trim())
    Message.success(t('admin.common.updated'))
    banVisible.value = false
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    banSaving.value = false
  }
}

async function restoreUser(user: AdminUser) {
  try {
    await adminApi.setUserBanned(user.id, false)
    Message.success(t('admin.common.updated'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

function openPoints(user: AdminUser) {
  pointsUser.value = user
  pointsMode.value = 'add'
  pointsAmount.value = undefined
  pointsReason.value = ''
  pointsVisible.value = true
}

async function submitPointsAdjustment() {
  if (!pointsUser.value) return
  const amount = Math.trunc(pointsAmount.value ?? 0)
  const reason = pointsReason.value.trim()
  if (amount <= 0) {
    Message.warning(t('admin.users.pointsAmountRequired'))
    return
  }
  if (!reason) {
    Message.warning(t('admin.users.pointsReasonRequired'))
    return
  }
  pointsSaving.value = true
  try {
    const signedAmount = pointsMode.value === 'deduct' ? `-${amount}` : String(amount)
    await adminApi.adjustUserPoints(pointsUser.value.id, signedAmount, reason)
    Message.success(t('admin.users.pointsAdjusted'))
    pointsVisible.value = false
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    pointsSaving.value = false
  }
}

async function deleteUser(user: AdminUser) {
  try {
    await adminApi.deleteUser(user.id)
    Message.success(t('admin.common.deleted'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

function isNeverLogin(value: string) {
  return new Date(value).getTime() <= 0
}

function roleLabel(role: string) {
  if (role === 'admin') return t('admin.users.adminRole')
  if (role === 'moderator') return t('admin.users.moderatorRole')
  return t('admin.users.userRole')
}

function privacySummary(user: AdminUser) {
  return [
    t(`privacy.avatarOptions.${user.avatarVisibility}`),
    t(`privacy.usernameOptions.${user.usernameVisibility}`),
    t(`privacy.pointsOptions.${user.pointsVisibility}`),
  ].join(' · ')
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('admin.users.title')" :description="t('admin.users.description')">
      <template #actions>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.users.create') }}
        </a-button>
      </template>
    </PageHeader>

    <a-card class="surface-card filter-card">
      <a-form :model="filters" layout="vertical" @submit="search">
        <a-row :gutter="16">
          <a-col :xs="24" :md="10">
            <a-form-item :label="t('admin.common.keyword')">
              <a-input
                v-model="filters.keyword"
                allow-clear
                :placeholder="`ID / ${t('admin.users.email')} / ${t('admin.users.nickname')}`"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="12" :md="5">
            <a-form-item :label="t('admin.users.role')">
              <a-select v-model="filters.role" allow-clear>
                <a-option value="user">{{ t('admin.users.userRole') }}</a-option>
                <a-option value="moderator">{{ t('admin.users.moderatorRole') }}</a-option>
                <a-option value="admin">{{ t('admin.users.adminRole') }}</a-option>
              </a-select>
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
        :scroll="{ x: 1320 }"
        row-key="id"
        stripe
      >
        <template #empty>
          <a-empty :description="t('common.noData')" />
        </template>
        <template #user="{ record }">
          <div class="user-cell">
            <a-avatar :size="34" :image-url="record.avatarUrl">
              <IconUser />
            </a-avatar>
            <div>
              <div class="user-name-row">
                <strong>{{ record.nickname }}</strong>
              </div>
              <span v-if="record.githubLogin">GitHub · {{ record.githubLogin }}</span>
            </div>
          </div>
        </template>
        <template #email="{ record }">
          <a class="email-link" :href="`mailto:${record.email}`" :title="record.email">
            {{ record.email }}
          </a>
        </template>
        <template #access="{ record }">
          <div class="access-cell">
            <a-tag :color="record.role === 'admin' ? 'arcoblue' : record.role === 'moderator' ? 'purple' : 'gray'">
              {{ roleLabel(record.role) }}
            </a-tag>
            <span>{{ privacySummary(record) }}</span>
            <a-tooltip v-if="record.bannedAt" :content="record.bannedReason || ''">
              <a-tag color="red" size="small">{{ t('admin.common.banned') }}</a-tag>
            </a-tooltip>
            <a-tag v-else color="green" size="small">{{ t('admin.common.active') }}</a-tag>
          </div>
        </template>
        <template #points="{ record }">
          <div class="points-cell">
            <strong>{{ formatPointBytes(record.scoreBytes) }}</strong>
            <span v-if="showPointRaw(record.scoreBytes)">{{ record.scoreBytes }} Bytes</span>
          </div>
        </template>
        <template #registerAt="{ record }">
          <span class="nowrap">{{ formatDate(record.registerAt) }}</span>
        </template>
        <template #lastLoginAt="{ record }">
          <span class="nowrap">
            {{ isNeverLogin(record.lastLoginAt) ? t('admin.users.neverLogin') : formatDate(record.lastLoginAt) }}
          </span>
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="openPoints(record)">
              <template #icon><IconTrophy /></template>
              {{ t('admin.users.adjustPoints') }}
            </a-button>
            <a-button type="text" size="small" @click="openEdit(record)">
              <template #icon><IconEdit /></template>
              {{ t('admin.common.edit') }}
            </a-button>
            <a-button
              v-if="record.bannedAt"
              type="text"
              size="small"
              :disabled="record.id === session.user?.id"
              @click="restoreUser(record)"
            >
              <template #icon><IconUnlock /></template>
              {{ t('admin.users.restore') }}
            </a-button>
            <a-button
              v-else
              type="text"
              size="small"
              :disabled="record.id === session.user?.id"
              @click="openBan(record)"
            >
              <template #icon><IconLock /></template>
              {{ t('admin.users.suspend') }}
            </a-button>
            <a-popconfirm :content="t('admin.users.deleteConfirm')" @ok="deleteUser(record)">
              <a-button
                status="danger"
                type="text"
                size="small"
                :disabled="record.id === session.user?.id"
              >
                <template #icon><IconDelete /></template>
              </a-button>
            </a-popconfirm>
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
      :title="editingId == null ? t('admin.users.create') : t('admin.users.edit')"
      :footer="false"
      width="640px"
    >
      <a-form :model="editorForm" layout="vertical">
        <a-alert v-if="editingId == null" type="info" class="editor-hint">
          {{ t('admin.users.createHint') }}
        </a-alert>
        <a-row :gutter="16">
          <a-col :xs="24" :md="12">
            <a-form-item :label="t('admin.users.email')" required>
              <a-input v-model="editorForm.email" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item :label="t('admin.users.nickname')" required>
              <a-input v-model="editorForm.nickname" :max-length="255" show-word-limit />
            </a-form-item>
          </a-col>
          <a-col :xs="24">
            <a-form-item :label="t('admin.users.avatar')">
              <a-input v-model="editorForm.avatarUrl" />
            </a-form-item>
          </a-col>
          <a-col :xs="12">
            <a-form-item :label="t('admin.users.role')">
              <a-select v-model="editorForm.role">
                <a-option value="user">{{ t('admin.users.userRole') }}</a-option>
                <a-option value="moderator">{{ t('admin.users.moderatorRole') }}</a-option>
                <a-option value="admin">{{ t('admin.users.adminRole') }}</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24">
            <a-form-item :label="t('admin.users.privacy')">
              <a-space wrap>
                <a-select v-model="editorForm.avatarVisibility" style="width: 180px">
                  <a-option value="ORIGINAL">{{ t('privacy.avatarOptions.ORIGINAL') }}</a-option>
                  <a-option value="BLURRED">{{ t('privacy.avatarOptions.BLURRED') }}</a-option>
                  <a-option value="HIDDEN">{{ t('privacy.avatarOptions.HIDDEN') }}</a-option>
                </a-select>
                <a-select v-model="editorForm.usernameVisibility" style="width: 180px">
                  <a-option value="FULL">{{ t('privacy.usernameOptions.FULL') }}</a-option>
                  <a-option value="BLURRED">{{ t('privacy.usernameOptions.BLURRED') }}</a-option>
                </a-select>
                <a-select v-model="editorForm.pointsVisibility" style="width: 180px">
                  <a-option value="SHOW">{{ t('privacy.pointsOptions.SHOW') }}</a-option>
                  <a-option value="HIDDEN">{{ t('privacy.pointsOptions.HIDDEN') }}</a-option>
                </a-select>
              </a-space>
            </a-form-item>
          </a-col>
        </a-row>
        <div class="modal-actions">
          <a-button @click="editorVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button type="primary" :loading="editorSaving" @click="saveUser">
            {{ t('admin.common.save') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="banVisible"
      :title="t('admin.users.suspendTitle')"
      :footer="false"
      width="520px"
    >
      <a-form :model="{ banReason }" layout="vertical">
        <a-form-item :label="t('admin.users.suspendReason')" required>
          <a-textarea
            v-model="banReason"
            :placeholder="t('admin.users.suspendReasonPlaceholder')"
            :auto-size="{ minRows: 3, maxRows: 6 }"
          />
        </a-form-item>
        <div class="modal-actions">
          <a-button @click="banVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button status="danger" type="primary" :loading="banSaving" @click="confirmBan">
            {{ t('admin.users.suspend') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="pointsVisible"
      :title="t('admin.users.pointsTitle')"
      :footer="false"
      width="540px"
    >
      <div v-if="pointsUser" class="points-adjust-current">
        <div>
          <strong>{{ pointsUser.nickname }}</strong>
          <span>UID {{ pointsUser.id }}</span>
        </div>
        <div>
          <strong>{{ formatPointBytes(pointsUser.scoreBytes) }}</strong>
          <span v-if="showPointRaw(pointsUser.scoreBytes)">{{ pointsUser.scoreBytes }} Bytes</span>
        </div>
      </div>
      <a-form :model="{ pointsAmount, pointsReason }" layout="vertical">
        <a-form-item :label="t('admin.users.pointsOperation')">
          <a-radio-group v-model="pointsMode" type="button">
            <a-radio value="add">{{ t('admin.users.addPoints') }}</a-radio>
            <a-radio value="deduct">{{ t('admin.users.deductPoints') }}</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item :label="t('admin.users.pointsAmount')" required>
          <a-input-number
            v-model="pointsAmount"
            :min="1"
            :precision="0"
            :max="Number.MAX_SAFE_INTEGER"
            hide-button
            style="width: 100%"
          >
            <template #suffix>Bytes</template>
          </a-input-number>
        </a-form-item>
        <a-form-item :label="t('admin.users.pointsReason')" required>
          <a-textarea
            v-model="pointsReason"
            :max-length="500"
            show-word-limit
            :placeholder="t('admin.users.pointsReasonPlaceholder')"
            :auto-size="{ minRows: 3, maxRows: 6 }"
          />
        </a-form-item>
        <a-alert type="warning" show-icon>
          {{ t('admin.users.pointsHint') }}
        </a-alert>
        <div class="modal-actions">
          <a-button @click="pointsVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button
            :status="pointsMode === 'deduct' ? 'danger' : 'normal'"
            type="primary"
            :loading="pointsSaving"
            @click="submitPointsAdjustment"
          >
            {{ t('common.confirm') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-cell > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-cell span {
  color: var(--color-text-3);
  font-size: 11px;
  white-space: nowrap;
}

.user-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-name-row strong {
  white-space: nowrap;
}

.email-link {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.access-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.access-cell span {
  color: var(--color-text-3);
  font-size: 11px;
}

.points-cell {
  display: flex;
  flex-direction: column;
}

.points-cell strong {
  color: rgb(var(--primary-6));
}

.points-cell span {
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
}

.points-adjust-current {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.points-adjust-current > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.points-adjust-current > div:last-child {
  align-items: flex-end;
  text-align: right;
}

.points-adjust-current span {
  color: var(--color-text-3);
  font-size: 11px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.editor-hint {
  margin-bottom: 18px;
}
</style>
