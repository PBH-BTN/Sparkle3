<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconArrowLeft,
  IconCheckCircleFill,
  IconCloseCircleFill,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type { PrivacyLevel } from '@/api/types'
import { errorMessage } from '@/utils/format'

const router = useRouter()
const { t } = useI18n()
const levels = ref<PrivacyLevel[]>([])
const selected = ref<PrivacyLevel['name']>('HIGH')
const loading = ref(true)
const saving = ref(false)

const features = computed(
  () =>
    new Map<
      PrivacyLevel['name'],
      Array<{ label: string; allowed: boolean }>
    >(
      levels.value.map((level) => [
        level.name,
        [
          { label: t('privacy.fullUsername'), allowed: level.allowFullUsername },
          { label: t('privacy.blurredUsername'), allowed: level.allowBlurredUsername },
          { label: t('privacy.originalAvatar'), allowed: level.allowOriginalAvatar },
          { label: t('privacy.blurredAvatar'), allowed: level.allowBlurredAvatar },
        ],
      ]),
    ),
)

onMounted(async () => {
  try {
    const response = await uiApi.privacy()
    levels.value = response.levels
    selected.value = response.current.name
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
})

async function save() {
  saving.value = true
  try {
    const response = await uiApi.updatePrivacy(selected.value)
    selected.value = response.current.name
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
    <PageHeader :title="t('privacy.title')" :description="t('privacy.description')">
      <template #actions>
        <a-button @click="router.push('/user/profile')">
          <template #icon><IconArrowLeft /></template>
          {{ t('common.back') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :loading="loading" class="privacy-spin">
      <a-radio-group v-model="selected" direction="vertical" class="level-group">
        <div
          v-for="level in levels"
          :key="level.name"
          class="level-card"
          :class="{ 'level-card--selected': selected === level.name }"
          role="radio"
          :aria-checked="selected === level.name"
          tabindex="0"
          @click="selected = level.name"
          @keydown.enter.prevent="selected = level.name"
          @keydown.space.prevent="selected = level.name"
        >
          <a-radio :value="level.name">
            <span class="level-title">{{ t(`privacy.levels.${level.name}`) }}</span>
            <span class="level-description">
              {{ t(`privacy.levelDescriptions.${level.name}`) }}
            </span>
          </a-radio>
          <div class="feature-grid">
            <span
              v-for="feature in features.get(level.name)"
              :key="feature.label"
              class="feature"
              :class="{ 'feature--disabled': !feature.allowed }"
            >
              <IconCheckCircleFill v-if="feature.allowed" />
              <IconCloseCircleFill v-else />
              {{ feature.label }}
            </span>
          </div>
        </div>
      </a-radio-group>

      <div class="save-row">
        <a-button
          type="primary"
          size="large"
          :loading="saving"
          :disabled="!selected"
          @click="save"
        >
          {{ t('common.save') }}
        </a-button>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.privacy-page {
  width: min(820px, 100%);
  margin: 0 auto;
}

.privacy-spin {
  display: block;
  width: 100%;
}

.level-group {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 12px;
}

.level-card {
  display: block;
  padding: 18px 20px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.level-card:hover,
.level-card--selected {
  border-color: rgb(var(--primary-6));
}

.level-card--selected {
  box-shadow: 0 0 0 2px rgb(var(--primary-1));
}

.level-title {
  display: block;
  color: var(--color-text-1);
  font-size: 15px;
  font-weight: 600;
}

.level-description {
  display: block;
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 12px;
  white-space: normal;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 16px 0 0 24px;
}

.feature {
  display: flex;
  align-items: center;
  gap: 7px;
  color: rgb(var(--green-6));
  font-size: 13px;
}

.feature--disabled {
  color: var(--color-text-4);
}

.save-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 620px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }

  .level-card {
    padding: 16px;
  }
}
</style>
