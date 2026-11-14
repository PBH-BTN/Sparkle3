<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconEmail,
  IconLock,
  IconNotification,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type { NotificationPreference } from '@/api/types'
import { errorMessage } from '@/utils/format'

const { t } = useI18n()
const loading = ref(true)
const saving = ref(false)
const preferences = ref<NotificationPreference[]>([])

async function load() {
  loading.value = true
  try {
    preferences.value = (await uiApi.notifications()).preferences
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    preferences.value = (
      await uiApi.updateNotifications(
        preferences.value.map((item) => ({
          category: item.category,
          inboxEnabled: item.locked ? true : item.inboxEnabled,
          emailEnabled: item.locked ? true : item.emailEnabled,
        })),
      )
    ).preferences
    Message.success(t('notifications.saved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="notification-settings-page">
    <PageHeader
      :title="t('notifications.title')"
      :description="t('notifications.description')"
    />

    <a-alert type="info" show-icon class="settings-hint">
      {{ t('notifications.hint') }}
    </a-alert>

    <a-spin :loading="loading" class="settings-spin">
      <a-card class="surface-card preferences-card">
        <div class="preference-header">
          <span>{{ t('notifications.category') }}</span>
          <span><IconNotification /> {{ t('notifications.types.inbox') }}</span>
          <span><IconEmail /> {{ t('notifications.types.email') }}</span>
        </div>

        <div
          v-for="preference in preferences"
          :key="preference.category"
          class="preference-row"
          :class="{ 'preference-row--locked': preference.locked }"
        >
          <div class="category-copy">
            <strong>
              {{ t(`notifications.categories.${preference.category}.name`) }}
              <a-tag v-if="preference.locked" color="orange" size="small">
                <template #icon><IconLock /></template>
                {{ t('notifications.mandatory') }}
              </a-tag>
            </strong>
            <span>{{ t(`notifications.categories.${preference.category}.description`) }}</span>
          </div>
          <a-switch
            v-model="preference.inboxEnabled"
            :disabled="preference.locked"
            :checked-value="true"
            :unchecked-value="false"
          />
          <a-switch
            v-model="preference.emailEnabled"
            :disabled="preference.locked"
            :checked-value="true"
            :unchecked-value="false"
          />
        </div>
      </a-card>

      <div class="save-row">
        <a-button type="primary" size="large" :loading="saving" @click="save">
          {{ t('common.save') }}
        </a-button>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.notification-settings-page,
.settings-spin {
  display: block;
  width: 100%;
}

.settings-hint {
  margin-bottom: 16px;
}

.preferences-card :deep(.arco-card-body) {
  padding: 0;
}

.preference-header,
.preference-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 110px 110px;
  align-items: center;
  gap: 16px;
}

.preference-header {
  padding: 14px 20px;
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 600;
  background: var(--color-fill-1);
}

.preference-header span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.preference-header span:first-child {
  justify-content: flex-start;
}

.preference-row {
  min-height: 88px;
  padding: 17px 20px;
  border-top: 1px solid var(--color-border-2);
}

.preference-row--locked {
  background: linear-gradient(90deg, var(--color-warning-light-1), transparent 55%);
}

.category-copy {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.category-copy strong {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--color-text-1);
}

.category-copy > span {
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.preference-row > :deep(.arco-switch) {
  justify-self: center;
}

.save-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 680px) {
  .preference-header {
    grid-template-columns: minmax(0, 1fr) 70px 70px;
    padding-inline: 14px;
    font-size: 11px;
  }

  .preference-row {
    grid-template-columns: minmax(0, 1fr) 54px 54px;
    gap: 8px;
    padding-inline: 14px;
  }
}
</style>
