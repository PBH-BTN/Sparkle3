<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconCode,
  IconInfoCircle,
  IconRefresh,
  IconSave,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type { AnalyseModule, AnalyseModuleField } from '@/api/adminTypes'
import { errorMessage } from '@/utils/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const modules = ref<AnalyseModule[]>([])
const drafts = reactive<Record<string, Record<string, string>>>({})
const loading = ref(true)
const savingId = ref<string | null>(null)
const pointsModuleIds = new Set([
  'points-settings',
  'points-fixed-rules',
  'points-dynamic-rules',
  'points-dynamic',
])
const taskManagedModuleIds = new Set([
  'swarm-peer-distribution',
  'ban-peer-distribution',
])
const isPointsScope = computed(() => route.name === 'admin-points-modules')
const visibleModules = computed(() => modules.value.filter((module) => (!route.query.module || route.query.module === module.id) && (
  isPointsScope.value
    ? pointsModuleIds.has(module.id)
    : !pointsModuleIds.has(module.id) && !taskManagedModuleIds.has(module.id)),
))

function moduleLabel(id: string, suffix: 'name' | 'description') {
  return t(`admin.modules.items.${id}.${suffix}`)
}

function fieldLabel(field: AnalyseModuleField) {
  return t(`admin.modules.fields.${field.field}`)
}

function fieldUnit(field: AnalyseModuleField) {
  if (field.type === 'duration-hours') return t('admin.modules.units.hours')
  if (field.type === 'bytes-gib') return t('admin.modules.units.gib')
  if (field.type === 'bytes-gb') return t('admin.modules.units.gb')
  return ''
}

function isBoolean(field: AnalyseModuleField) {
  return field.type === 'boolean'
}

function isNumeric(field: AnalyseModuleField) {
  return ['integer', 'long', 'decimal', 'duration-hours', 'bytes-gib', 'bytes-gb'].includes(
    field.type,
  )
}

function numericMin(field: AnalyseModuleField) {
  if (field.field === 'geoip-region-threshold') return 2
  if (field.field === 'geoip-scan-limit') return 500
  return 0
}

function numericMax(field: AnalyseModuleField) {
  if (field.field === 'geoip-alert-cooldown-minutes') return 10080
  if (field.field === 'geoip-scan-limit') return 50000
  return undefined
}

function numericValue(moduleId: string, field: AnalyseModuleField) {
  const value = Number(drafts[moduleId][field.field])
  return Number.isFinite(value) ? value : undefined
}

function setFieldValue(
  moduleId: string,
  field: AnalyseModuleField,
  value: string | number | undefined,
) {
  drafts[moduleId][field.field] = value == null ? '' : String(value)
}

function toggleField(moduleId: string, field: AnalyseModuleField, value: string | number | boolean) {
  drafts[moduleId][field.field] = String(Boolean(value))
}

