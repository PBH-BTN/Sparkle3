<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconCheckCircle,
  IconClockCircle,
  IconExclamationCircle,
  IconLoading,
  IconPlayArrow,
  IconRefresh,
  IconSave,
  IconSchedule,
  IconThunderbolt,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  BackgroundTaskRun,
  BackgroundTaskStatus,
  BackgroundTaskStatusResponse,
  TaskGroup,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

type TaskTypeFilter = 'ALL' | 'SCHEDULED' | 'EVENT' | 'STARTUP'

const { t } = useI18n()
const { formatDate, formatDuration, formatNumber } = useFormatters()
const data = ref<BackgroundTaskStatusResponse | null>(null)
const loading = ref(true)
const refreshing = ref(false)
const typeFilter = ref<TaskTypeFilter>('ALL')
const now = ref(Date.now())
const cronDrafts = reactive<Record<string, string>>({})
const cronDirty = reactive<Record<string, boolean>>({})
const savingCronId = ref<string | null>(null)
const togglingModuleId = ref<string | null>(null)
const runningTaskId = ref<string | null>(null)
let pollTimer: ReturnType<typeof setInterval> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined
let pollInFlight = false

function taskCategory(task: BackgroundTaskStatus): Exclude<TaskTypeFilter, 'ALL'> {
  if (task.scheduleType === 'STARTUP') return 'STARTUP'
  if (task.scheduleType === 'EVENT') return 'EVENT'
  return 'SCHEDULED'
}

const taskFilters = computed(() => [
  {
    value: 'ALL' as const,
    label: t('admin.tasks.filters.all'),
    count: data.value?.tasks.length ?? 0,
  },
  ...(['SCHEDULED', 'EVENT', 'STARTUP'] as const).map((type) => ({
    value: type,
    label: t(`admin.tasks.filters.${type}`),
    count: data.value?.tasks.filter((task) => taskCategory(task) === type).length ?? 0,
  })),
])

const visibleTasks = computed(() => {
  const tasks = data.value?.tasks ?? []
  return typeFilter.value === 'ALL'
    ? tasks
    : tasks.filter((task) => taskCategory(task) === typeFilter.value)
})

const summary = computed(() => {
  const tasks = data.value?.tasks ?? []
  return [
    {
      key: 'total',
      label: t('admin.tasks.summary.total'),
      value: tasks.length,
      icon: IconSchedule,
      tone: 'blue',
    },
    {
      key: 'running',
      label: t('admin.tasks.summary.running'),
      value: tasks.filter((task) => task.runningCount > 0).length,
      icon: IconLoading,
      tone: 'cyan',
    },
    {
      key: 'scheduled',
      label: t('admin.tasks.summary.scheduled'),
      value: tasks.filter((task) => taskCategory(task) === 'SCHEDULED').length,
      icon: IconClockCircle,
      tone: 'purple',
    },
    {
      key: 'attention',
      label: t('admin.tasks.summary.attention'),
      value: tasks.filter(
        (task) =>
          task.lastResult === 'FAILED' ||
          Boolean(task.scheduleError) ||
          (!task.enabled && task.kind === 'SCHEDULED'),
      ).length,
      icon: IconExclamationCircle,
      tone: 'orange',
    },
  ]
})

function groupLabel(group: TaskGroup) {
  return t(`admin.tasks.groups.${group}`)
}

function taskTypeLabel(task: BackgroundTaskStatus) {
  return t(`admin.tasks.filters.${taskCategory(task)}`)
}

function taskTypeColor(task: BackgroundTaskStatus) {
  if (taskCategory(task) === 'STARTUP') return 'green'
  if (taskCategory(task) === 'EVENT') return 'arcoblue'
  return 'purple'
}

function resultLabel(result: BackgroundTaskRun['result'] | null) {
  if (!result) return t('admin.tasks.results.none')
  return t(`admin.tasks.results.${result}`)
}

function resultColor(result: BackgroundTaskRun['result'] | null) {
  if (result === 'SUCCESS') return 'green'
  if (result === 'FAILED') return 'red'
  if (result === 'SKIPPED') return 'orange'
  return 'gray'
}

