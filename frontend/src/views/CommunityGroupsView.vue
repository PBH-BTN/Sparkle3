<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal } from '@arco-design/web-vue'
import {
  IconClockCircle,
  IconGithub,
  IconLink,
  IconRefresh,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type {
  CommunityGroup,
  GithubCommunityStatus,
  GithubMembershipStatus,
} from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const { t, locale } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(true)
const joiningId = ref<number | null>(null)
const groups = ref<CommunityGroup[]>([])
const statuses = ref<Record<number, GithubCommunityStatus>>({})

function title(group: CommunityGroup) {
  return locale.value.startsWith('en')
    ? group.titleEn || group.titleZh
    : group.titleZh || group.titleEn
}

function description(group: CommunityGroup) {
  return locale.value.startsWith('en')
    ? group.descriptionEn || group.descriptionZh
    : group.descriptionZh || group.descriptionEn
}

async function load() {
  loading.value = true
  try {
    groups.value = await uiApi.communityGroups()
    const githubGroups = groups.value.filter((group) => group.type === 'GITHUB')
    const results = await Promise.allSettled(
      githubGroups.map(async (group) => {
        const status = await uiApi.communityGroupGithubStatus(group.id)
        statuses.value[group.id] = status
      }),
    )
    const failed = results.find((result) => result.status === 'rejected')
    if (failed?.status === 'rejected') {
      Message.warning(errorMessage(failed.reason, t('communityGroups.statusFailed')))
    }
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function statusColor(status: GithubMembershipStatus) {
  if (status === 'ACTIVE') return 'green'
  if (status === 'PENDING') return 'orange'
  if (status === 'PARTIAL' || status === 'NOT_MEMBER') return 'arcoblue'
  if (status === 'ERROR' || status === 'UNAVAILABLE') return 'red'
  return 'gray'
}

function teamStateColor(state: string) {
  if (state === 'ACTIVE') return 'green'
  if (state === 'PENDING') return 'orange'
  return 'gray'
}

function canJoin(group: CommunityGroup) {
  const status = statuses.value[group.id]
  return Boolean(
    group.githubLinked &&
      group.githubConfigured &&
      status &&
      !['ACTIVE', 'PENDING', 'UNAVAILABLE'].includes(status.status),
  )
}

function requestJoin(group: CommunityGroup) {
  Modal.confirm({
    title: t('communityGroups.joinConfirmTitle', { name: title(group) }),
    content: t('communityGroups.joinConfirm', {
      organization: group.githubOrganization,
      account: group.githubLogin,
    }),
    okText: t('communityGroups.join'),
    cancelText: t('common.cancel'),
    onOk: () => join(group),
  })
}

async function join(group: CommunityGroup) {
  joiningId.value = group.id
  try {
    const result = await uiApi.joinCommunityGithubGroup(group.id)
    Message.success(result.message || t('communityGroups.invited'))
    statuses.value[group.id] = await uiApi.communityGroupGithubStatus(group.id)
  } catch (error) {
    Message.error(errorMessage(error, t('communityGroups.joinFailed')))
  } finally {
    joiningId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="community-groups-page">
    <PageHeader
      :title="t('communityGroups.title')"
      :description="t('communityGroups.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('common.retry') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon class="groups-hint">
      {{ t('communityGroups.hint') }}
    </a-alert>

    <a-spin :loading="loading" class="groups-spin">
      <div v-if="groups.length" class="group-grid">
        <article v-for="group in groups" :key="group.id" class="group-card">
          <div class="group-heading">
            <span
              class="group-icon"
              :class="{ 'group-icon--github': group.type === 'GITHUB' }"
            >
              <IconGithub v-if="group.type === 'GITHUB'" />
              <IconUserGroup v-else />
            </span>
            <div class="group-title">
              <h2>{{ title(group) }}</h2>
              <a-tag size="small" :color="group.type === 'GITHUB' ? 'purple' : 'arcoblue'">
                {{ t(`communityGroups.types.${group.type}`) }}
              </a-tag>
            </div>
          </div>

          <p class="group-description">
            {{ description(group) || t('communityGroups.noDescription') }}
          </p>

          <template v-if="group.type === 'GITHUB'">
            <div class="github-meta">
              <span>
                {{ t('communityGroups.organization') }}
                <code>{{ group.githubOrganization }}</code>
              </span>
              <span v-if="group.githubLinked">
                {{ t('communityGroups.linkedAccount') }}
                <code>{{ group.githubLogin }}</code>
              </span>
              <a-tag v-else color="orange">{{ t('communityGroups.githubNotLinked') }}</a-tag>
            </div>

            <div v-if="statuses[group.id]" class="status-panel">
              <div class="status-line">
                <span>{{ t('communityGroups.membershipStatus') }}</span>
                <a-tag :color="statusColor(statuses[group.id].status)">
                  {{ t(`communityGroups.statuses.${statuses[group.id].status}`) }}
                </a-tag>
              </div>
              <div class="team-list">
                <a-tag
                  v-for="team in statuses[group.id].teams"
                  :key="team.slug"
                  :color="teamStateColor(team.state)"
                >
                  {{ team.name }} ·
                  {{ t(`communityGroups.teamStates.${team.state}`) }}
                </a-tag>
              </div>
              <a-alert
                v-if="statuses[group.id].message"
                type="warning"
                show-icon
                class="status-message"
              >
                {{ statuses[group.id].message }}
              </a-alert>
              <span class="checked-at">
                <IconClockCircle />
                {{ t('communityGroups.checkedAt', { time: formatDate(statuses[group.id].checkedAt) }) }}
              </span>
            </div>

            <div class="group-actions">
              <a-button
                type="primary"
                :loading="joiningId === group.id"
                :disabled="!canJoin(group)"
                @click="requestJoin(group)"
              >
                <template #icon><IconGithub /></template>
                {{
                  statuses[group.id]?.status === 'ACTIVE'
                    ? t('communityGroups.joined')
                    : statuses[group.id]?.status === 'PENDING'
                      ? t('communityGroups.pending')
                      : t('communityGroups.join')
                }}
              </a-button>
            </div>
          </template>

          <div v-else class="group-actions">
            <a-button
              v-if="group.url"
              type="primary"
              :href="group.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              <template #icon><IconLink /></template>
              {{ t('communityGroups.openGroup') }}
            </a-button>
          </div>
        </article>
      </div>
      <a-empty v-else :description="t('communityGroups.empty')" />
    </a-spin>
  </div>
</template>

<style scoped>
.community-groups-page {
  width: 100%;
}

.groups-hint {
  margin-bottom: 16px;
}

.groups-spin {
  display: block;
  width: 100%;
}

.group-grid {
  display: grid;
  gap: 16px;
}

.group-card {
  padding: 20px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  box-shadow: 0 2px 10px rgb(0 0 0 / 3%);
}

.group-heading {
  display: flex;
  align-items: center;
  gap: 14px;
}

.group-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  color: rgb(var(--primary-6));
  font-size: 24px;
  background: rgb(var(--primary-1));
  border-radius: 12px;
}

.group-icon--github {
  color: var(--color-text-1);
  background: var(--color-fill-2);
}

.group-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.group-title h2 {
  margin: 0;
  font-size: 19px;
}

.group-description {
  margin: 16px 0;
  color: var(--color-text-2);
  line-height: 1.75;
  white-space: pre-wrap;
}

.github-meta,
.status-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.github-meta > span,
.status-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--color-text-3);
  font-size: 12px;
}

.github-meta code {
  color: var(--color-text-1);
}

.status-panel {
  margin-top: 10px;
}

.status-line {
  justify-content: space-between;
}

.team-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.status-message {
  margin-top: 2px;
}

.checked-at {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--color-text-3);
  font-size: 11px;
}

.group-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
