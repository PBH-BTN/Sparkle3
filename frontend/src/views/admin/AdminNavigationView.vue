<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconDelete,
  IconEdit,
  IconMenu,
  IconPlus,
  IconRefresh,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  AdminNavigationItem,
  NavigationItemInput,
} from '@/api/adminTypes'
import type { NavigationIcon } from '@/api/types'
import { useSessionStore } from '@/stores/session'
import { errorMessage, useFormatters } from '@/utils/format'

const iconOptions: NavigationIcon[] = [
  'home',
  'apps',
  'ban',
  'search',
  'swarm',
  'ranking',
  'link',
  'file',
  'dashboard',
]

const { t, locale } = useI18n()
const { formatDate } = useFormatters()
const session = useSessionStore()
const loading = ref(true)
const saving = ref(false)
const records = ref<AdminNavigationItem[]>([])
const editorVisible = ref(false)
const editingId = ref<string | null>(null)
const editingBuiltIn = ref(false)
const editor = ref<NavigationItemInput>(emptyEditor(10))

const columns = computed<TableColumnData[]>(() => [
  { title: t('admin.common.status'), slotName: 'status', width: 100 },
  { title: t('admin.navigation.type'), slotName: 'type', width: 100 },
  { title: t('admin.navigation.label'), slotName: 'label', width: 210 },
  { title: t('admin.navigation.target'), slotName: 'href', width: 280 },
  { title: t('admin.navigation.icon'), slotName: 'icon', width: 120 },
  { title: t('admin.navigation.sortOrder'), dataIndex: 'sortOrder', width: 90 },
  { title: t('admin.navigation.updatedAt'), slotName: 'updatedAt', width: 170 },
  { title: t('admin.common.actions'), slotName: 'actions', width: 240, fixed: 'right' },
])

function emptyEditor(sortOrder: number): NavigationItemInput {
  return {
    labelZh: '',
    labelEn: '',
    href: '',
    icon: 'link',
    sortOrder,
    enabled: true,
    openInNewTab: false,
  }
}

function displayLabel(item: AdminNavigationItem) {
  return locale.value.startsWith('en')
    ? item.labelEn || item.labelZh
    : item.labelZh || item.labelEn
}

function iconLabel(icon: NavigationIcon) {
  return t(`admin.navigation.icons.${icon}`)
}