function taskState(task: BackgroundTaskStatus) {
  if (task.runningCount > 0) {
    return {
      key: 'running',
      label: t('admin.tasks.states.running', { count: task.runningCount }),
      color: 'arcoblue',
    }
  }
  if (!task.enabled) {
    return { key: 'disabled', label: t('admin.tasks.states.disabled'), color: 'gray' }
  }
  if (task.scheduleError || task.lastResult === 'FAILED') {
    return { key: 'failed', label: t('admin.tasks.states.failed'), color: 'red' }
  }
  if (task.lastResult === 'SUCCESS') {
    return { key: 'success', label: t('admin.tasks.states.success'), color: 'green' }
  }
  if (task.lastResult === 'SKIPPED') {
    return { key: 'skipped', label: t('admin.tasks.states.skipped'), color: 'orange' }
  }
  return { key: 'idle', label: t('admin.tasks.states.idle'), color: 'gray' }
}

function currentDuration(task: BackgroundTaskStatus) {
  if (task.runningCount > 0 && task.lastStartedAt) {
    const startedAt = new Date(task.lastStartedAt).getTime()
    return Number.isNaN(startedAt) ? task.currentDurationMs : Math.max(0, now.value - startedAt)
  }
  return task.currentDurationMs
}

function triggerLabel(trigger: string | null) {
  if (!trigger) return t('common.notAvailable')
  return t(`admin.tasks.triggers.${trigger}`)
}

function canEditCron(task: BackgroundTaskStatus) {
  return task.moduleId != null && task.scheduleType === 'CRON'
}

function cronDraft(task: BackgroundTaskStatus) {
  if (cronDrafts[task.id] == null) {
    cronDrafts[task.id] = task.schedule.replace(/^CRON\s+/, '')
  }
  return cronDrafts[task.id]
}

function markCronDirty(task: BackgroundTaskStatus) {
  cronDirty[task.id] = true
}

async function saveCron(task: BackgroundTaskStatus) {
  if (!task.moduleId) return
  const cron = cronDraft(task).trim()
  if (!cron) {
    Message.warning(t('admin.tasks.cronRequired'))
    return
  }
  savingCronId.value = task.id
  try {
    await adminApi.updateAnalyseModule(task.moduleId, {
      values: { schedule: cron },
    })
    cronDirty[task.id] = false
    Message.success(t('admin.tasks.cronSaved'))
    await load(true)
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingCronId.value = null
  }
}

async function toggleModule(task: BackgroundTaskStatus, enabled: boolean) {
  if (!task.moduleId) return
  togglingModuleId.value = task.moduleId
  try {
    await adminApi.updateAnalyseModule(task.moduleId, {
      values: { enabled: String(enabled) },
    })
    Message.success(
      t(enabled ? 'admin.tasks.moduleEnabledSaved' : 'admin.tasks.moduleDisabledSaved'),
    )
    await load(true)
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    togglingModuleId.value = null
  }
}

async function runNow(task: BackgroundTaskStatus) {
  if (!task.manualExecutable) return
  runningTaskId.value = task.id
  try {
    await adminApi.runTask(task.id)
    Message.success(t('admin.tasks.runNowStarted'))
    window.setTimeout(() => load(true), 300)
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    runningTaskId.value = null
  }
}

async function load(silent = false) {
  if (pollInFlight) return
  pollInFlight = true
  if (!silent) loading.value = true
  else refreshing.value = true
  try {
    data.value = await adminApi.taskStatus()
    for (const task of data.value.tasks) {
      if (canEditCron(task) && !cronDirty[task.id]) {
        cronDrafts[task.id] = task.schedule.replace(/^CRON\s+/, '')
      }
    }
  } catch (error) {
    if (!silent) Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
    refreshing.value = false
    pollInFlight = false
  }
}

