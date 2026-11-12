<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconCode,
  IconDelete,
  IconEdit,
  IconEye,
  IconPlus,
  IconRefresh,
  IconSearch,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import TablePager from '@/components/TablePager.vue'
import AdminGeoIpCityBlacklistView from '@/views/admin/AdminGeoIpCityBlacklistView.vue'
import { adminApi } from '@/api/admin'
import type { ManualRule, RuleInput, RuleSummary, RuleType } from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatNumber } = useFormatters()
const route = useRoute()
const router = useRouter()
const activeTab = computed({
  get: () => (route.query.tab === 'geoip' ? 'geoip' : 'manual'),
  set: (value: string) => {
    const query = { ...route.query }
    if (value === 'geoip') {
      query.tab = 'geoip'
    } else {
      delete query.tab
    }
    router.replace({ path: route.path, query })
  },
})
const ruleTypes: RuleType[] = ['ip_denylist', 'peer_id', 'client_name', 'port', 'script']
const records = ref<ManualRule[]>([])
const summary = ref<RuleSummary[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = reactive({
  keyword: '',
  type: '',
})

const editorVisible = ref(false)
const editorSaving = ref(false)
const editingId = ref<number | null>(null)
const editor = reactive<RuleInput>({
  type: 'ip_denylist',
  category: '',
  content: '',
})
const previewVisible = ref(false)
const previewLoading = ref(false)
const generatedContent = ref('')

const columns = computed<TableColumnData[]>(() => [
  { title: 'ID', dataIndex: 'id', width: 90 },
  { title: t('admin.rules.type'), slotName: 'type', width: 150 },
  { title: t('admin.rules.category'), slotName: 'category', width: 240, ellipsis: true },
  { title: t('admin.rules.content'), slotName: 'content', width: 560, ellipsis: true },
  { title: t('admin.common.actions'), slotName: 'actions', width: 190, fixed: 'right' },
])

function typeLabel(type: string) {
  return t(`admin.rules.types.${type}`)
}

function summaryCount(type: string) {
  return summary.value.find((item) => item.type === type)?.count ?? 0
}

async function load() {
  loading.value = true
  try {
    const [result, summaryResult] = await Promise.all([
      adminApi.rules({
        page: page.value,
        size: pageSize.value,
        keyword: filters.keyword.trim() || undefined,
        type: filters.type || undefined,
      }),
      adminApi.ruleSummary(),
    ])
    records.value = result.records
    total.value = result.total
    page.value = result.current
    pageSize.value = result.size
    summary.value = summaryResult
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
  filters.type = ''
  search()
}

function filterByType(type: string) {
  filters.type = type
  search()
}

function openCreate() {
  editingId.value = null
  Object.assign(editor, {
    type: filters.type || 'ip_denylist',
    category: '',
    content: '',
  })
  editorVisible.value = true
}

function openEdit(rule: ManualRule) {
  editingId.value = rule.id
  Object.assign(editor, {
    type: rule.type,
    category: rule.category,
    content: rule.content,
  })
  editorVisible.value = true
}

async function saveRule() {
  if (!editor.category.trim() || !editor.content.trim()) {
    Message.warning(t('admin.common.requiredFields'))
    return
  }
  editorSaving.value = true
  try {
    if (editingId.value == null) {
      await adminApi.createRule(editor)
      Message.success(t('admin.common.created'))
    } else {
      await adminApi.updateRule(editingId.value, editor)
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

async function deleteRule(rule: ManualRule) {
  try {
    await adminApi.deleteRule(rule.id)
    Message.success(t('admin.common.deleted'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

async function openPreview() {
  previewVisible.value = true
  previewLoading.value = true
  try {
    generatedContent.value = await adminApi.generatedRules()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    previewLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack rules-page">
    <PageHeader
      :title="
        activeTab === 'geoip'
          ? t('admin.geoIpCityBlacklist.title')
          : t('admin.rules.title')
      "
      :description="
        activeTab === 'geoip'
          ? t('admin.geoIpCityBlacklist.description')
          : t('admin.rules.description')
      "
    >
      <template v-if="activeTab === 'manual'" #actions>
        <a-button @click="openPreview">
          <template #icon><IconEye /></template>
          {{ t('admin.rules.preview') }}
        </a-button>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.rules.create') }}
        </a-button>
      </template>
    </PageHeader>

    <a-tabs
      v-model:active-key="activeTab"
      type="rounded"
      lazy-load
      class="rules-tabs"
    >
      <a-tab-pane key="manual" :title="t('admin.rules.title')">
        <div class="page-stack">
          <div class="summary-grid">
      <button
        v-for="type in ruleTypes"
        :key="type"
        type="button"
        class="summary-card"
        :class="{ 'summary-card--active': filters.type === type }"
        @click="filterByType(type)"
      >
        <span class="summary-icon"><IconCode /></span>
        <span>{{ typeLabel(type) }}</span>
        <strong>{{ formatNumber(summaryCount(type)) }}</strong>
      </button>
    </div>

    <a-card class="surface-card filter-card">
      <a-form :model="filters" layout="vertical" @submit="search">
        <a-row :gutter="16">
          <a-col :xs="24" :md="12">
            <a-form-item :label="t('admin.common.keyword')">
              <a-input v-model="filters.keyword" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="8">
            <a-form-item :label="t('admin.rules.type')">
              <a-select v-model="filters.type" allow-clear>
                <a-option v-for="type in ruleTypes" :key="type" :value="type">
                  {{ typeLabel(type) }}
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
        :scroll="{ x: 1220 }"
        row-key="id"
        stripe
      >
        <template #empty><a-empty :description="t('common.noData')" /></template>
        <template #type="{ record }">
          <a-tag color="arcoblue">{{ typeLabel(record.type) }}</a-tag>
        </template>
        <template #category="{ record }">
          <span class="table-cell-ellipsis" :title="record.category">
            {{ record.category }}
          </span>
        </template>
        <template #content="{ record }">
          <a-tooltip :content="record.content">
            <code class="content-preview table-cell-ellipsis">
              {{ record.content }}
            </code>
          </a-tooltip>
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="openEdit(record)">
              <template #icon><IconEdit /></template>
              {{ t('admin.common.edit') }}
            </a-button>
            <a-popconfirm :content="t('admin.rules.deleteConfirm')" @ok="deleteRule(record)">
              <a-button status="danger" type="text" size="small">
                <template #icon><IconDelete /></template>
                {{ t('admin.common.delete') }}
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
      :title="editingId == null ? t('admin.rules.create') : t('admin.rules.edit')"
      :footer="false"
      width="760px"
    >
      <a-form :model="editor" layout="vertical">
        <a-row :gutter="16">
          <a-col :xs="24" :md="8">
            <a-form-item :label="t('admin.rules.type')" required>
              <a-select v-model="editor.type">
                <a-option v-for="type in ruleTypes" :key="type" :value="type">
                  {{ typeLabel(type) }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="16">
            <a-form-item :label="t('admin.rules.category')" required>
              <a-input v-model="editor.category" />
            </a-form-item>
          </a-col>
          <a-col :xs="24">
            <a-form-item :label="t('admin.rules.content')" required>
              <a-textarea
                v-model="editor.content"
                :auto-size="{ minRows: 10, maxRows: 24 }"
                class="rule-editor"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <div class="modal-actions">
          <a-button @click="editorVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button type="primary" :loading="editorSaving" @click="saveRule">
            {{ t('admin.common.save') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <a-drawer
      v-model:visible="previewVisible"
      :title="t('admin.rules.generated')"
      :width="760"
      :footer="false"
    >
      <a-spin :loading="previewLoading" class="preview-spin">
        <pre class="generated-preview">{{ generatedContent || t('common.noData') }}</pre>
      </a-spin>
    </a-drawer>
        </div>
      </a-tab-pane>
      <a-tab-pane
        key="geoip"
        :title="t('admin.geoIpCityBlacklist.title')"
      >
        <AdminGeoIpCityBlacklistView embedded />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 14px;
  color: var(--color-text-2);
  text-align: left;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  cursor: pointer;
}

.summary-card:hover,
.summary-card--active {
  border-color: rgb(var(--primary-6));
}

.summary-card > span:nth-child(2) {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-card strong {
  color: var(--color-text-1);
  font-size: 20px;
}

.summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
  border-radius: 6px;
}

.content-preview {
  color: var(--color-text-2);
  font-size: 12px;
}

.rule-editor :deep(textarea) {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.preview-spin {
  display: block;
  width: 100%;
}

.generated-preview {
  min-height: 240px;
  padding: 16px;
  margin: 0;
  overflow: auto;
  color: var(--color-text-2);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  background: var(--color-fill-1);
  border-radius: 6px;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
