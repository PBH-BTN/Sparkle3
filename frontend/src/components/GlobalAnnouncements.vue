<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { uiApi } from '@/api'
import type { Announcement, AnnouncementPlacement } from '@/api/types'

const DISMISSED_STORAGE_KEY = 'sparkle:announcement-dismissals'
const props = withDefaults(
  defineProps<{
    placement?: AnnouncementPlacement
    variant?: 'global' | 'hero'
  }>(),
  {
    placement: 'GLOBAL_TOP',
    variant: 'global',
  },
)
const { locale } = useI18n()
const announcements = ref<Announcement[]>([])
const dismissed = ref<Record<string, number>>(readDismissals())

const visibleAnnouncements = computed(() =>
  announcements.value
    .filter((announcement) => !dismissed.value[dismissKey(announcement)])
    .map((announcement) => {
      const english = locale.value.startsWith('en')
      return {
        ...announcement,
        title:
          (english ? announcement.titleEn : announcement.titleZh) ||
          (english ? announcement.titleZh : announcement.titleEn),
        message:
          (english ? announcement.contentEn : announcement.contentZh) ||
          (english ? announcement.contentZh : announcement.contentEn),
        alertType: alertType(announcement.level),
      }
    }),
)

function alertType(level: Announcement['level']) {
  return {
    GREEN: 'success',
    YELLOW: 'warning',
    BLUE: 'info',
    RED: 'error',
  }[level] as 'success' | 'warning' | 'info' | 'error'
}

function dismissKey(announcement: Announcement) {
  return `${announcement.id}:${announcement.updatedAt}`
}

function dismiss(announcement: Announcement) {
  const now = Date.now()
  dismissed.value = {
    ...dismissed.value,
    [dismissKey(announcement)]: now,
  }
  const cutoff = now - 90 * 24 * 60 * 60 * 1000
  dismissed.value = Object.fromEntries(
    Object.entries(dismissed.value).filter(([, timestamp]) => timestamp >= cutoff),
  )
  try {
    localStorage.setItem(DISMISSED_STORAGE_KEY, JSON.stringify(dismissed.value))
  } catch {
    // Dismissal still applies for the current page when browser storage is unavailable.
  }
}

function readDismissals(): Record<string, number> {
  try {
    const value = JSON.parse(localStorage.getItem(DISMISSED_STORAGE_KEY) ?? '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    const result: Record<string, number> = {}
    for (const [key, timestamp] of Object.entries(value)) {
      if (typeof timestamp === 'number') result[key] = timestamp
    }
    return result
  } catch {
    return {}
  }
}

onMounted(async () => {
  try {
    announcements.value = await uiApi.announcements(props.placement)
  } catch {
    announcements.value = []
  }
})
</script>

<template>
  <section
    v-if="visibleAnnouncements.length"
    :class="['global-announcements', `global-announcements--${variant}`]"
    aria-live="polite"
  >
    <div class="global-announcements-inner">
      <a-alert
        v-for="announcement in visibleAnnouncements"
        :key="announcement.id"
        :type="announcement.alertType"
        :closable="announcement.dismissible"
        show-icon
        class="global-announcement"
        @close="dismiss(announcement)"
      >
        <template #title>
          <strong>{{ announcement.title }}</strong>
        </template>
        <span class="announcement-message">{{ announcement.message }}</span>
      </a-alert>
    </div>
  </section>
</template>

<style scoped>
.global-announcements {
  position: relative;
  z-index: 110;
  padding: 8px 0;
  background: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border-2);
}

.global-announcements-inner {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: min(1440px, calc(100% - 40px));
  margin: 0 auto;
}

.global-announcement {
  border-radius: var(--app-radius);
}

.global-announcements--hero {
  z-index: 1;
  padding: 0;
  background: transparent;
  border-bottom: 0;
}

.global-announcements--hero .global-announcements-inner {
  width: 100%;
}

.announcement-message {
  white-space: pre-line;
}

@media (max-width: 720px) {
  .global-announcements-inner {
    width: calc(100% - 24px);
  }
}
</style>
