<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal } from '@arco-design/web-vue'
import {
  IconDelete,
  IconEdit,
  IconPlus,
  IconRefresh,
  IconSave,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  GithubSyncSettings,
  GithubSyncTarget,
  GithubSyncTargetInput,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()

const loading = ref(true)
const savingSettings = ref(false)
const runningAll = ref(false)
const runningTargetId = ref<number | null>(null)
const settingsData = ref<GithubSyncSettings | null>(null)
const loadError = ref('')
const settingsForm = reactive({
  schedule: '',
  personalAccessToken: '',
  clearToken: false,
})

const editorVisible = ref(false)
const editorSaving = ref(false)
const editingId = ref<number | null>(null)
const editor = reactive<GithubSyncTargetInput>({
  name: '',
  repositoryOwner: '',
  repositoryName: '',
  branchName: 'main',
  filePath: '',
  enabled: true,
  sourceIds: [],
})

const availableSources = computed(
  () => settingsData.value?.sources.filter((source) => source.available) ?? [],
)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    applySettings(await adminApi.githubSync())
  } catch (error) {
    loadError.value = errorMessage(error, t('common.loadFailed'))
    Message.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function applySettings(settings: GithubSyncSettings) {
  settingsData.value = settings
  settingsForm.schedule = settings.schedule
  settingsForm.personalAccessToken = ''
  settingsForm.clearToken = false
}

async function saveSettings() {
  if (!settingsForm.schedule.trim()) {
    Message.warning(t('admin.githubSync.scheduleRequired'))
    return
  }
  savingSettings.value = true
  try {
    const updated = await adminApi.updateGithubSyncSettings({
      schedule: settingsForm.schedule.trim(),
      personalAccessToken: settingsForm.personalAccessToken.trim() || undefined,
      clearToken: settingsForm.clearToken,
    })
    applySettings(updated)
    Message.success(t('admin.githubSync.settingsSaved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingSettings.value = false
  }
}

function openCreate() {
  const firstSource = availableSources.value[0]
  editingId.value = null
  Object.assign(editor, {
    name: '',
    repositoryOwner: '',
    repositoryName: '',
    branchName: 'main',
    filePath: firstSource?.defaultFileName ?? '',
    enabled: true,
    sourceIds: firstSource ? [firstSource.id] : [],
  })
  editorVisible.value = true
}

function openEdit(target: GithubSyncTarget) {
  editingId.value = target.id
  Object.assign(editor, {
    name: target.name,
    repositoryOwner: target.repositoryOwner,
    repositoryName: target.repositoryName,
    branchName: target.branchName,
    filePath: target.filePath,
    enabled: target.enabled,
    sourceIds: [...target.sourceIds],
  })
  editorVisible.value = true
}

function onSourcesChanged(value: unknown) {
  const sourceIds = Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : []
  if (!editor.filePath.trim()) {
    const firstSource = settingsData.value?.sources.find(
      (source) => source.id === sourceIds[0],
    )
    if (firstSource) editor.filePath = firstSource.defaultFileName
  }
}

async function saveTarget() {
  if (
    !editor.name.trim() ||
    !editor.repositoryOwner.trim() ||
    !editor.repositoryName.trim() ||
    !editor.branchName.trim() ||
    !editor.filePath.trim() ||
    editor.sourceIds.length === 0
  ) {
    Message.warning(t('admin.githubSync.requiredFields'))
    return
  }
  editorSaving.value = true
  try {
    const body: GithubSyncTargetInput = {
      ...editor,
      name: editor.name.trim(),
      repositoryOwner: editor.repositoryOwner.trim(),
      repositoryName: editor.repositoryName.trim(),
      branchName: editor.branchName.trim(),
      filePath: editor.filePath.trim(),
      sourceIds: [...editor.sourceIds],
    }
    if (editingId.value == null) {
      await adminApi.createGithubSyncTarget(body)
      Message.success(t('admin.githubSync.created'))
    } else {
      await adminApi.updateGithubSyncTarget(editingId.value, body)
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

function confirmDelete(target: GithubSyncTarget) {
  Modal.warning({
    title: t('admin.githubSync.deleteTitle'),
    content: t('admin.githubSync.deleteConfirm', { name: target.name }),
    hideCancel: false,
    onOk: async () => {
      try {
        await adminApi.deleteGithubSyncTarget(target.id)
        Message.success(t('admin.common.deleted'))
        await load()
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
      }
    },
  })
}

async function runAll() {
  runningAll.value = true
  try {
    const result = await adminApi.runGithubSync()
    if (result.failed > 0) {
      Message.warning(
        t('admin.githubSync.runSummaryPartial', {
          succeeded: result.succeeded,
          failed: result.failed,
        }),
      )
    } else {
      Message.success(
        t('admin.githubSync.runSummary', {
          succeeded: result.succeeded,
        }),
      )
    }
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    runningAll.value = false
  }
}

async function runTarget(target: GithubSyncTarget) {
  runningTargetId.value = target.id
  try {
    const result = await adminApi.runGithubSyncTarget(target.id)
    if (result.success) {
      Message.success(result.message)
    } else {
      Message.error(result.message)
    }
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    runningTargetId.value = null
  }
}

function sourceLabel(sourceId: string) {
  return t(`admin.githubSync.sources.${sourceId}`)
}

onMounted(load)
</script>

<template>
  <div class="page-stack github-sync-page">
    <PageHeader
      :title="t('admin.githubSync.title')"
      :description="t('admin.githubSync.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
        <a-button
          type="primary"
          :loading="runningAll"
          :disabled="loading || Boolean(loadError) || !settingsData"
          @click="runAll"
        >
          <template #icon><IconRefresh /></template>
          {{ t('admin.githubSync.runAll') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      {{ t('admin.githubSync.alert') }}
    </a-alert>

    <a-alert v-if="loadError" type="error" show-icon>
      {{ loadError }}
    </a-alert>

    <a-spin :loading="loading" class="github-sync-spin">
      <a-card class="surface-card">
        <template #title>{{ t('admin.githubSync.settingsTitle') }}</template>
        <a-form :model="settingsForm" layout="vertical">
          <div class="settings-grid">
            <a-form-item :label="t('admin.githubSync.schedule')" required>
              <a-input v-model="settingsForm.schedule" :max-length="128" />
              <template #extra>{{ t('admin.githubSync.scheduleHint') }}</template>
            </a-form-item>
            <a-form-item :label="t('admin.githubSync.token')">
              <a-input-password
                v-model="settingsForm.personalAccessToken"
                :placeholder="
                  settingsData?.tokenConfigured
                    ? t('admin.githubSync.tokenKeepPlaceholder')
                    : t('admin.githubSync.tokenPlaceholder')
                "
                :max-length="4096"
              />
              <template #extra>
                <div class="token-meta">
                  <a-tag :color="settingsData?.tokenConfigured ? 'green' : 'gray'">
                    {{
                      settingsData?.tokenConfigured
                        ? t('admin.githubSync.tokenConfigured')
                        : t('admin.githubSync.tokenMissing')
                    }}
                  </a-tag>
                  <a-checkbox
                    v-if="settingsData?.tokenConfigured"
                    v-model="settingsForm.clearToken"
                  >
                    {{ t('admin.githubSync.clearToken') }}
                  </a-checkbox>
                </div>
              </template>
            </a-form-item>
          </div>
          <a-button
            type="primary"
            :loading="savingSettings"
            :disabled="Boolean(loadError) || !settingsData"
            @click="saveSettings"
          >
            <template #icon><IconSave /></template>
            {{ t('admin.common.save') }}
          </a-button>
        </a-form>
      </a-card>

      <a-card class="surface-card">
        <template #title>{{ t('admin.githubSync.targetsTitle') }}</template>
        <template #extra>
          <a-button
            type="outline"
            :disabled="Boolean(loadError) || !settingsData"
            @click="openCreate"
          >
            <template #icon><IconPlus /></template>
            {{ t('admin.githubSync.createTarget') }}
          </a-button>
        </template>

        <a-empty
          v-if="settingsData?.targets.length === 0"
          :description="t('admin.githubSync.noTargets')"
        />
        <div v-else class="target-list">
          <article
            v-for="target in settingsData?.targets"
            :key="target.id"
            class="target-card"
          >
            <div class="target-heading">
              <div>
                <div class="target-title">
                  <strong>{{ target.name }}</strong>
                  <a-tag :color="target.enabled ? 'green' : 'gray'" size="small">
                    {{
                      target.enabled
                        ? t('admin.common.enabled')
                        : t('admin.common.disabled')
                    }}
                  </a-tag>
                </div>
                <code>{{ target.repositoryOwner }}/{{ target.repositoryName }}@{{ target.branchName }}</code>
              </div>
              <div class="target-actions">
                <a-button
                  type="text"
                  size="small"
                  :loading="runningTargetId === target.id"
                  @click="runTarget(target)"
                >
                  <template #icon><IconRefresh /></template>
                  {{ t('admin.githubSync.run') }}
                </a-button>
                <a-button type="text" size="small" @click="openEdit(target)">
                  <template #icon><IconEdit /></template>
                  {{ t('admin.common.edit') }}
                </a-button>
                <a-button type="text" size="small" @click="confirmDelete(target)">
                  <template #icon><IconDelete /></template>
                  {{ t('admin.common.delete') }}
                </a-button>
              </div>
            </div>

            <div class="target-path">
              <span>{{ t('admin.githubSync.filePath') }}</span>
              <code>{{ target.filePath }}</code>
            </div>

            <div class="source-tags">
              <a-tag
                v-for="sourceId in target.sourceIds"
                :key="sourceId"
                color="arcoblue"
                size="small"
              >
                {{ sourceLabel(sourceId) }}
              </a-tag>
            </div>

            <div class="target-status-grid">
              <span>
                {{ t('admin.githubSync.lastSuccess') }}
                <strong>{{ formatDate(target.lastSuccessAt) }}</strong>
              </span>
              <span>
                {{ t('admin.githubSync.lastAttempt') }}
                <strong>{{ formatDate(target.lastAttemptAt) }}</strong>
              </span>
              <span>
                {{ t('admin.githubSync.commit') }}
                <code>{{ target.lastCommitSha?.slice(0, 12) || 'N/A' }}</code>
              </span>
            </div>
            <a-alert v-if="target.lastError" type="error" class="target-error">
              {{ target.lastError }}
            </a-alert>
          </article>
        </div>
      </a-card>
    </a-spin>

    <a-modal
      v-model:visible="editorVisible"
      :title="
        editingId == null
          ? t('admin.githubSync.createTarget')
          : t('admin.githubSync.editTarget')
      "
      :footer="false"
      width="min(760px, 94vw)"
    >
      <a-form :model="editor" layout="vertical">
        <a-form-item :label="t('admin.githubSync.targetName')" required>
          <a-input v-model="editor.name" :max-length="128" />
        </a-form-item>
        <div class="settings-grid">
          <a-form-item :label="t('admin.githubSync.repositoryOwner')" required>
            <a-input v-model="editor.repositoryOwner" :max-length="100" />
          </a-form-item>
          <a-form-item :label="t('admin.githubSync.repositoryName')" required>
            <a-input v-model="editor.repositoryName" :max-length="100" />
          </a-form-item>
          <a-form-item :label="t('admin.githubSync.branchName')" required>
            <a-input v-model="editor.branchName" :max-length="255" />
          </a-form-item>
          <a-form-item :label="t('admin.githubSync.filePath')" required>
            <a-input v-model="editor.filePath" :max-length="1024" />
          </a-form-item>
        </div>
        <a-form-item :label="t('admin.githubSync.ruleSources')" required>
          <a-select
            v-model="editor.sourceIds"
            multiple
            allow-search
            @change="onSourcesChanged"
          >
            <a-option
              v-for="source in availableSources"
              :key="source.id"
              :value="source.id"
            >
              {{ sourceLabel(source.id) }} · {{ source.kind }}
            </a-option>
          </a-select>
          <template #extra>{{ t('admin.githubSync.ruleSourcesHint') }}</template>
        </a-form-item>
        <a-form-item :label="t('admin.common.status')">
          <a-switch v-model="editor.enabled" />
        </a-form-item>
        <div class="editor-actions">
          <a-button @click="editorVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button type="primary" :loading="editorSaving" @click="saveTarget">
            {{ t('admin.common.save') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.github-sync-page {
  width: min(1120px, 100%);
}

.github-sync-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.token-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.target-list {
  display: grid;
  gap: 12px;
}

.target-card {
  padding: 16px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.target-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.target-heading > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.target-heading code,
.target-path code,
.target-status-grid code {
  overflow-wrap: anywhere;
  font-size: 12px;
}

.target-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.target-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.target-path {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 12px;
  color: var(--color-text-3);
  font-size: 12px;
}

.source-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.target-status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.target-status-grid span {
  display: flex;
  flex-direction: column;
  gap: 3px;
  color: var(--color-text-3);
  font-size: 11px;
}

.target-status-grid strong {
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 500;
}

.target-error {
  margin-top: 12px;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

@media (max-width: 760px) {
  .settings-grid,
  .target-status-grid {
    grid-template-columns: 1fr;
  }

  .target-heading {
    flex-direction: column;
  }

  .target-actions {
    justify-content: flex-start;
  }
}
</style>
