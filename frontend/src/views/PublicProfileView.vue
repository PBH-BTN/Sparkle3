<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconArrowLeft,
  IconEmail,
  IconLock,
  IconUser,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type { ContactEmailRequest, PublicUserProfileResponse } from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const { formatPointBytes } = useFormatters()
const profile = ref<PublicUserProfileResponse | null>(null)
const loading = ref(true)
const sending = ref(false)
const emailVisible = ref(false)
const emailForm = reactive<ContactEmailRequest>({
  subject: '',
  content: '',
})

const uid = computed(() => String(route.params.uid ?? ''))
const displayName = computed(() =>
  profile.value?.anonymous
    ? t('common.anonymous')
    : profile.value?.nickname || t('common.anonymous'),
)
const privacyLabel = computed(() =>
  profile.value
    ? t(`privacy.levels.${profile.value.privacyLevel}`)
    : t('common.notAvailable'),
)
const privacyColor = computed(() => {
  const name = profile.value?.privacyLevel
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

async function load() {
  loading.value = true
  try {
    profile.value = await uiApi.publicProfile(uid.value)
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function openEmail() {
  emailForm.subject = ''
  emailForm.content = ''
  emailVisible.value = true
}

async function sendEmail(): Promise<boolean> {
  if (!emailForm.subject.trim()) {
    Message.warning(t('publicProfile.subjectRequired'))
    return false
  }
  if (!emailForm.content.trim()) {
    Message.warning(t('publicProfile.contentRequired'))
    return false
  }
  sending.value = true
  try {
    const result = await uiApi.sendContactEmail(uid.value, {
      subject: emailForm.subject,
      content: emailForm.content,
    })
    Message.success(
      t('publicProfile.sent', { remaining: result.remainingToday }),
    )
    return true
  } catch (error) {
    Message.error(errorMessage(error, t('publicProfile.sendFailed')))
    return false
  } finally {
    sending.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="public-profile-page">
    <PageHeader :title="t('publicProfile.title')">
      <template #actions>
        <a-button @click="router.back()">
          <template #icon><IconArrowLeft /></template>
          {{ t('common.back') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :loading="loading" class="profile-spin">
      <template v-if="profile">
        <a-alert v-if="profile.banned" type="warning" show-icon>
          {{ t('publicProfile.accountDisabled') }}
        </a-alert>

        <a-card class="surface-card profile-card">
          <div class="profile-main">
            <a-avatar :size="88" :image-url="profile.avatarUrl ?? undefined">
              <IconUserGroup v-if="profile.anonymous" />
              <IconUser v-else />
            </a-avatar>
            <div class="profile-copy">
              <h2>{{ displayName }}</h2>
              <span class="muted">UID {{ profile.id }}</span>
              <div class="profile-tags">
                <a-tag :color="privacyColor">
                  <template #icon><IconLock /></template>
                  {{ privacyLabel }}
                </a-tag>
                <a-tag v-if="profile.banned" color="red">
                  {{ t('common.paused') }}
                </a-tag>
              </div>
            </div>
          </div>

          <div class="profile-side">
            <div v-if="profile.scoreBytesRaw" class="score-block">
              <div class="score-topline">
                <span>{{ t('profile.bytesScore') }}</span>
                <span>{{ t('points.unit') }}</span>
              </div>
              <strong>{{ formatPointBytes(profile.scoreBytesRaw) }}</strong>
              <span v-if="showPointRaw(profile.scoreBytesRaw)" class="score-raw">
                {{ profile.scoreBytesRaw }} Bytes
              </span>
            </div>

            <div class="profile-actions">
              <a-button v-if="profile.self" type="primary" @click="router.push('/user/profile')">
                {{ t('publicProfile.openOwnProfile') }}
              </a-button>
              <a-button
                v-else-if="profile.canSendEmail"
                type="primary"
                @click="openEmail"
              >
                <template #icon><IconEmail /></template>
                {{ t('publicProfile.sendEmail') }}
              </a-button>
            </div>
          </div>
        </a-card>

        <a-alert
          v-if="!profile.self && !profile.canSendEmail && !profile.banned"
          type="info"
          show-icon
        >
          {{ t('publicProfile.emailUnavailable') }}
        </a-alert>
      </template>
    </a-spin>

    <a-modal
      v-model:visible="emailVisible"
      :title="t('publicProfile.sendEmail')"
      :ok-loading="sending"
      :ok-text="t('publicProfile.send')"
      :cancel-text="t('common.cancel')"
      :on-before-ok="sendEmail"
      unmount-on-close
    >
      <a-alert type="info" show-icon class="email-hint">
        {{ t('publicProfile.emailHint') }}
      </a-alert>
      <a-form :model="emailForm" layout="vertical" @submit="sendEmail">
        <a-form-item :label="t('publicProfile.subject')" required>
          <a-input
            v-model="emailForm.subject"
            :max-length="80"
            show-word-limit
            :placeholder="t('publicProfile.subjectPlaceholder')"
          />
        </a-form-item>
        <a-form-item :label="t('publicProfile.content')" required>
          <a-textarea
            v-model="emailForm.content"
            :max-length="2000"
            show-word-limit
            :auto-size="{ minRows: 7, maxRows: 14 }"
            :placeholder="t('publicProfile.contentPlaceholder')"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.public-profile-page {
  width: min(820px, 100%);
  margin: 0 auto;
}

.profile-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.profile-card :deep(.arco-card-body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.profile-side {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  gap: 14px;
}

.score-block {
  display: flex;
  flex-direction: column;
  min-width: 250px;
  padding: 18px 20px;
  color: #fff;
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
  gap: 12px;
  margin-bottom: 9px;
  color: rgb(255 255 255 / 72%);
  font-size: 11px;
}

.score-block strong {
  color: #fff;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.score-raw {
  margin-top: 3px;
  color: rgb(255 255 255 / 60%);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.profile-copy h2 {
  margin: 0 0 4px;
  overflow-wrap: anywhere;
  font-size: 24px;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.muted {
  color: var(--color-text-3);
}

.email-hint {
  margin-bottom: 16px;
}

@media (max-width: 620px) {
  .profile-card :deep(.arco-card-body),
  .profile-main,
  .profile-side {
    align-items: flex-start;
    flex-direction: column;
  }

  .score-block {
    width: 100%;
    min-width: 0;
  }
}
</style>
