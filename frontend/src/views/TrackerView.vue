<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconCheck,
  IconCloud,
  IconCopy,
  IconInfoCircle,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type { TrackerResponse } from '@/api/types'
import { errorMessage } from '@/utils/format'

const { t } = useI18n()
const tracker = ref<TrackerResponse | null>(null)
const loading = ref(true)
const dashboardAvailable = ref(true)

onMounted(async () => {
  try {
    tracker.value = await uiApi.tracker()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
})

async function copyAnnounceUrl() {
  if (!tracker.value) return
  await navigator.clipboard.writeText(tracker.value.announceUrl)
  Message.success(t('tracker.copied'))
}
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('tracker.title')" :description="t('tracker.description')" />

    <a-spin :loading="loading" class="tracker-spin">
      <template v-if="tracker">
        <a-card class="surface-card">
          <template #title>
            <span class="card-title">
              <IconCloud />
              {{ t('tracker.announceUrl') }}
            </span>
          </template>
          <div class="announce-row">
            <div class="announce-label">{{ t('tracker.announceUrl') }}</div>
            <div class="announce-value">
              <code>{{ tracker.announceUrl }}</code>
              <a-button type="primary" @click="copyAnnounceUrl">
                <template #icon><IconCopy /></template>
                {{ t('common.copy') }}
              </a-button>
            </div>
          </div>
        </a-card>

        <div class="tracker-grid">
          <a-card class="surface-card">
            <template #title>
              <span class="card-title">
                <IconInfoCircle />
                {{ t('tracker.introduction') }}
              </span>
            </template>
            <p class="intro-text">{{ t('tracker.introText') }}</p>
            <h3>{{ t('tracker.features') }}</h3>
            <ul class="feature-list">
              <li><IconCheck />{{ t('tracker.feature1') }}</li>
              <li><IconCheck />{{ t('tracker.feature2') }}</li>
              <li><IconCheck />{{ t('tracker.feature3') }}</li>
              <li><IconCheck />{{ t('tracker.feature4') }}</li>
            </ul>
          </a-card>

          <a-card class="surface-card">
            <template #title>
              <span class="card-title">
                <IconCloud />
                {{ t('tracker.statistics') }}
              </span>
            </template>
            <div v-if="dashboardAvailable" class="dashboard-frame">
              <img
                :src="tracker.dashboardUrl"
                alt="Tracker dashboard"
                loading="lazy"
                @error="dashboardAvailable = false"
              />
            </div>
            <a-empty v-else :description="t('tracker.dashboardUnavailable')" />
          </a-card>
        </div>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.tracker-spin {
  display: block;
  width: 100%;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.announce-label {
  margin-bottom: 8px;
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 600;
}

.announce-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 16px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.announce-value code {
  overflow-wrap: anywhere;
  font-size: 14px;
}

.tracker-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.4fr);
  gap: 16px;
  margin-top: 16px;
}

.intro-text {
  margin: 0 0 24px;
  color: var(--color-text-2);
  line-height: 1.75;
}

h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0;
  margin: 0;
  color: var(--color-text-2);
  list-style: none;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 9px;
}

.feature-list svg {
  color: rgb(var(--green-6));
}

.dashboard-frame {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 260px;
  overflow: auto;
  background: var(--color-fill-1);
  border-radius: 6px;
}

.dashboard-frame img {
  display: block;
  max-width: 100%;
  height: auto;
}

@media (max-width: 980px) {
  .tracker-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .announce-value {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
