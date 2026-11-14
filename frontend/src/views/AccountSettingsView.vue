<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconClockCircle,
  IconDelete,
  IconExclamationCircle,
  IconGithub,
  IconSafe,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type { AccountDeletionStatus } from '@/api/types'
import { useSessionStore } from '@/stores/session'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const session = useSessionStore()
const status = ref<AccountDeletionStatus | null>(null)
const loading = ref(true)
const modalVisible = ref(false)
const confirmed = ref(false)
const redirecting = ref(false)

const pending = computed(() => status.value?.state === 'PENDING')
const completed = computed(() => status.value?.state === 'COMPLETED')

function openWarning() {
  confirmed.value = false
  modalVisible.value = true
}

async function requestReauthentication() {
  if (!confirmed.value || redirecting.value) return
  redirecting.value = true
  try {
    const response = await uiApi.beginAccountDeletionReauthentication()
    window.location.assign(response.redirectUrl)
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
    redirecting.value = false
  }
}

onMounted(async () => {
  try {
    status.value = await uiApi.accountDeletionStatus()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-stack account-settings">
    <PageHeader
      :title="t('accountSettings.title')"
      :description="t('accountSettings.description')"
    />

    <a-spin :loading="loading">
      <a-card class="surface-card account-card">
        <template #title>
          <span class="card-title">
            <IconSafe />
            {{ t('accountSettings.profileTitle') }}
          </span>
        </template>
        <a-descriptions :column="1" layout="horizontal" bordered>
          <a-descriptions-item :label="t('profile.email')">
            {{ session.user?.email }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('accountSettings.uid')">
            {{ session.user?.id }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('accountSettings.loginMethod')">
            <span class="login-method">
              <IconGithub />
              GitHub OAuth
            </span>
          </a-descriptions-item>
        </a-descriptions>
      </a-card>

      <a-card class="surface-card danger-card">
        <template #title>
          <span class="card-title danger-title">
            <IconExclamationCircle />
            {{ t('accountSettings.dangerTitle') }}
          </span>
        </template>
        <p class="danger-description">{{ t('accountSettings.dangerDescription') }}</p>

        <a-alert v-if="pending" type="warning" show-icon class="status-alert">
          <template #title>{{ t('accountSettings.pendingTitle') }}</template>
          {{ t('accountSettings.pendingText', { time: formatDate(status?.coolingEndsAt) }) }}
        </a-alert>
        <a-alert v-else-if="completed" type="error" show-icon class="status-alert">
          {{ t('accountSettings.completedText') }}
        </a-alert>

        <div class="danger-row">
          <div>
            <strong>{{ t('accountSettings.deleteAccount') }}</strong>
            <p>{{ t('accountSettings.deleteHint') }}</p>
          </div>
          <a-button
            class="danger-button"
            type="primary"
            :disabled="pending || completed"
            @click="openWarning"
          >
            <template #icon><IconDelete /></template>
            {{ t('accountSettings.deleteAction') }}
          </a-button>
        </div>
      </a-card>
    </a-spin>

    <a-modal
      v-model:visible="modalVisible"
      :title="t('accountSettings.warningTitle')"
      :width="580"
      :footer="false"
      :mask-closable="!redirecting"
      :closable="!redirecting"
      unmount-on-close
    >
      <div class="warning-body">
        <div class="warning-heading">
          <span class="warning-icon"><IconExclamationCircle /></span>
          <div>
            <strong>{{ t('accountSettings.warningHeading') }}</strong>
            <p>{{ t('accountSettings.warningIntro') }}</p>
          </div>
        </div>

        <ul class="warning-list">
          <li>
            <IconClockCircle />
            <span>{{ t('accountSettings.warningCooling') }}</span>
          </li>
          <li>
            <IconExclamationCircle />
            <span>{{ t('accountSettings.warningCancellation') }}</span>
          </li>
          <li>
            <IconDelete />
            <span>{{ t('accountSettings.warningAnonymize') }}</span>
          </li>
          <li>
            <IconGithub />
            <span>{{ t('accountSettings.warningReauth') }}</span>
          </li>
        </ul>

        <a-checkbox v-model="confirmed" class="confirm-checkbox">
          {{ t('accountSettings.confirmCheckbox') }}
        </a-checkbox>
      </div>

      <div class="modal-actions">
        <a-button :disabled="redirecting" @click="modalVisible = false">
          {{ t('common.cancel') }}
        </a-button>
        <a-button
          class="danger-button"
          type="primary"
          :loading="redirecting"
          :disabled="!confirmed || redirecting"
          @click="requestReauthentication"
        >
          <template #icon><IconGithub /></template>
          {{ t('accountSettings.reauthAction') }}
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.account-settings {
  width: 100%;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.login-method {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.danger-card {
  border-color: rgb(var(--red-2));
  box-shadow: 0 8px 24px rgb(var(--red-6) / 5%);
}

.danger-card :deep(.arco-card-header) {
  border-bottom-color: rgb(var(--red-2));
}

.danger-title {
  color: rgb(var(--red-6));
}

.danger-description {
  margin: 0 0 18px;
  color: var(--color-text-2);
  line-height: 1.7;
}

.status-alert {
  margin-bottom: 16px;
}

.danger-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 16px 18px;
  background: var(--color-danger-light-1);
  border: 1px solid rgb(var(--red-2));
  border-radius: var(--app-radius);
}

.danger-row strong {
  color: var(--color-text-1);
}

.danger-row p {
  margin: 5px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.danger-button.arco-btn-primary {
  color: #fff;
  background: rgb(var(--red-6));
  border-color: rgb(var(--red-6));
}

.danger-button.arco-btn-primary:hover,
.danger-button.arco-btn-primary:focus-visible {
  color: #fff;
  background: rgb(var(--red-7));
  border-color: rgb(var(--red-7));
}

.warning-body {
  padding-top: 4px;
}

.warning-heading {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  background: var(--color-danger-light-1);
  border: 1px solid rgb(var(--red-2));
  border-radius: var(--app-radius);
}

.warning-icon {
  display: inline-flex;
  flex: 0 0 auto;
  color: rgb(var(--red-6));
  font-size: 22px;
}

.warning-heading strong {
  color: rgb(var(--red-7));
}

.warning-heading p {
  margin: 5px 0 0;
  color: var(--color-text-2);
  line-height: 1.65;
}

.warning-list {
  display: grid;
  gap: 11px;
  padding: 18px 4px;
  margin: 0;
  list-style: none;
}

.warning-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: var(--color-text-2);
  line-height: 1.65;
}

.warning-list svg {
  flex: 0 0 auto;
  margin-top: 3px;
  color: rgb(var(--red-6));
}

.confirm-checkbox {
  padding: 12px 14px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 20px;
  margin-top: 20px;
  border-top: 1px solid var(--color-border-2);
}

@media (max-width: 620px) {
  .danger-row {
    align-items: stretch;
    flex-direction: column;
  }

  .danger-row .arco-btn {
    width: 100%;
  }

  .modal-actions {
    flex-direction: column-reverse;
  }

  .modal-actions .arco-btn {
    width: 100%;
  }
}
</style>
