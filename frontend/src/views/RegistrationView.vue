<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconCheckCircle,
  IconGithub,
  IconHome,
  IconUser,
} from '@arco-design/web-vue/es/icon'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { uiApi } from '@/api'
import type { PendingGithubRegistration } from '@/api/types'
import { errorMessage } from '@/utils/format'

const { t } = useI18n()
const loading = ref(true)
const registering = ref(false)
const loggingOut = ref(false)
const profile = ref<PendingGithubRegistration | null>(null)
const loadError = ref('')

async function register() {
  if (registering.value || loggingOut.value) return
  registering.value = true
  try {
    await uiApi.completeGithubRegistration()
    window.location.assign('/')
  } catch (error) {
    Message.error(errorMessage(error, t('registration.failed')))
    registering.value = false
  }
}

async function logout() {
  if (loggingOut.value || registering.value) return
  loggingOut.value = true
  try {
    await uiApi.logout()
  } catch {
    // Redirecting to login also clears the visible OAuth registration state.
  } finally {
    window.location.assign('/login')
  }
}

onMounted(async () => {
  try {
    // Materializes the CSRF cookie needed by the registration action.
    await uiApi.session()
    profile.value = await uiApi.pendingGithubRegistration()
  } catch (error) {
    loadError.value = errorMessage(error, t('registration.pendingExpired'))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="registration-page">
    <div class="registration-language"><LanguageSwitcher /></div>

    <a-card class="registration-card">
      <div class="registration-brand">
        <span class="registration-logo"><IconGithub /></span>
        <div>
          <strong>{{ t('app.shortName') }}</strong>
          <small>{{ t('registration.badge') }}</small>
        </div>
      </div>

      <a-spin :loading="loading" class="registration-spin">
        <template v-if="profile">
          <div class="verification-state">
            <IconCheckCircle />
            {{ t('registration.githubVerified') }}
          </div>
          <h1>{{ t('registration.title') }}</h1>
          <p class="registration-description">{{ t('registration.description') }}</p>

          <div class="github-profile">
            <a-avatar :size="64" :image-url="profile.avatarUrl">
              <IconUser />
            </a-avatar>
            <div class="profile-copy">
              <strong>{{ profile.nickname }}</strong>
              <span>@{{ profile.githubLogin }}</span>
              <small>{{ profile.email }}</small>
            </div>
          </div>

          <a-alert type="info" show-icon class="registration-note">
            {{ t('registration.oneClickHint') }}
          </a-alert>

          <div class="registration-actions">
            <a-button
              type="primary"
              size="large"
              long
              :loading="registering"
              :disabled="loggingOut"
              @click="register"
            >
              <template #icon><IconCheckCircle /></template>
              {{ t('registration.registerAction') }}
            </a-button>
            <a-button
              size="large"
              long
              :loading="loggingOut"
              :disabled="registering"
              @click="logout"
            >
              <template #icon><IconHome /></template>
              {{ t('registration.logoutAction') }}
            </a-button>
          </div>
        </template>

        <a-result
          v-else-if="!loading"
          status="warning"
          :title="t('registration.pendingExpired')"
          :subtitle="loadError"
        >
          <template #extra>
            <a-button type="primary" href="/login">
              {{ t('registration.backToLogin') }}
            </a-button>
          </template>
        </a-result>
      </a-spin>
    </a-card>
  </main>
</template>

<style scoped>
.registration-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 20px;
  background:
    radial-gradient(circle at 8% 10%, var(--color-primary-light-1) 0, transparent 32%),
    radial-gradient(circle at 92% 88%, var(--color-primary-light-1) 0, transparent 30%),
    var(--color-fill-2);
}

.registration-language {
  position: absolute;
  top: 20px;
  right: 24px;
}

.registration-card {
  width: min(520px, 100%);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
  box-shadow: 0 16px 44px rgb(0 0 0 / 8%);
}

.registration-card :deep(.arco-card-body) {
  padding: 34px;
}

.registration-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 30px;
}

.registration-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: #fff;
  font-size: 20px;
  background: var(--color-text-1);
  border-radius: var(--app-radius);
}

.registration-brand div {
  display: flex;
  flex-direction: column;
}

.registration-brand strong {
  color: var(--color-text-1);
  font-size: 14px;
}

.registration-brand small {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 11px;
}

.registration-spin {
  display: block;
  min-height: 260px;
}

.verification-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgb(var(--green-6));
  font-size: 12px;
  font-weight: 600;
}

.registration-card h1 {
  margin: 12px 0 7px;
  color: var(--color-text-1);
  font-size: 28px;
  line-height: 1.35;
}

.registration-description {
  margin: 0;
  color: var(--color-text-3);
  line-height: 1.7;
}

.github-profile {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 16px;
  margin: 24px 0 16px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
}

.profile-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.profile-copy strong,
.profile-copy span,
.profile-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-copy strong {
  color: var(--color-text-1);
  font-size: 16px;
}

.profile-copy span {
  margin-top: 3px;
  color: var(--color-text-3);
}

.profile-copy small {
  margin-top: 6px;
  color: var(--color-text-3);
  font-size: 12px;
}

.registration-note {
  margin-bottom: 20px;
}

.registration-actions {
  display: grid;
  gap: 10px;
}

@media (max-width: 520px) {
  .registration-card :deep(.arco-card-body) {
    padding: 24px 20px;
  }

  .registration-card h1 {
    font-size: 24px;
  }
}
</style>