async function load() {
  loading.value = true
  try {
    records.value = await adminApi.navigation()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  editingBuiltIn.value = false
  const nextOrder = records.value.reduce(
    (maximum, item) => Math.max(maximum, item.sortOrder),
    0,
  ) + 10
  editor.value = emptyEditor(nextOrder)
  editorVisible.value = true
}

function openEdit(item: AdminNavigationItem) {
  editingId.value = item.id
  editingBuiltIn.value = item.builtIn
  editor.value = {
    labelZh: item.labelZh,
    labelEn: item.labelEn,
    href: item.href,
    icon: item.icon,
    sortOrder: item.sortOrder,
    enabled: item.enabled,
    openInNewTab: item.openInNewTab,
  }
  editorVisible.value = true
}

function validTarget(value: string) {
  const target = value.trim()
  if (target.startsWith('//')) return false
  if (target.startsWith('/')) return true
  try {
    const url = new URL(target)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

async function save() {
  if (!editor.value.labelZh.trim() || !editor.value.labelEn.trim()) {
    Message.warning(t('admin.navigation.labelsRequired'))
    return
  }
  if (!validTarget(editor.value.href)) {
    Message.warning(t('admin.navigation.invalidTarget'))
    return
  }
  saving.value = true
  try {
    if (editingId.value == null) {
      await adminApi.createNavigationItem(editor.value)
      Message.success(t('admin.common.created'))
    } else {
      await adminApi.updateNavigationItem(editingId.value, editor.value)
      Message.success(t('admin.common.updated'))
    }
    editorVisible.value = false
    await Promise.all([load(), session.load().catch(() => undefined)])
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(item: AdminNavigationItem) {
  try {
    await adminApi.updateNavigationItem(item.id, toInput(item, !item.enabled))
    Message.success(item.enabled ? t('admin.navigation.disabled') : t('admin.navigation.enabled'))
    await Promise.all([load(), session.load().catch(() => undefined)])
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

async function remove(item: AdminNavigationItem) {
  try {
    await adminApi.deleteNavigationItem(item.id)
    Message.success(t('admin.common.deleted'))
    await Promise.all([load(), session.load().catch(() => undefined)])
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

function toInput(item: AdminNavigationItem, enabled = item.enabled): NavigationItemInput {
  return {
    labelZh: item.labelZh,
    labelEn: item.labelEn,
    href: item.href,
    icon: item.icon,
    sortOrder: item.sortOrder,
    enabled,
    openInNewTab: item.openInNewTab,
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader
      :title="t('admin.navigation.title')"
      :description="t('admin.navigation.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.navigation.create') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      <template #title>{{ t('admin.navigation.hintTitle') }}</template>
      {{ t('admin.navigation.hint') }}
    </a-alert>

    <a-card class="surface-card table-card">
      <a-table
        :columns="columns"
        :data="records"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1320 }"
        row-key="id"
      >
        <template #status="{ record }">
          <a-tag :color="record.enabled ? 'green' : 'gray'">
            {{ record.enabled ? t('admin.common.enabled') : t('admin.common.disabled') }}
          </a-tag>
        </template>
        <template #type="{ record }">
          <a-tag :color="record.builtIn ? 'arcoblue' : 'purple'">
            {{
              record.builtIn
                ? t('admin.navigation.builtIn')
                : t('admin.navigation.custom')
            }}
          </a-tag>
        </template>
        <template #label="{ record }">
          <div class="navigation-label">
            <strong>{{ displayLabel(record) }}</strong>
            <span>{{ record.labelZh }} / {{ record.labelEn }}</span>
          </div>
        </template>
        <template #href="{ record }">
          <code class="navigation-target">{{ record.href }}</code>
        </template>
        <template #icon="{ record }">
          <span>{{ iconLabel(record.icon) }}</span>
        </template>
        <template #updatedAt="{ record }">
          {{ formatDate(record.updatedAt) }}
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button size="small" @click="openEdit(record)">
              <template #icon><IconEdit /></template>
              {{ t('admin.common.edit') }}
            </a-button>
            <a-button size="small" @click="toggleEnabled(record)">
              {{ record.enabled ? t('admin.navigation.disable') : t('admin.navigation.enable') }}
            </a-button>
            <a-popconfirm
              v-if="!record.builtIn"
              :content="t('admin.navigation.deleteConfirm')"
              @ok="remove(record)"
            >
              <a-button size="small" status="danger">
                <template #icon><IconDelete /></template>
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
        <template #empty>
          <a-empty :description="t('admin.navigation.noData')" />
        </template>
      </a-table>
    </a-card>

    <a-drawer
      v-model:visible="editorVisible"
      :width="620"
      :footer="false"
      unmount-on-close
    >
      <template #title>
        <span class="drawer-title">
          <IconMenu />
          {{
            editingId == null
              ? t('admin.navigation.create')
              : t('admin.navigation.edit')
          }}
        </span>
      </template>

      <a-form :model="editor" layout="vertical" @submit="save">
        <a-alert v-if="editingBuiltIn" type="info" class="built-in-hint">
          {{ t('admin.navigation.builtInHint') }}
        </a-alert>
        <div class="editor-grid">
          <a-form-item :label="t('admin.navigation.labelZh')" required>
            <a-input v-model="editor.labelZh" :max-length="100" show-word-limit />
          </a-form-item>
          <a-form-item :label="t('admin.navigation.labelEn')" required>
            <a-input v-model="editor.labelEn" :max-length="100" show-word-limit />
          </a-form-item>
        </div>
        <a-form-item :label="t('admin.navigation.target')" required>
          <a-input
            v-model="editor.href"
            :disabled="editingBuiltIn"
            placeholder="/custom-page 或 https://example.com"
          />
          <template #extra>{{ t('admin.navigation.targetHint') }}</template>
        </a-form-item>
        <div class="editor-grid">
          <a-form-item :label="t('admin.navigation.icon')">
            <a-select v-model="editor.icon">
              <a-option v-for="icon in iconOptions" :key="icon" :value="icon">
                {{ iconLabel(icon) }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="t('admin.navigation.sortOrder')">
            <a-input-number v-model="editor.sortOrder" :min="-10000" :max="10000" />
          </a-form-item>
        </div>
        <div class="editor-grid">
          <a-form-item :label="t('admin.navigation.enabled')">
            <a-switch v-model="editor.enabled" />
          </a-form-item>
          <a-form-item :label="t('admin.navigation.openInNewTab')">
            <a-switch v-model="editor.openInNewTab" />
          </a-form-item>
        </div>
        <div class="drawer-actions">
          <a-button @click="editorVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button type="primary" :loading="saving" @click="save">
            {{ t('admin.common.save') }}
          </a-button>
        </div>
      </a-form>
    </a-drawer>
  </div>
</template>

<style scoped>
.navigation-label {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.navigation-label strong,
.navigation-label span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.navigation-label span {
  color: var(--color-text-3);
  font-size: 11px;
}

.navigation-target {
  overflow-wrap: anywhere;
  font-size: 12px;
}

.drawer-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.built-in-hint {
  margin-bottom: 18px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

@media (max-width: 620px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