onMounted(() => {
  load()
  now.value = Date.now()
  pollTimer = setInterval(() => load(true), 5_000)
  clockTimer = setInterval(() => {
    now.value = Date.now()
  }, 1_000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<template>
  <div class="page-stack task-status-page">
    <PageHeader :title="t('admin.tasks.title')" :description="t('admin.tasks.description')">
      <template #actions>
        <span v-if="data" class="updated-at">
          {{ t('admin.tasks.updatedAt', { time: formatDate(data.generatedAt) }) }}
        </span>
        <a-button :loading="refreshing" @click="load()">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <div class="summary-grid">
      <a-card
        v-for="item in summary"
        :key="item.key"
        class="surface-card summary-card"
        :class="`tone-${item.tone}`"
      >
        <span class="summary-icon"><component :is="item.icon" /></span>
        <div>
          <span>{{ item.label }}</span>
          <strong>{{ formatNumber(item.value) }}</strong>
        </div>
      </a-card>
    </div>

    <a-card class="surface-card tasks-card">
      <template #title>
        <span class="card-title">
          <IconThunderbolt />
          {{ t('admin.tasks.timeline') }}
        </span>
      </template>
      <template #extra>
        <span class="poll-hint">{{ t('admin.tasks.pollHint') }}</span>
      </template>

      <div class="task-toolbar">
        <a-radio-group v-model="typeFilter" type="button" size="small">
          <a-radio v-for="filter in taskFilters" :key="filter.value" :value="filter.value">
            {{ filter.label }}
            <span class="filter-count">{{ filter.count }}</span>
          </a-radio>
        </a-radio-group>
      </div>

      <a-spin :loading="loading" class="tasks-spin">
        <div v-if="visibleTasks.length" class="task-list">
          <article v-for="task in visibleTasks" :key="task.id" class="task-item">
            <header class="task-header">
              <span class="task-icon" :class="{ running: task.runningCount > 0 }">
                <IconLoading v-if="task.runningCount > 0" class="spin" />
                <IconSchedule v-else />
              </span>
              <div class="task-identity">
                <div class="task-title-line">
                  <strong>{{ t(task.nameKey) }}</strong>
                  <a-tag size="small" color="gray">{{ groupLabel(task.group) }}</a-tag>
                  <a-tag size="small" :color="taskTypeColor(task)">
                    {{ taskTypeLabel(task) }}
                  </a-tag>
                  <a-tag v-if="!task.enabled" size="small" color="gray">
                    {{ t('admin.common.disabled') }}
                  </a-tag>
                </div>
                <p>{{ t(task.descriptionKey) }}</p>
                <code>{{ task.schedule }}</code>
                <div v-if="task.moduleId" class="module-editor">
                  <div class="module-toggle-row">
                    <span>{{ t('admin.tasks.moduleEnabled') }}</span>
                    <a-switch
                      :model-value="task.moduleEnabled ?? task.enabled"
                      :loading="togglingModuleId === task.moduleId"
                      :disabled="savingCronId === task.id"
                      @change="(value) => toggleModule(task, Boolean(value))"
                    />
                  </div>
                  <div v-if="canEditCron(task)" class="cron-editor">
                    <span>{{ t('admin.tasks.cronExpression') }}</span>
                    <div class="cron-editor-row">
                      <a-input
                        v-model="cronDrafts[task.id]"
                        size="small"
                        :placeholder="t('admin.tasks.cronPlaceholder')"
                        @input="markCronDirty(task)"
                      />
                      <a-button
                        size="small"
                        type="primary"
                        :loading="savingCronId === task.id"
                        :disabled="togglingModuleId === task.moduleId"
                        @click="saveCron(task)"
                      >
                        <template #icon><IconSave /></template>
                        {{ t('admin.common.save') }}
                      </a-button>
                    </div>
                  </div>
                </div>
              </div>
              <div class="task-actions">
                <a-tag :color="taskState(task).color">{{ taskState(task).label }}</a-tag>
                <a-popconfirm
                  v-if="task.manualExecutable"
                  :content="t('admin.tasks.runNowConfirm', { name: t(task.nameKey) })"
                  :ok-text="t('admin.tasks.runNow')"
                  :cancel-text="t('common.cancel')"
                  @ok="runNow(task)"
                >
                  <a-button
                    size="small"
                    type="outline"
                    :loading="runningTaskId === task.id"
                    :disabled="!task.enabled || task.runningCount > 0"
                  >
                    <template #icon><IconPlayArrow /></template>
                    {{ t('admin.tasks.runNow') }}
                  </a-button>
                </a-popconfirm>
              </div>
            </header>

            <div class="task-metrics">
              <div>
                <span>{{ t('admin.tasks.nextRun') }}</span>
                <strong>{{ formatDate(task.nextRunAt) }}</strong>
              </div>
              <div>
                <span>{{ t('admin.tasks.currentDuration') }}</span>
                <strong>
                  {{
                    task.runningCount > 0
                      ? formatDuration(currentDuration(task))
                      : t('common.notAvailable')
                  }}
                </strong>
              </div>
              <div>
                <span>{{ t('admin.tasks.lastStarted') }}</span>
                <strong>{{ formatDate(task.lastStartedAt) }}</strong>
              </div>
              <div>
                <span>{{ t('admin.tasks.lastDuration') }}</span>
                <strong>
                  {{
                    task.lastFinishedAt
                      ? formatDuration(task.lastDurationMs)
                      : t('common.notAvailable')
                  }}
                </strong>
              </div>
              <div>
                <span>{{ t('admin.tasks.lastTrigger') }}</span>
                <strong>{{ triggerLabel(task.lastTrigger) }}</strong>
              </div>
              <div>
                <span>{{ t('admin.tasks.lastResult') }}</span>
                <strong class="result-value">
                  <a-tag size="small" :color="resultColor(task.lastResult)">
                    {{ resultLabel(task.lastResult) }}
                  </a-tag>
                </strong>
              </div>
            </div>

            <a-alert v-if="task.scheduleError" type="error" show-icon class="task-alert">
              <template #title>{{ t('admin.tasks.scheduleError') }}</template>
              {{ task.scheduleError }}
            </a-alert>
            <a-alert
              v-else-if="task.lastError"
              type="error"
              show-icon
              class="task-alert"
            >
              <template #title>{{ t('admin.tasks.lastError') }}</template>
              {{ task.lastError }}
            </a-alert>

            <section class="run-history">
              <div class="run-history-title">
                <span>{{ t('admin.tasks.recentRuns') }}</span>
                <span>{{ t('admin.tasks.recentLimit') }}</span>
              </div>
              <div v-if="task.recentRuns.length" class="run-list">
                <div v-for="(run, index) in task.recentRuns.slice(0, 5)" :key="index" class="run-row">
                  <a-tag size="small" :color="resultColor(run.result)">
                    {{ resultLabel(run.result) }}
                  </a-tag>
                  <span class="run-time">{{ formatDate(run.startedAt) }}</span>
                  <span class="run-trigger">{{ triggerLabel(run.trigger) }}</span>
                  <span class="run-duration">{{ formatDuration(run.durationMs) }}</span>
                  <a-tooltip v-if="run.message" :content="run.message" position="top">
                    <span class="run-message">{{ run.message }}</span>
                  </a-tooltip>
                  <span v-else class="run-message muted">{{ t('admin.tasks.noMessage') }}</span>
                </div>
              </div>
              <div v-else class="empty-runs">
                <IconCheckCircle />
                {{ t('admin.tasks.noRuns') }}
              </div>
            </section>
          </article>
        </div>
        <a-empty v-else :description="t('admin.tasks.noTasks')" />
      </a-spin>
    </a-card>
  </div>
</template>

<style scoped>
.task-status-page {
  width: min(1320px, 100%);
}

.updated-at,
.poll-hint {
  color: var(--color-text-3);
  font-size: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card :deep(.arco-card-body) {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 17px 18px;
}

.summary-card div {
  display: flex;
  flex-direction: column;
}

.summary-card div span {
  color: var(--color-text-3);
  font-size: 12px;
}

.summary-card div strong {
  margin-top: 2px;
  color: var(--color-text-1);
  font-size: 25px;
  line-height: 1.2;
}

.summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  font-size: 18px;
  border-radius: var(--app-radius);
}

.tone-blue .summary-icon {
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
}

.tone-cyan .summary-icon {
  color: rgb(var(--cyan-6));
  background: rgb(var(--cyan-6) / 12%);
}

.tone-purple .summary-icon {
  color: rgb(var(--purple-6));
  background: rgb(var(--purple-6) / 12%);
}

.tone-orange .summary-icon {
  color: rgb(var(--orange-6));
  background: var(--color-warning-light-1);
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.task-toolbar {
  margin-bottom: 16px;
  overflow-x: auto;
}

.task-toolbar :deep(.arco-radio-group) {
  flex-wrap: nowrap;
  white-space: nowrap;
}

.filter-count {
  margin-left: 5px;
  opacity: 0.62;
}

.tasks-spin {
  display: block;
  width: 100%;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.task-item {
  overflow: hidden;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
}

.task-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 15px 16px 13px;
}

.task-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--color-text-3);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
}

.task-icon.running {
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
  border-color: transparent;
}

.spin {
  animation: spin 1.2s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.task-identity {
  flex: 1;
  min-width: 0;
}

.task-title-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
}

.task-title-line strong {
  color: var(--color-text-1);
  font-size: 15px;
}

.task-identity p {
  margin: 5px 0 7px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.55;
}

.task-identity code {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-actions {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.module-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 620px;
  margin-top: 10px;
}

.module-toggle-row {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text-3);
  font-size: 11px;
}

.cron-editor {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.cron-editor > span {
  color: var(--color-text-3);
  font-size: 10px;
}

.cron-editor-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cron-editor-row :deep(.arco-input-wrapper) {
  font-family: "SFMono-Regular", Consolas, monospace;
}

.task-metrics {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  border-top: 1px solid var(--color-border-2);
  border-bottom: 1px solid var(--color-border-2);
}

.task-metrics > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 11px 13px;
  border-right: 1px solid var(--color-border-2);
}

.task-metrics > div:last-child {
  border-right: 0;
}

.task-metrics span {
  color: var(--color-text-3);
  font-size: 10px;
}

.task-metrics strong {
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-value {
  display: flex;
  align-items: center;
}

.task-alert {
  margin: 12px 16px 0;
}

.run-history {
  padding: 12px 16px 14px;
}

.run-history-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;
  color: var(--color-text-3);
  font-size: 10px;
}

.run-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.run-row {
  display: grid;
  grid-template-columns: 78px 165px 100px 120px minmax(120px, 1fr);
  gap: 9px;
  align-items: center;
  min-height: 30px;
  padding: 3px 7px;
  color: var(--color-text-3);
  font-size: 11px;
  border-radius: var(--app-radius);
}

.run-row:nth-child(odd) {
  background: var(--color-bg-2);
}

.run-message {
  display: block;
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-runs {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 7px;
  color: var(--color-text-3);
  font-size: 11px;
}

.empty-runs svg {
  color: rgb(var(--green-6));
}

@media (max-width: 1180px) {
  .task-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .task-metrics > div:nth-child(3n) {
    border-right: 0;
  }

  .task-metrics > div:nth-child(-n + 3) {
    border-bottom: 1px solid var(--color-border-2);
  }
}

@media (max-width: 820px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .run-row {
    grid-template-columns: 72px minmax(140px, 1fr) 80px;
  }

  .run-trigger,
  .run-message {
    display: none;
  }
}

@media (max-width: 620px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .task-header {
    flex-wrap: wrap;
  }

  .task-header > :last-child {
    margin-left: 46px;
  }

  .task-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .task-metrics > div:nth-child(3n) {
    border-right: 1px solid var(--color-border-2);
  }

  .task-metrics > div:nth-child(2n) {
    border-right: 0;
  }

  .run-row {
    grid-template-columns: 70px minmax(120px, 1fr);
  }

  .run-duration {
    display: none;
  }
}
</style>
