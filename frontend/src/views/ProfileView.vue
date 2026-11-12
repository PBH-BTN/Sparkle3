<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconGithub,
  IconLock,
  IconSettings,
  IconUser,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type { UserProfileResponse } from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const router = useRouter()
const { t } = useI18n()
const { formatDate, formatPointBytes } = useFormatters()
const profile = ref<UserProfileResponse | null>(null)
const loading = ref(true)

const privacyLabel = computed(() => {
  const name = profile.value?.user.privacyLevel.name ?? 'HIGH'
  return t(`privacy.levels.${name}`)
})

const privacyColor = computed(() => {
  const name = profile.value?.user.privacyLevel.name
  return name === 'DISABLED'
    ? 'green'
    : name === 'LOW'
      ? 'arcoblue'
      : name === 'MEDIUM'
        ? 'orange'
        : 'red'
})

function showPointRaw(value: string | null | undefined) {
  if (!value) return false
  try {
    const points = BigInt(value)
    return (points < 0n ? -points : points) >= 1024n
  } catch {
    return false
  }
}

onMounted(async () => {
  try {
    profile.value = await uiApi.profile()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="profile-page">
    <PageHeader :title="t('profile.title')">
      <template #actions>
        <a-button @click="router.push('/user/settings')">
          <template #icon><IconSettings /></template>
          {{ t('profile.settings') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :loading="loading" class="profile-spin">
      <template v-if="profile">
        <a-card class="surface-card profile-summary">
          <div class="profile-main">
            <a-avatar :size="88" :image-url="profile.user.avatarUrl">
              <IconUser />
            </a-avatar>
            <div class="profile-identity">
              <h2>{{ profile.user.nickname }}</h2>
              <span class="muted">UID {{ profile.user.id }}</span>
            </div>
          </div>

          <div class="score-block">
            <div class="score-topline">
              <a-tooltip :content="t('profile.bytesHint')">
                <span class="score-label">{{ t('profile.bytesScore') }}</span>
              </a-tooltip>
              <span class="score-unit">{{ t('points.unit') }}</span>
            </div>
            <strong>{{ formatPointBytes(profile.scoreBytesRaw) }}</strong>
            <span v-if="showPointRaw(profile.scoreBytesRaw)" class="score-raw">
              {{ profile.scoreBytesRaw }} Bytes
            </span>
            <a-button
              type="primary"
              long
              class="score-action"
              @click="router.push('/user/points')"
            >
              {{ t('profile.openPoints') }}
            </a-button>
          </div>
        </a-card>

        <a-card class="surface-card details-card">
          <a-descriptions :column="1" layout="horizontal" bordered>
            <a-descriptions-item :label="t('profile.email')">
              {{ profile.user.email }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('profile.registerAt')">
              {{ formatDate(profile.user.registerAt) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('profile.accountStatus')">
              <a-tag v-if="profile.user.bannedAt" color="red">{{ t('common.paused') }}</a-tag>
              <a-tag v-else color="green">{{ t('common.normal') }}</a-tag>
              <div v-if="profile.user.bannedAt" class="banned-note">
                {{
                  t('profile.bannedReason', {
                    reason: profile.user.bannedReason || 'N/A',
                  })
                }}
                {{ t('profile.bannedHint') }}
              </div>
            </a-descriptions-item>
            <a-descriptions-item :label="t('profile.privacyProtection')">
              <a-tag :color="privacyColor">
                <template #icon><IconLock /></template>
                {{ privacyLabel }}
              </a-tag>
              <a-button
                type="text"
                size="small"
                class="privacy-settings-button"
                @click="router.push('/user/settings/privacy')"
              >
                {{ t('profile.settings') }}
              </a-button>
            </a-descriptions-item>
            <a-descriptions-item
              v-if="profile.relation"
              :label="t('profile.githubAccount')"
            >
              <a
                :href="`https://github.com/${profile.relation.platformUserLogin}`"
                target="_blank"
                rel="noopener noreferrer"
              >
                <IconGithub />
                {{ profile.relation.platformUserLogin }}
              </a>
              <span class="muted"> · UID {{ profile.relation.platformUserId }}</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.profile-page {
  width: min(960px, 100%);
  margin: 0 auto;
}

.profile-spin {
  display: block;
  width: 100%;
}

.profile-summary :deep(.arco-card-body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 28px;
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 20px;
}

.profile-identity h2 {
  margin: 0 0 4px;
  font-size: 24px;
}

.score-block {
  display: flex;
  flex-direction: column;
  min-width: 250px;
  padding: 20px;
  color: #fff;
  text-align: left;
  background:
    radial-gradient(circle at 88% 12%, rgb(255 255 255 / 22%), transparent 34%),
    linear-gradient(135deg, #0e7490, #155e75 60%, #164e63);
  border-radius: 12px;
  box-shadow: 0 12px 30px rgb(14 116 144 / 18%);
}

.score-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.score-block strong {
  color: #fff;
  font-size: 30px;
  letter-spacing: -0.03em;
}

.score-raw {
  margin-top: 3px;
  color: rgb(255 255 255 / 60%);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}

.score-label {
  color: rgb(255 255 255 / 76%);
  font-size: 12px;
  cursor: help;
}

.score-unit {
  padding: 2px 7px;
  color: rgb(255 255 255 / 72%);
  font-size: 10px;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 999px;
}

.score-action {
  margin-top: 16px;
  color: #155e75;
  background: rgb(255 255 255 / 92%);
  border-color: transparent;
}

.score-action:hover {
  color: #155e75;
  background: #fff;
}

.details-card {
  margin-top: 16px;
}

.banned-note {
  margin-top: 8px;
  color: rgb(var(--red-6));
  font-size: 12px;
  line-height: 1.6;
}

.privacy-settings-button {
  margin-left: 8px;
}

@media (max-width: 720px) {
  .profile-summary :deep(.arco-card-body) {
    align-items: stretch;
    flex-direction: column;
  }

  .score-block {
    text-align: left;
  }
}
</style>
