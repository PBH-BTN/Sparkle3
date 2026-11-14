<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { IconGithub, IconHome } from '@arco-design/web-vue/es/icon'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'

const route = useRoute()
const { t } = useI18n()

const state = computed(() => String(route.query.state || 'error'))
const reason = computed(() => String(route.query.reason || ''))
const status = computed(() => {
  if (state.value === 'started' || state.value === 'completed') return 'success'
  if (state.value === 'cancelled') return 'warning'
  return 'error'
})
const title = computed(() => {
  if (state.value === 'started') return t('accountDeletionResult.startedTitle')
  if (state.value === 'completed') return t('accountDeletionResult.completedTitle')
  if (state.value === 'cancelled') return t('accountDeletionResult.cancelledTitle')
  return t('accountDeletionResult.errorTitle')
})
const description = computed(() => {
  if (state.value === 'started') return t('accountDeletionResult.startedDescription')
  if (state.value === 'completed') return t('accountDeletionResult.completedDescription')
  if (state.value === 'cancelled' && reason.value === 'login') {
    return t('accountDeletionResult.cancelledByLogin')
  }
  if (state.value === 'cancelled') return t('accountDeletionResult.cancelledDescription')
  return t('accountDeletionResult.errorDescription')
})

function login() {
  window.location.assign('/oauth2/authorization/github')
}
</script>

<template>
  <main class="result-page">
    <div class="result-language"><LanguageSwitcher /></div>
    <a-card class="result-card">
      <a-result :status="status" :title="title" :subtitle="description">
        <template #extra>
          <div class="result-actions">
            <a-button v-if="state === 'cancelled'" type="primary" @click="login">
              <template #icon><IconGithub /></template>
              {{ t('accountDeletionResult.loginAgain') }}
            </a-button>
            <a-button href="/">
              <template #icon><IconHome /></template>
              {{ t('accountDeletionResult.backHome') }}
            </a-button>
          </div>
        </template>
      </a-result>
      <a-alert
        v-if="state === 'started'"
        type="warning"
        show-icon
      >
        {{ t('accountDeletionResult.startedWarning') }}
      </a-alert>
    </a-card>
  </main>
</template>

<style scoped>
.result-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 20px;
  background:
    radial-gradient(circle at 12% 12%, var(--color-primary-light-1) 0, transparent 32%),
    var(--color-fill-2);
}

.result-language {
  position: absolute;
  top: 20px;
  right: 24px;
}

.result-card {
  width: min(700px, 100%);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
  box-shadow: 0 12px 40px rgb(0 0 0 / 7%);
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

@media (max-width: 520px) {
  .result-card {
    padding: 0;
  }

  .result-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
