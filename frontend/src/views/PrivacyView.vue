<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconEye,
  IconEyeInvisible,
  IconLock,
  IconUser,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type { PrivacySettings } from '@/api/types'
import { errorMessage } from '@/utils/format'

const { t } = useI18n()
const loading = ref(true)
const saving = ref(false)
const form = reactive<PrivacySettings>({
  avatarVisibility: 'BLURRED',
  usernameVisibility: 'BLURRED',
  pointsVisibility: 'SHOW',
})

onMounted(async () => {
  try {
    Object.assign(form, (await uiApi.privacy()).current)
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
})

async function save() {
  saving.value = true
  try {
    Object.assign(form, (await uiApi.updatePrivacy({ ...form })).current)
    Message.success(t('privacy.saveSuccess'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="privacy-page">
    <PageHeader :title="t('privacy.title')" :description="t('privacy.description')" />

    <a-spin :loading="loading" class="privacy-spin">
      <div class="privacy-grid">
        <a-card class="surface-card privacy-card">
          <template #title>
            <span class="card-title"><IconUser />{{ t('privacy.avatarTitle') }}</span>
          </template>
          <p class="option-description">{{ t('privacy.avatarDescription') }}</p>
          <a-radio-group v-model="form.avatarVisibility" direction="vertical">
            <a-radio value="ORIGINAL">
              <span class="option-label"><IconEye />{{ t('privacy.avatarOptions.ORIGINAL') }}</span>
              <span class="option-help">{{ t('privacy.avatarHelp.ORIGINAL') }}</span>
            </a-radio>
            <a-radio value="BLURRED">
              <span class="option-label"><IconLock />{{ t('privacy.avatarOptions.BLURRED') }}</span>
              <span class="option-help">{{ t('privacy.avatarHelp.BLURRED') }}</span>
            </a-radio>
            <a-radio value="HIDDEN">
              <span class="option-label">
                <IconEyeInvisible />{{ t('privacy.avatarOptions.HIDDEN') }}
              </span>
              <span class="option-help">{{ t('privacy.avatarHelp.HIDDEN') }}</span>
            </a-radio>
          </a-radio-group>
        </a-card>

        <a-card class="surface-card privacy-card">
          <template #title>
            <span class="card-title"><IconUser />{{ t('privacy.usernameTitle') }}</span>
          </template>
          <p class="option-description">{{ t('privacy.usernameDescription') }}</p>
          <a-radio-group v-model="form.usernameVisibility" direction="vertical">
            <a-radio value="FULL">
              <span class="option-label">{{ t('privacy.usernameOptions.FULL') }}</span>
              <span class="option-help">{{ t('privacy.usernameHelp.FULL') }}</span>
            </a-radio>
            <a-radio value="BLURRED">
              <span class="option-label">{{ t('privacy.usernameOptions.BLURRED') }}</span>
              <span class="option-help">{{ t('privacy.usernameHelp.BLURRED') }}</span>
            </a-radio>
          </a-radio-group>
        </a-card>

        <a-card class="surface-card privacy-card">
          <template #title>
            <span class="card-title"><IconLock />{{ t('privacy.pointsTitle') }}</span>
          </template>
          <p class="option-description">{{ t('privacy.pointsDescription') }}</p>
          <a-radio-group v-model="form.pointsVisibility" direction="vertical">
            <a-radio value="SHOW">
              <span class="option-label">{{ t('privacy.pointsOptions.SHOW') }}</span>
              <span class="option-help">{{ t('privacy.pointsHelp.SHOW') }}</span>
            </a-radio>
            <a-radio value="HIDDEN">
              <span class="option-label">{{ t('privacy.pointsOptions.HIDDEN') }}</span>
              <span class="option-help">{{ t('privacy.pointsHelp.HIDDEN') }}</span>
            </a-radio>
          </a-radio-group>
        </a-card>
      </div>

      <div class="save-row">
        <a-button type="primary" size="large" :loading="saving" @click="save">
          {{ t('common.save') }}
        </a-button>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.privacy-page,
.privacy-spin {
  display: block;
  width: 100%;
}

.privacy-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.privacy-card:last-child {
  grid-column: 1 / -1;
}

.card-title,
.option-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title {
  font-weight: 600;
}

.option-description {
  margin: 0 0 16px;
  color: var(--color-text-3);
  line-height: 1.65;
}

.option-label {
  color: var(--color-text-1);
  font-weight: 600;
}

.option-help {
  display: block;
  max-width: 520px;
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
  white-space: normal;
}

.save-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 760px) {
  .privacy-grid {
    grid-template-columns: 1fr;
  }

  .privacy-card:last-child {
    grid-column: auto;
  }
}
</style>
