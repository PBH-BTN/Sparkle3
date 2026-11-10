<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { IconApps, IconBug, IconRight } from '@arco-design/web-vue/es/icon'
import { uiApi } from '@/api'
import type { HomeStats } from '@/api/types'
import { useSessionStore } from '@/stores/session'
import { errorMessage } from '@/utils/format'

const router = useRouter()
const session = useSessionStore()
const { t } = useI18n()
const stats = ref<HomeStats | null>(null)
const loading = ref(true)

const banStats = computed(() => [
  { label: t('home.allTime'), value: stats.value?.banHistory.allTime ?? 0 },
  { label: t('home.last30Days'), value: stats.value?.banHistory.last30Days ?? 0 },
  { label: t('home.last7Days'), value: stats.value?.banHistory.last7Days ?? 0 },
  { label: t('home.last24Hours'), value: stats.value?.banHistory.last24Hours ?? 0 },
])

const appStats = computed(() => [
  { label: t('home.last30Days'), value: stats.value?.userApps.last30Days ?? 0 },
  { label: t('home.last14Days'), value: stats.value?.userApps.last14Days ?? 0 },
  { label: t('home.last7Days'), value: stats.value?.userApps.last7Days ?? 0 },
  { label: t('home.last24Hours'), value: stats.value?.userApps.last24Hours ?? 0 },
])

onMounted(async () => {
  try {
    stats.value = await uiApi.home()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-stack">
    <a-alert
      v-if="session.user?.bannedAt"
      type="error"
      show-icon
      :title="t('home.accountPaused')"
    >
      {{ t('home.accountPausedText', { reason: session.user.bannedReason || 'N/A' }) }}
    </a-alert>

    <a-card class="hero-card" :bordered="false">
      <div class="hero-copy">
        <span class="hero-eyebrow">{{ t('home.heroEyebrow') }}</span>
        <h1>{{ t('home.heroTitle') }}</h1>
        <h2>{{ t('home.heroSubtitle') }}</h2>
        <p>{{ t('home.heroText') }}</p>
        <a-button type="primary" size="large" @click="router.push('/userapp')">
          <template #icon><IconApps /></template>
          {{ t('home.createApp') }}
          <IconRight />
        </a-button>
      </div>
      <div class="hero-graphic" aria-hidden="true">
        <span class="hero-ring hero-ring--one" />
        <span class="hero-ring hero-ring--two" />
        <span class="hero-dot hero-dot--one" />
        <span class="hero-dot hero-dot--two" />
        <span class="hero-dot hero-dot--three" />
      </div>
    </a-card>

    <section class="stats-section">
      <h2>{{ t('home.statsTitle') }}</h2>
      <a-spin :loading="loading" class="stats-spin">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :lg="12">
            <a-card class="surface-card stats-card">
              <template #title>
                <span class="stats-title">
                  <span class="stats-icon"><IconBug /></span>
                  {{ t('home.banHistory') }}
                </span>
              </template>
              <a-row :gutter="[12, 12]">
                <a-col v-for="item in banStats" :key="item.label" :xs="12" :md="6">
                  <a-statistic
                    :title="item.label"
                    :value="item.value"
                    :precision="0"
                    show-group-separator
                    :animation="true"
                  />
                </a-col>
              </a-row>
            </a-card>
          </a-col>
          <a-col :xs="24" :lg="12">
            <a-card class="surface-card stats-card">
              <template #title>
                <span class="stats-title">
                  <span class="stats-icon stats-icon--apps"><IconApps /></span>
                  {{ t('home.activeApps') }}
                </span>
              </template>
              <a-row :gutter="[12, 12]">
                <a-col v-for="item in appStats" :key="item.label" :xs="12" :md="6">
                  <a-statistic
                    :title="item.label"
                    :value="item.value"
                    :precision="0"
                    show-group-separator
                    :animation="true"
                  />
                </a-col>
              </a-row>
            </a-card>
          </a-col>
        </a-row>
      </a-spin>
    </section>

    <a-card class="surface-card upcoming-card">
      <a-empty :description="t('home.moreSoon')" />
    </a-card>
  </div>
</template>

<style scoped>
.hero-card {
  position: relative;
  overflow: hidden;
  color: var(--color-white);
  background:
    linear-gradient(120deg, rgb(var(--primary-7)), rgb(var(--primary-5)));
  border-radius: 10px;
}

.hero-card :deep(.arco-card-body) {
  position: relative;
  min-height: 260px;
  padding: 44px;
}

.hero-copy {
  position: relative;
  z-index: 2;
  max-width: 640px;
}

.hero-eyebrow {
  display: inline-flex;
  padding: 3px 10px;
  color: rgb(var(--primary-7));
  font-size: 12px;
  font-weight: 600;
  background: rgb(255 255 255 / 88%);
  border-radius: 20px;
}

.hero-copy h1 {
  margin: 18px 0 2px;
  color: var(--color-white);
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.hero-copy h2 {
  margin: 0 0 14px;
  color: var(--color-white);
  font-size: 20px;
  font-weight: 500;
  opacity: 0.9;
}

.hero-copy p {
  margin: 0 0 28px;
  color: var(--color-white);
  font-size: 15px;
  line-height: 1.7;
  opacity: 0.88;
}

.hero-copy :deep(.arco-btn) {
  color: rgb(var(--primary-6));
  background: white;
  border-color: white;
}

.hero-copy :deep(.arco-btn .arco-icon) {
  color: rgb(var(--primary-6));
}

.hero-graphic {
  position: absolute;
  top: 0;
  right: 0;
  width: 46%;
  height: 100%;
  pointer-events: none;
}

.hero-ring {
  position: absolute;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 50%;
}

.hero-ring--one {
  top: -100px;
  right: -30px;
  width: 380px;
  height: 380px;
}

.hero-ring--two {
  top: -20px;
  right: 130px;
  width: 220px;
  height: 220px;
}

.hero-dot {
  position: absolute;
  width: 10px;
  height: 10px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 0 0 8px rgb(255 255 255 / 10%);
}

.hero-dot--one {
  top: 68px;
  right: 270px;
}

.hero-dot--two {
  right: 70px;
  bottom: 62px;
}

.hero-dot--three {
  top: 120px;
  right: 72px;
  width: 6px;
  height: 6px;
}

.stats-section h2 {
  margin: 12px 0 14px;
  color: var(--color-text-1);
  font-size: 20px;
  font-weight: 600;
}

.stats-spin {
  width: 100%;
}

.stats-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.stats-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
  border-radius: 6px;
}

.stats-icon--apps {
  color: rgb(var(--green-6));
  background: rgb(var(--green-1));
}

.stats-card :deep(.arco-statistic-title) {
  color: var(--color-text-3);
  font-size: 12px;
}

.stats-card :deep(.arco-statistic-value) {
  font-size: 24px;
}

.upcoming-card :deep(.arco-card-body) {
  padding: 28px;
}

@media (max-width: 720px) {
  .hero-card :deep(.arco-card-body) {
    min-height: 0;
    padding: 28px 22px;
  }

  .hero-copy h1 {
    font-size: 32px;
  }

  .hero-copy p {
    margin-bottom: 22px;
  }

  .hero-graphic {
    opacity: 0.45;
  }
}
</style>
