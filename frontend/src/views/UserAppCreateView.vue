<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { IconArrowLeft, IconCheck } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type { ApplicationCredentials } from '@/api/types'
import { errorMessage } from '@/utils/format'

const router = useRouter()
const { t } = useI18n()
const comment = ref('')
const submitting = ref(false)
const credentialStorageKey = 'sparkle-application-credentials'

async function submit() {
  const trimmedComment = comment.value.trim()
  if (!trimmedComment) {
    Message.warning(t('userApps.appNameHelp'))
    return
  }
  submitting.value = true
  try {
    const credentials: ApplicationCredentials = await uiApi.createUserApp(trimmedComment)
    sessionStorage.setItem(credentialStorageKey, JSON.stringify(credentials))
    Message.success(t('userApps.createdSuccess'))
    router.replace('/userapp/created')
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="form-page">
    <PageHeader :title="t('userApps.createTitle')" :description="t('userApps.createDescription')" />

    <a-card class="surface-card form-card">
      <a-alert type="info" show-icon>{{ t('userApps.appNameHelp') }}</a-alert>

      <form @submit.prevent="submit">
        <a-form-item :label="t('userApps.appName')" required>
          <a-input
            v-model="comment"
            :placeholder="t('userApps.appNamePlaceholder')"
            :max-length="100"
            show-word-limit
            allow-clear
            size="large"
            autofocus
          />
        </a-form-item>

        <div class="form-actions">
          <a-button size="large" @click="router.push('/userapp')">
            <template #icon><IconArrowLeft /></template>
            {{ t('common.back') }}
          </a-button>
          <a-button type="primary" size="large" html-type="submit" :loading="submitting">
            <template #icon><IconCheck /></template>
            {{ t('userApps.createAction') }}
          </a-button>
        </div>
      </form>
    </a-card>
  </div>
</template>

<style scoped>
.form-page {
  width: min(680px, 100%);
  margin: 0 auto;
}

.form-card :deep(.arco-card-body) {
  padding: 24px;
}

.form-card :deep(.arco-alert) {
  margin-bottom: 24px;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 8px;
}

@media (max-width: 520px) {
  .form-actions {
    flex-direction: column-reverse;
  }

  .form-actions .arco-btn {
    width: 100%;
  }
}
</style>
