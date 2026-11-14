<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconEmail,
  IconNotification,
  IconSend,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type { StaffMessageResult } from '@/api/adminTypes'
import type { NotificationCategory } from '@/api/types'
import { errorMessage } from '@/utils/format'

const { t } = useI18n()
const sending = ref(false)
const result = ref<StaffMessageResult | null>(null)
const form = reactive({
  audience: 'ALL' as 'ALL' | 'USER',
  recipientId: undefined as number | undefined,
  category: 'DIRECT_MESSAGE' as NotificationCategory,
  subject: '',
  content: '',
  systemIdentity: true,
  forceEmail: false,
})

const categories: NotificationCategory[] = [
  'DIRECT_MESSAGE',
  'LEGAL_COMPLIANCE',
  'ACCOUNT_SECURITY',
  'CLIENT_SECURITY',
  'MAJOR_ANNOUNCEMENT',
  'DAILY_ANNOUNCEMENT',
  'CHANGELOG',
  'SOCIAL_ALERT',
  'GENERAL_NOTICE',
]

const remaining = computed(() => result.value?.remainingToday)

async function send() {
  if (!form.subject.trim() || !form.content.trim()) {
    Message.warning(t('admin.messages.required'))
    return
  }
  if (form.audience === 'USER' && !form.recipientId) {
    Message.warning(t('admin.messages.recipientRequired'))
    return
  }
  sending.value = true
  try {
    result.value = await adminApi.sendStaffMessage({
      subject: form.subject.trim(),
      content: form.content.trim(),
      category: form.category,
      recipientId: form.audience === 'USER' ? form.recipientId ?? null : null,
      systemIdentity: form.systemIdentity,
      forceEmail: form.forceEmail,
    })
    form.subject = ''
    form.content = ''
    Message.success(
      t('admin.messages.sent', { count: result.value.recipients }),
    )
  } catch (error) {
    Message.error(errorMessage(error, t('admin.messages.sendFailed')))
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="page-stack admin-messages-page">
    <PageHeader
      :title="t('admin.messages.title')"
      :description="t('admin.messages.description')"
    />

    <a-alert type="warning" show-icon>
      {{ t('admin.messages.permissionHint') }}
    </a-alert>

    <a-card class="surface-card message-composer">
      <a-form :model="form" layout="vertical">
        <a-row :gutter="16">
          <a-col :xs="24" :md="12">
            <a-form-item :label="t('admin.messages.audience')">
              <a-radio-group v-model="form.audience" type="button">
                <a-radio value="ALL">
                  <template #icon><IconUserGroup /></template>
                  {{ t('admin.messages.allUsers') }}
                </a-radio>
                <a-radio value="USER">
                  <template #icon><IconNotification /></template>
                  {{ t('admin.messages.singleUser') }}
                </a-radio>
              </a-radio-group>
            </a-form-item>
          </a-col>
          <a-col v-if="form.audience === 'USER'" :xs="24" :md="12">
            <a-form-item :label="t('admin.messages.recipientId')" required>
              <a-input-number
                v-model="form.recipientId"
                :min="1"
                hide-button
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item :label="t('admin.messages.category')">
              <a-select v-model="form.category">
                <a-option v-for="category in categories" :key="category" :value="category">
                  {{ t(`notifications.categories.${category}.name`) }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item :label="t('admin.messages.senderIdentity')">
              <a-space direction="vertical">
                <a-switch v-model="form.systemIdentity">
                  <template #checked>{{ t('admin.messages.systemIdentity') }}</template>
                  <template #unchecked>{{ t('admin.messages.personalIdentity') }}</template>
                </a-switch>
                <span class="field-help">
                  {{ t('admin.messages.systemIdentityHint') }}
                </span>
              </a-space>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item :label="t('admin.messages.emailReminder')">
              <a-space direction="vertical">
                <a-switch v-model="form.forceEmail" />
                <span class="field-help">{{ t('admin.messages.forceEmailHint') }}</span>
              </a-space>
            </a-form-item>
          </a-col>
          <a-col :xs="24">
            <a-form-item :label="t('admin.messages.subject')" required>
              <a-input
                v-model="form.subject"
                :max-length="500"
                show-word-limit
                :placeholder="t('admin.messages.subjectPlaceholder')"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24">
            <a-form-item :label="t('admin.messages.content')" required>
              <a-textarea
                v-model="form.content"
                :max-length="20000"
                show-word-limit
                :auto-size="{ minRows: 9, maxRows: 20 }"
                :placeholder="t('admin.messages.contentPlaceholder')"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-alert v-if="form.audience === 'ALL'" type="info" show-icon>
          {{ t('admin.messages.broadcastHint') }}
        </a-alert>
        <a-alert v-else type="info" show-icon>
          {{ t('admin.messages.bypassPrivacyHint') }}
        </a-alert>

        <div class="composer-footer">
          <span v-if="remaining !== undefined" class="quota">
            {{ t('admin.messages.remaining', { count: remaining }) }}
          </span>
          <a-button type="primary" size="large" :loading="sending" @click="send">
            <template #icon><IconSend /></template>
            {{ t('admin.messages.send') }}
          </a-button>
        </div>
      </a-form>
    </a-card>

    <a-card v-if="result" class="surface-card result-card">
      <div class="result-summary">
        <span class="result-icon"><IconEmail /></span>
        <div>
          <strong>{{ t('admin.messages.resultTitle') }}</strong>
          <span>{{ t('admin.messages.resultText', { count: result.recipients }) }}</span>
        </div>
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.admin-messages-page {
  width: min(1040px, 100%);
}

.message-composer :deep(.arco-card-body) {
  padding: 24px;
}

.field-help {
  max-width: 420px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.55;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 24px;
}

.quota {
  color: var(--color-text-3);
  font-size: 12px;
}

.result-summary {
  display: flex;
  align-items: center;
  gap: 14px;
}

.result-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: rgb(var(--green-6));
  font-size: 20px;
  background: var(--color-success-light-1);
  border-radius: var(--app-radius);
}

.result-summary div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.result-summary span:last-child {
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
