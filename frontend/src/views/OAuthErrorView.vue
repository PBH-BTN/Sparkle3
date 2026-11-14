<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { IconGithub, IconHome } from '@arco-design/web-vue/es/icon'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'

const route = useRoute()
const { t } = useI18n()
const message = computed(() => String(route.query.message || t('common.requestFailed')))
const retryable = computed(() => route.query.retryable !== 'false')
</script>

<template>
  <main class="oauth-page">
    <div class="oauth-language"><LanguageSwitcher /></div>
    <a-card class="error-card">
      <a-result status="error" :title="t('oauth.title')" :subtitle="t('oauth.description')">
        <template #extra>
          <div class="error-actions">
            <a-button href="/oauth2/authorization/github" type="primary">
              <template #icon><IconGithub /></template>
              {{ t('oauth.retry') }}
            </a-button>
            <a-button href="/">
              <template #icon><IconHome /></template>
              {{ t('oauth.backHome') }}
            </a-button>
          </div>
        </template>
      </a-result>
      <div class="error-message">
        <strong>{{ t('oauth.error') }}</strong>
        <span>{{ message }}</span>
      </div>
      <a-alert v-if="retryable" type="info" show-icon>{{ t('oauth.retryHint') }}</a-alert>
    </a-card>
  </main>
</template>

<style scoped>
.oauth-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 20px;
  background: var(--color-fill-2);
}

.oauth-language {
  position: absolute;
  top: 20px;
  right: 24px;
}

.error-card {
  width: min(680px, 100%);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
}

.error-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.error-message {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  margin: 0 0 16px;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
  background: var(--color-danger-light-1);
  border: 1px solid rgb(var(--red-2));
  border-radius: var(--app-radius);
}

.error-message strong {
  color: rgb(var(--red-6));
}
</style>
