<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconCheckCircle,
  IconCopy,
  IconExclamationCircle,
  IconSafe,
  IconList,
  IconPlus,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import type { ApplicationCredentials } from '@/api/types'

const router = useRouter()
const { t } = useI18n()
const credentials = ref<ApplicationCredentials | null>(null)
const credentialStorageKey = 'sparkle-application-credentials'

onMounted(() => {
  const raw = sessionStorage.getItem(credentialStorageKey)
  if (raw) {
    try {
      credentials.value = JSON.parse(raw) as ApplicationCredentials
    } catch {
      credentials.value = null
    }
  }
})

onBeforeUnmount(() => {
  sessionStorage.removeItem(credentialStorageKey)
})

async function copy(value: string) {
  await navigator.clipboard.writeText(value)
  Message.success(t('common.copied'))
}
</script>

<template>
  <div class="credentials-page">
    <PageHeader
      :title="t('userApps.credentialsTitle')"
      :description="t('userApps.credentialsDescription')"
    />

    <a-result v-if="!credentials" status="warning" :title="t('userApps.credentialsMissing')">
      <template #extra>
        <a-button type="primary" @click="router.replace('/userapp/create')">
          {{ t('userApps.createAnother') }}
        </a-button>
      </template>
    </a-result>

    <a-card v-else class="surface-card">
      <div class="success-heading">
        <span class="success-icon"><IconCheckCircle /></span>
        <div>
          <h2>{{ credentials.application.comment }}</h2>
          <p>{{ t('userApps.credentialsDescription') }}</p>
        </div>
      </div>

      <a-alert type="warning" show-icon class="credential-warning">
        <template #title>
          <IconExclamationCircle />
          {{ t('userApps.credentialsWarning') }}
        </template>
      </a-alert>

      <div class="credential-block">
        <div class="credential-label">{{ t('userApps.appId') }}</div>
        <div class="credential-value">
          <code>{{ credentials.appId }}</code>
          <a-button size="small" @click="copy(credentials.appId)">
            <template #icon><IconCopy /></template>
            {{ t('common.copy') }}
          </a-button>
        </div>
      </div>

      <div class="credential-block credential-block--secret">
        <div class="credential-label">
          <IconSafe />
          {{ t('userApps.appSecret') }}
        </div>
        <div class="credential-value">
          <code>{{ credentials.appSecret }}</code>
          <a-button size="small" type="primary" @click="copy(credentials.appSecret)">
            <template #icon><IconCopy /></template>
            {{ t('common.copy') }}
          </a-button>
        </div>
      </div>

      <div class="credential-actions">
        <a-button type="primary" size="large" @click="router.push('/userapp')">
          <template #icon><IconList /></template>
          {{ t('userApps.returnToList') }}
        </a-button>
        <a-button size="large" @click="router.push('/userapp/create')">
          <template #icon><IconPlus /></template>
          {{ t('userApps.createAnother') }}
        </a-button>
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.credentials-page {
  width: min(780px, 100%);
  margin: 0 auto;
}

.success-heading {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.success-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: rgb(var(--green-6));
  font-size: 24px;
  background: rgb(var(--green-1));
  border-radius: 50%;
}

.success-heading h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.success-heading p {
  margin: 0;
  color: var(--color-text-3);
}

.credential-warning {
  margin: 24px 0;
}

.credential-block {
  margin-top: 16px;
  padding: 16px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.credential-block--secret {
  background: var(--color-primary-light-1);
  border-color: var(--color-primary-light-2);
}

.credential-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 600;
}

.credential-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.credential-value code {
  overflow-wrap: anywhere;
  font-size: 14px;
  font-weight: 600;
}

.credential-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 28px;
}

@media (max-width: 620px) {
  .credential-value {
    align-items: stretch;
    flex-direction: column;
  }

  .credential-actions {
    flex-direction: column;
  }
}
</style>
