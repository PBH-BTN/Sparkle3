<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconEdit,
  IconFile,
  IconRefresh,
  IconSafe,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  ConfigMigrationResult,
  DatabaseConfigEntry,
  DatabaseConfigSummary,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatNumber } = useFormatters()
const entries = ref<DatabaseConfigEntry[]>([])
const summary = ref<DatabaseConfigSummary | null>(null)
const loading = ref(true)
const migrating = ref(false)
const lastMigration = ref<ConfigMigrationResult | null>(null)
const filters = ref({
  keyword: '',
  group: '',
})

const editorVisible = ref(false)
const editorSaving = ref(false)
const editing = ref<DatabaseConfigEntry | null>(null)
const editedValue = ref('')

const columns = computed<TableColumnData[]>(() => [
  { title: t('admin.databaseConfig.key'), slotName: 'key', width: 300 },
  { title: t('admin.databaseConfig.value'), slotName: 'value', width: 280 },
  { title: t('admin.databaseConfig.mode'), slotName: 'mode', width: 120 },
  { title: t('admin.databaseConfig.status'), slotName: 'status', width: 150 },
  { title: t('admin.databaseConfig.updatedAt'), slotName: 'updatedAt', width: 160 },
  { title: t('admin.databaseConfig.actions'), slotName: 'actions', width: 90 },
])

const groups = computed(() => summary.value?.groups ?? [])