async function load() {
  loading.value = true
  try {
    modules.value = await adminApi.analyseModules()
    for (const module of modules.value) {
      drafts[module.id] = Object.fromEntries(
        module.fields.map((field) => [field.field, field.value]),
      )
    }
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function businessFields(module: AnalyseModule) {
  const scheduled = module.fields.some((field) => field.type === 'cron')
  return module.fields.filter((field) => field.type !== 'cron' && (!scheduled || field.field !== 'enabled'))
}

async function save(module: AnalyseModule) {
  savingId.value = module.id
  try {
    const updated = await adminApi.updateAnalyseModule(module.id, {
      values: Object.fromEntries(businessFields(module).map((field) => [field.field, drafts[module.id][field.field]])),
    })
    const index = modules.value.findIndex((item) => item.id === module.id)
    if (index >= 0) modules.value[index] = updated
    drafts[module.id] = Object.fromEntries(
      updated.fields.map((field) => [field.field, field.value]),
    )
    Message.success(t('admin.modules.saved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack modules-page">
    <PageHeader
      :title="t(isPointsScope ? 'admin.pointsModules.title' : 'admin.modules.title')"
      :description="t(
        isPointsScope ? 'admin.pointsModules.description' : 'admin.modules.description',
      )"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      <template #title>
        {{ t('admin.modules.hotReloadTitle') }}
      </template>
      {{ t('admin.modules.hotReloadText') }}
    </a-alert>

    <a-alert v-if="route.query.module" type="info">{{ t('admin.organization.moduleSettings') }}<template #action><router-link :to="route.path">{{ t('admin.tasks.filters.all') }}</router-link></template></a-alert>
    <a-spin :loading="loading" class="modules-spin">
      <div class="module-grid">
        <a-card
          v-for="module in visibleModules"
          :key="module.id"
          class="surface-card module-card"
        >
          <template #title>
            <span class="module-title">
              <span class="module-icon"><IconCode /></span>
              <span>
                <strong>{{ moduleLabel(module.id, 'name') }}</strong>
                <small>{{ module.id }}</small>
              </span>
            </span>
          </template>
          <template #extra>
            <a-tag :color="module.enabled ? 'green' : 'gray'">
              {{ module.enabled ? t('admin.common.enabled') : t('admin.common.disabled') }}
            </a-tag>
          </template>

          <p class="module-description">{{ moduleLabel(module.id, 'description') }}</p>

          <a-form :model="drafts[module.id]" layout="vertical" @submit="save(module)">
            <div v-if="businessFields(module).length" class="module-fields">
              <a-form-item
                v-for="field in businessFields(module)"
                :key="field.field"
                :class="{ 'module-field-wide': ['cron', 'text', 'uid-list'].includes(field.type) }"
                :label="fieldLabel(field)"
              >
                <a-switch
                  v-if="isBoolean(field)"
                  :model-value="drafts[module.id][field.field] === 'true'"
                  @change="(value) => toggleField(module.id, field, value)"
                />
                <a-input-number
                  v-else-if="isNumeric(field)"
                  :model-value="numericValue(module.id, field)"
                  :precision="field.type === 'integer' || field.type === 'long' ? 0 : 2"
                  :min="numericMin(field)"
                  :max="numericMax(field)"
                  @change="(value) => setFieldValue(module.id, field, value)"
                >
                  <template v-if="fieldUnit(field)" #suffix>
                    {{ fieldUnit(field) }}
                  </template>
                </a-input-number>
                <a-input
                  v-else
                  v-model="drafts[module.id][field.field]"
                  :class="{ 'cron-input': field.type === 'cron' }"
                />
                <template v-if="field.restartRequired" #extra>
                  <span class="restart-hint">{{ t('admin.modules.restartRequired') }}</span>
                </template>
                <template v-else-if="field.type === 'cron'" #extra>
                  {{ t('admin.modules.cronDisabledHint') }}
                </template>
                <template v-else-if="field.type === 'text'" #extra>
                  {{ t('admin.modules.includeModulesHint') }}
                </template>
                <template v-else-if="field.type === 'uid-list'" #extra>
                  {{ t('admin.modules.recipientUidsHint') }}
                </template>
                <template v-else-if="field.field === 'geoip-alert-cooldown-minutes'" #extra>
                  {{ t('admin.modules.geoipAlertCooldownHint') }}
                </template>
              </a-form-item>
            </div>

            <div class="module-footer">
              <a-tooltip :content="module.id">
                <span class="module-id">
                  <IconInfoCircle />
                  {{ t('admin.modules.configMoved') }}
                </span>
              </a-tooltip>
              <a-space>
                <a-button
                  type="outline"
                  v-if="module.fields.some((field) => field.type === 'cron')"
                  :disabled="savingId === module.id"
                  @click="router.push({ path: '/admin/tasks', query: { module: module.id } })"
                >
                  <template #icon><IconRefresh /></template>
                  {{ t('admin.organization.taskSettings') }}
                </a-button>
                <a-button
                  type="primary"
                  v-if="businessFields(module).length"
                  :loading="savingId === module.id"
                  :disabled="loading"
                  @click="save(module)"
                >
                  <template #icon><IconSave /></template>
                  {{ t('admin.modules.saveModule') }}
                </a-button>
              </a-space>
            </div>
          </a-form>
        </a-card>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.modules-page {
  width: 100%;
}

.modules-spin {
  display: block;
  width: 100%;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.module-card {
  min-width: 0;
  height: 100%;
}

.module-card :deep(.arco-card-header-title) { min-width: 0; white-space: normal; }
.module-title, .module-title > span:last-child { min-width: 0; }
.module-title strong { overflow-wrap: anywhere; }
.module-icon { flex-shrink: 0; }
.module-footer :deep(.arco-space) { flex-wrap: wrap; }

.module-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.module-title > span:last-child {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.module-title strong {
  font-size: 15px;
}

.module-title small {
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  font-weight: 400;
}

.module-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
  border-radius: var(--app-radius);
}

.module-description {
  min-height: 42px;
  margin: 0 0 16px;
  color: var(--color-text-3);
  line-height: 1.6;
}

.module-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.module-field-wide {
  grid-column: 1 / -1;
}

.module-fields :deep(.arco-form-item) {
  margin-bottom: 14px;
}

.module-fields :deep(.arco-form-item-extra) {
  font-size: 11px;
}

.cron-input :deep(input) {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}

.restart-hint {
  color: rgb(var(--orange-6));
}

.module-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-2);
}

.module-id {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--color-text-3);
  font-size: 11px;
}

@media (max-width: 980px) {
  .module-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .module-fields {
    grid-template-columns: 1fr;
  }

  .module-field-wide {
    grid-column: auto;
  }

  .module-footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
