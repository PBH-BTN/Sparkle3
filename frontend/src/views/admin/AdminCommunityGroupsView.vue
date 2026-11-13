<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal } from '@arco-design/web-vue'
import {
  IconDelete,
  IconEdit,
  IconGithub,
  IconLink,
  IconPlus,
  IconRefresh,
  IconSave,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  CommunityGroupAdmin,
  CommunityGroupInput,
  CommunityGroupsSettings,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(true)
const savingSettings = ref(false)
const savingGroup = ref(false)
const validatingTeams = ref(false)
const data = ref<CommunityGroupsSettings | null>(null)
const editorVisible = ref(false)
const editingId = ref<number | null>(null)

const settingsForm = reactive({
  personalAccessToken: '',
  clearToken: false,
  apiBaseUrl: 'https://api.github.com',
})
const editor = reactive<CommunityGroupInput>({
  slug: '',
  type: 'LINK',
  titleZh: '',
  titleEn: '',
  descriptionZh: '',
  descriptionEn: '',
  url: '',
  githubOrganization: '',
  enabled: true,
  sortOrder: 0,
  teams: [],
})

const sortedGroups = computed(
  () => [...(data.value?.groups ?? [])].sort((left, right) => left.sortOrder - right.sortOrder),
)

async function load() {
  loading.value = true
  try {
    applySettings(await adminApi.communityGroups())
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function applySettings(settings: CommunityGroupsSettings) {
  data.value = settings
  settingsForm.apiBaseUrl = settings.apiBaseUrl
  settingsForm.personalAccessToken = ''
  settingsForm.clearToken = false
}

async function saveSettings() {
  savingSettings.value = true
  try {
    applySettings(
      await adminApi.updateCommunityGroupSettings({
        personalAccessToken: settingsForm.personalAccessToken.trim() || undefined,
        clearToken: settingsForm.clearToken,
        apiBaseUrl: settingsForm.apiBaseUrl.trim(),
      }),
    )
    Message.success(t('admin.communityGroups.settingsSaved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingSettings.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(editor, {
    slug: '',
    type: 'LINK',
    titleZh: '',
    titleEn: '',
    descriptionZh: '',
    descriptionEn: '',
    url: '',
    githubOrganization: '',
    enabled: true,
    sortOrder: sortedGroups.value.length * 10,
    teams: [],
  })
  editorVisible.value = true
}

function openEdit(group: CommunityGroupAdmin) {
  editingId.value = group.id
  Object.assign(editor, {
    slug: group.slug,
    type: group.type,
    titleZh: group.titleZh,
    titleEn: group.titleEn,
    descriptionZh: group.descriptionZh,
    descriptionEn: group.descriptionEn,
    url: group.url ?? '',
    githubOrganization: group.githubOrganization ?? '',
    enabled: group.enabled,
    sortOrder: group.sortOrder,
    teams: group.teams.map((team) => ({ ...team })),
  })
  editorVisible.value = true
}

function addTeam() {
  editor.teams.push({ slug: '', name: '' })
}

function removeTeam(index: number) {
  editor.teams.splice(index, 1)
}

async function validateTeams() {
  if (!editor.githubOrganization.trim() || editor.teams.length === 0) {
    Message.warning(t('admin.communityGroups.githubRequired'))
    return
  }
  const slugs = editor.teams.map((team) => team.slug.trim()).filter(Boolean)
  if (!slugs.length) {
    Message.warning(t('admin.communityGroups.githubRequired'))
    return
  }
  validatingTeams.value = true
  try {
    const result = await adminApi.validateCommunityGithubTeams(
      editor.githubOrganization.trim(),
      slugs,
    )
    editor.teams = result.teams.map((team) => ({
      slug: team.slug,
      name: team.name,
    }))
    Message.success(t('admin.communityGroups.teamsValidated', { count: result.teams.length }))
  } catch (error) {
    Message.error(errorMessage(error, t('admin.communityGroups.validationFailed')))
  } finally {
    validatingTeams.value = false
  }
}

async function saveGroup() {
  if (!editor.slug.trim() || (!editor.titleZh.trim() && !editor.titleEn.trim())) {
    Message.warning(t('admin.communityGroups.required'))
    return
  }
  if (editor.type === 'LINK' && !editor.url.trim()) {
    Message.warning(t('admin.communityGroups.urlRequired'))
    return
  }
  if (
    editor.type === 'GITHUB' &&
    (!editor.githubOrganization.trim() ||
      !editor.teams.some((team) => team.slug.trim()))
  ) {
    Message.warning(t('admin.communityGroups.githubRequired'))
    return
  }
  savingGroup.value = true
  try {
    const body: CommunityGroupInput = {
      ...editor,
      slug: editor.slug.trim(),
      titleZh: editor.titleZh.trim(),
      titleEn: editor.titleEn.trim(),
      descriptionZh: editor.descriptionZh.trim(),
      descriptionEn: editor.descriptionEn.trim(),
      url: editor.url.trim(),
      githubOrganization:
        editor.type === 'GITHUB' ? editor.githubOrganization.trim() : '',
      teams:
        editor.type === 'GITHUB'
          ? editor.teams
              .filter((team) => team.slug.trim())
              .map((team) => ({
                slug: team.slug.trim(),
                name: team.name.trim() || team.slug.trim(),
              }))
          : [],
    }
    if (editingId.value == null) {
      await adminApi.createCommunityGroup(body)
      Message.success(t('admin.common.created'))
    } else {
      await adminApi.updateCommunityGroup(editingId.value, body)
      Message.success(t('admin.common.updated'))
    }
    editorVisible.value = false
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingGroup.value = false
  }
}

function confirmDelete(group: CommunityGroupAdmin) {
  Modal.warning({
    title: t('admin.communityGroups.deleteTitle'),
    content: t('admin.communityGroups.deleteConfirm', { name: group.titleZh || group.titleEn }),
    hideCancel: false,
    onOk: async () => {
      try {
        await adminApi.deleteCommunityGroup(group.id)
        Message.success(t('admin.common.deleted'))
        await load()
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
      }
    },
  })
}

onMounted(load)
</script>

<template>
  <div class="page-stack community-admin-page">
    <PageHeader
      :title="t('admin.communityGroups.title')"
      :description="t('admin.communityGroups.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.communityGroups.create') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      {{ t('admin.communityGroups.githubTokenHint') }}
    </a-alert>

    <a-spin :loading="loading" class="community-spin">
      <a-card class="surface-card">
        <template #title>{{ t('admin.communityGroups.apiSettings') }}</template>
        <a-form :model="settingsForm" layout="vertical">
          <div class="settings-grid">
            <a-form-item :label="t('admin.communityGroups.apiBaseUrl')" required>
              <a-input v-model="settingsForm.apiBaseUrl" :max-length="2048" />
            </a-form-item>
            <a-form-item :label="t('admin.communityGroups.accessToken')">
              <a-input-password
                v-model="settingsForm.personalAccessToken"
                :placeholder="
                  data?.tokenConfigured
                    ? t('admin.communityGroups.tokenKeepPlaceholder')
                    : t('admin.communityGroups.tokenPlaceholder')
                "
                :max-length="4096"
              />
              <template #extra>
                <div class="token-row">
                  <a-tag :color="data?.tokenConfigured ? 'green' : 'gray'">
                    {{
                      data?.tokenConfigured
                        ? t('admin.communityGroups.tokenConfigured')
                        : t('admin.communityGroups.tokenMissing')
                    }}
                  </a-tag>
                  <a-checkbox v-if="data?.tokenConfigured" v-model="settingsForm.clearToken">
                    {{ t('admin.communityGroups.clearToken') }}
                  </a-checkbox>
                </div>
              </template>
            </a-form-item>
          </div>
          <a-button type="primary" :loading="savingSettings" @click="saveSettings">
            <template #icon><IconSave /></template>
            {{ t('admin.common.save') }}
          </a-button>
        </a-form>
      </a-card>

      <a-empty v-if="!sortedGroups.length" :description="t('admin.communityGroups.empty')" />
      <div v-else class="group-list">
        <article v-for="group in sortedGroups" :key="group.id" class="group-row">
          <span class="group-type-icon">
            <IconGithub v-if="group.type === 'GITHUB'" />
            <IconLink v-else />
          </span>
          <div class="group-copy">
            <div class="group-title">
              <strong>{{ group.titleZh || group.titleEn }}</strong>
              <a-tag :color="group.enabled ? 'green' : 'gray'" size="small">
                {{ group.enabled ? t('admin.common.enabled') : t('admin.common.disabled') }}
              </a-tag>
              <a-tag size="small">{{ t(`communityGroups.types.${group.type}`) }}</a-tag>
            </div>
            <span class="group-slug">{{ group.slug }} · #{{ group.sortOrder }}</span>
            <span v-if="group.type === 'GITHUB'" class="group-detail">
              {{ group.githubOrganization }} ·
              {{ group.teams.map((team) => team.name).join(', ') }}
            </span>
            <span v-else class="group-detail">{{ group.url }}</span>
            <span class="group-updated">
              {{ t('admin.communityGroups.updatedAt', { time: formatDate(group.updatedAt) }) }}
            </span>
          </div>
          <div class="group-actions">
            <a-button type="text" @click="openEdit(group)">
              <template #icon><IconEdit /></template>
              {{ t('admin.common.edit') }}
            </a-button>
            <a-button type="text" status="danger" @click="confirmDelete(group)">
              <template #icon><IconDelete /></template>
              {{ t('admin.common.delete') }}
            </a-button>
          </div>
        </article>
      </div>
    </a-spin>

    <a-modal
      v-model:visible="editorVisible"
      :title="
        editingId == null
          ? t('admin.communityGroups.create')
          : t('admin.communityGroups.edit')
      "
      :footer="false"
      width="min(860px, 94vw)"
    >
      <a-form :model="editor" layout="vertical">
        <div class="settings-grid">
          <a-form-item :label="t('admin.communityGroups.type')">
            <a-radio-group v-model="editor.type" type="button">
              <a-radio value="LINK">
                <template #icon><IconLink /></template>
                {{ t('communityGroups.types.LINK') }}
              </a-radio>
              <a-radio value="GITHUB">
                <template #icon><IconGithub /></template>
                {{ t('communityGroups.types.GITHUB') }}
              </a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item :label="t('admin.communityGroups.slug')" required>
            <a-input v-model="editor.slug" :max-length="128" />
          </a-form-item>
          <a-form-item :label="t('admin.communityGroups.titleZh')">
            <a-input v-model="editor.titleZh" :max-length="255" />
          </a-form-item>
          <a-form-item :label="t('admin.communityGroups.titleEn')">
            <a-input v-model="editor.titleEn" :max-length="255" />
          </a-form-item>
          <a-form-item :label="t('admin.communityGroups.descriptionZh')">
            <a-textarea v-model="editor.descriptionZh" :max-length="5000" show-word-limit />
          </a-form-item>
          <a-form-item :label="t('admin.communityGroups.descriptionEn')">
            <a-textarea v-model="editor.descriptionEn" :max-length="5000" show-word-limit />
          </a-form-item>
        </div>

        <a-form-item :label="t('admin.communityGroups.url')">
          <a-input
            v-model="editor.url"
            :max-length="2048"
            :placeholder="t('admin.communityGroups.urlPlaceholder')"
          />
        </a-form-item>

        <template v-if="editor.type === 'GITHUB'">
          <a-form-item :label="t('admin.communityGroups.organization')" required>
            <a-input v-model="editor.githubOrganization" :max-length="100" />
          </a-form-item>
          <a-form-item :label="t('admin.communityGroups.teams')" required>
            <div class="team-editor">
              <div
                v-for="(team, index) in editor.teams"
                :key="index"
                class="team-editor-row"
              >
                <a-input
                  v-model="team.slug"
                  :placeholder="t('admin.communityGroups.teamSlug')"
                />
                <a-input
                  v-model="team.name"
                  :placeholder="t('admin.communityGroups.teamName')"
                />
                <a-button status="danger" @click="removeTeam(index)">
                  <template #icon><IconDelete /></template>
                </a-button>
              </div>
              <a-space>
                <a-button @click="addTeam">
                  <template #icon><IconPlus /></template>
                  {{ t('admin.communityGroups.addTeam') }}
                </a-button>
                <a-button :loading="validatingTeams" @click="validateTeams">
                  <template #icon><IconUserGroup /></template>
                  {{ t('admin.communityGroups.validateTeams') }}
                </a-button>
              </a-space>
            </div>
          </a-form-item>
        </template>

        <div class="settings-grid compact">
          <a-form-item :label="t('admin.communityGroups.sortOrder')">
            <a-input-number v-model="editor.sortOrder" :min="-10000" :max="10000" />
          </a-form-item>
          <a-form-item :label="t('admin.common.status')">
            <a-switch v-model="editor.enabled" />
          </a-form-item>
        </div>
        <div class="modal-actions">
          <a-button @click="editorVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button type="primary" :loading="savingGroup" @click="saveGroup">
            {{ t('admin.common.save') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.community-admin-page {
  width: min(1120px, 100%);
}

.community-spin {
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

.settings-grid.compact {
  grid-template-columns: 180px 180px;
}

.token-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.group-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 9px;
}

.group-type-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: rgb(var(--primary-6));
  font-size: 21px;
  background: rgb(var(--primary-1));
  border-radius: 10px;
}

.group-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 5px;
}

.group-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.group-slug,
.group-detail,
.group-updated {
  color: var(--color-text-3);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.group-slug {
  font-family: "SFMono-Regular", Consolas, monospace;
}

.group-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.team-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.team-editor-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 8px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 720px) {
  .settings-grid,
  .settings-grid.compact,
  .team-editor-row {
    grid-template-columns: 1fr;
  }

  .group-row {
    align-items: stretch;
    flex-direction: column;
  }

  .group-actions {
    justify-content: flex-start;
  }
}
</style>