async function load() {
  loading.value = true
  try {
    const [entryResult, summaryResult] = await Promise.all([
      adminApi.databaseConfig(filters.value.keyword, filters.value.group),
      adminApi.databaseConfigSummary(),
    ])
    entries.value = entryResult
    summary.value = summaryResult
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function migrate() {
  migrating.value = true
  try {
    lastMigration.value = await adminApi.migrateDatabaseConfig()
    Message.success(
      t('admin.databaseConfig.migrated', {
        inserted: lastMigration.value.inserted,
        updated: lastMigration.value.updated,
        skipped: lastMigration.value.skipped,
      }),
    )
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    migrating.value = false
  }
}

function openEdit(entry: DatabaseConfigEntry) {
  if (entry.sensitive) return
  editing.value = entry
  editedValue.value = entry.value ?? ''
  editorVisible.value = true
}

async function save() {
  if (!editing.value) return
  editorSaving.value = true
  try {
    const updated = await adminApi.updateDatabaseConfig(editing.value.key, editedValue.value)
    editing.value = updated
    editorVisible.value = false
    Message.success(
      updated.hotReload
        ? t('admin.databaseConfig.hotSaved')
        : t('admin.databaseConfig.restartSaved'),
    )
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    editorSaving.value = false
  }
}

function sourceLabel(source: string) {
  const key = `admin.databaseConfig.sourceLabels.${source}`
  const label = t(key)
  return label === key ? source : label
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader
      :title="t('admin.databaseConfig.title')"
      :description="t('admin.databaseConfig.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.databaseConfig.refresh') }}
        </a-button>
        <a-button type="primary" :loading="migrating" @click="migrate">
          <template #icon><IconFile /></template>
          {{ migrating ? t('admin.databaseConfig.refreshing') : t('admin.databaseConfig.migrate') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon :title="t('admin.databaseConfig.envPriority')">
      {{ t('admin.databaseConfig.migrationHint') }}
      {{ t('admin.databaseConfig.restartHint') }}
    </a-alert>

    <a-spin :loading="loading" class="config-spin">
      <template v-if="summary">
        <div class="summary-grid">
          <div class="summary-card">
            <span>{{ t('admin.databaseConfig.total') }}</span>
            <strong>{{ formatNumber(summary.total) }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('admin.databaseConfig.yamlEntries') }}</span>
            <strong>{{ formatNumber(summary.yamlEntries) }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('admin.databaseConfig.databaseEntries') }}</span>
            <strong>{{ formatNumber(summary.databaseEntries) }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('admin.databaseConfig.sensitiveEntries') }}</span>
            <strong>{{ formatNumber(summary.sensitiveEntries) }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('admin.databaseConfig.hotReloadEntries') }}</span>
            <strong>{{ formatNumber(summary.hotReloadEntries) }}</strong>
          </div>
        </div>

        <a-card class="surface-card filter-card">
          <a-form :model="filters" layout="vertical" @submit.prevent="load">
            <a-row :gutter="16">
              <a-col :xs="24" :md="16">
                <a-form-item :label="t('admin.databaseConfig.search')">
                  <a-input v-model="filters.keyword" allow-clear @press-enter="load" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-item :label="t('admin.databaseConfig.group')">
                  <a-select v-model="filters.group" allow-clear @change="load">
                    <a-option value="">{{ t('admin.databaseConfig.allGroups') }}</a-option>
                    <a-option v-for="group in groups" :key="group" :value="group">
                      {{ group }}
                    </a-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-card>

        <a-card class="surface-card table-card">
          <a-table
            :columns="columns"
            :data="entries"
            :pagination="false"
            :scroll="{ x: 1100, y: 620 }"
            row-key="key"
            stripe
          >
            <template #empty><a-empty :description="t('admin.databaseConfig.noData')" /></template>
            <template #key="{ record }">
              <div class="config-key-cell">
                <code class="config-key">{{ record.key }}</code>
                <span>{{ record.group }} · {{ sourceLabel(record.source) }}</span>
              </div>
            </template>
            <template #value="{ record }">
              <span v-if="record.sensitive" class="muted">
                <IconSafe />
                {{ t('admin.databaseConfig.sensitiveHidden') }}
              </span>
              <code v-else class="config-value" :title="record.value || ''">
                {{ record.value === '' ? '""' : record.value }}
              </code>
            </template>
            <template #mode="{ record }">
              <a-tag :color="record.hotReload ? 'green' : 'orange'">
                {{
                  record.hotReload
                    ? t('admin.databaseConfig.hotReload')
                    : t('admin.databaseConfig.restartRequired')
                }}
              </a-tag>
            </template>
            <template #status="{ record }">
              <div class="status-tags">
                <a-tag v-if="record.sensitive" color="red">
                  {{ t('admin.databaseConfig.sensitiveEntries') }}
                </a-tag>
                <a-tooltip
                  v-if="record.environmentOverride"
                  :content="t('admin.databaseConfig.envPriority')"
                >
                  <a-tag color="purple">
                    {{ t('admin.databaseConfig.environmentOverride') }}
                  </a-tag>
                </a-tooltip>
                <span
                  v-if="!record.sensitive && !record.environmentOverride"
                  class="muted"
                >
                  —
                </span>
              </div>
            </template>
            <template #updatedAt="{ record }">
              <span class="nowrap">{{ formatDate(record.updatedAt) }}</span>
            </template>
            <template #actions="{ record }">
              <a-button
                v-if="!record.sensitive"
                type="text"
                size="small"
                @click="openEdit(record)"
              >
                <template #icon><IconEdit /></template>
                {{ t('admin.databaseConfig.edit') }}
              </a-button>
            </template>
          </a-table>
          <div v-if="summary.lastUpdatedAt" class="config-footer">
            {{
              t('admin.databaseConfig.lastUpdated', {
                time: formatDate(summary.lastUpdatedAt),
              })
            }}
          </div>
        </a-card>
      </template>
    </a-spin>

    <a-modal
      v-model:visible="editorVisible"
      :title="t('admin.databaseConfig.editTitle')"
      :footer="false"
      width="720px"
    >
      <template v-if="editing">
        <a-form :model="{ editedValue }" layout="vertical">
          <a-form-item :label="t('admin.databaseConfig.key')">
            <a-input :model-value="editing.key" readonly />
          </a-form-item>
          <a-form-item :label="t('admin.databaseConfig.value')">
            <a-select v-if="editing.valueType === 'boolean'" v-model="editedValue">
              <a-option value="true">true</a-option>
              <a-option value="false">false</a-option>
            </a-select>
            <a-textarea
              v-else
              v-model="editedValue"
              :auto-size="{ minRows: 4, maxRows: 16 }"
              class="value-editor"
            />
          </a-form-item>
          <a-alert :type="editing.hotReload ? 'info' : 'warning'" show-icon>
            {{
              editing.hotReload
                ? t('admin.databaseConfig.hotSaved')
                : t('admin.databaseConfig.restartSaved')
            }}
          </a-alert>
        </a-form>
        <div class="modal-actions">
          <a-button @click="editorVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button type="primary" :loading="editorSaving" @click="save">
            {{ t('admin.databaseConfig.save') }}
          </a-button>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.config-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  padding: 15px 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.summary-card span {
  color: var(--color-text-3);
  font-size: 11px;
}

.summary-card strong {
  margin-top: 7px;
  font-size: 22px;
}

.config-key,
.config-value {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-value {
  max-width: 260px;
}

.config-key-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.config-key-cell > span {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 10px;
}

.status-tags {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.config-footer {
  padding: 11px 20px;
  color: var(--color-text-3);
  font-size: 11px;
  border-top: 1px solid var(--color-border-2);
}

.value-editor :deep(textarea) {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
