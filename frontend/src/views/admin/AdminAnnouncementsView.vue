<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconEdit,
  IconNotification,
  IconPlus,
  IconRefresh,
  IconSend,
  IconStop,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  Announcement,
  AnnouncementInput,
  AnnouncementLevel,
  AnnouncementPlacement,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t, locale } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(true)
const saving = ref(false)
const editingId = ref<number | null>(null)
const editorVisible = ref(false)
const announcements = ref<Announcement[]>([])

const emptyEditor = (): AnnouncementInput => ({
  level: 'BLUE',
  placement: 'GLOBAL_TOP',
  titleZh: '',
  titleEn: '',
  contentZh: '',
  contentEn: '',
  enabled: true,
  dismissible: true,
  sortOrder: 0,
  startsAt: null,
  endsAt: null,
})

const editor = reactive<AnnouncementInput>(emptyEditor())
const startsAtValue = computed<string | undefined>({
  get: () => editor.startsAt ?? undefined,
  set: (value) => {
    editor.startsAt = value || null
  },
})
const endsAtValue = computed<string | undefined>({
  get: () => editor.endsAt ?? undefined,
  set: (value) => {
    editor.endsAt = value || null
  },
})

const columns = computed(() => [
  { title: t('admin.announcements.status'), slotName: 'status', width: 100 },
  { title: t('admin.announcements.level'), slotName: 'level', width: 100 },
  { title: t('admin.announcements.placement'), slotName: 'placement', width: 160 },
  { title: t('admin.announcements.titleField'), slotName: 'titleField', width: 240 },
  { title: t('admin.announcements.schedule'), slotName: 'schedule', width: 260 },
  { title: t('admin.announcements.sortOrder'), dataIndex: 'sortOrder', width: 90 },
  { title: t('admin.announcements.updatedAt'), slotName: 'updatedAt', width: 180 },
  { title: t('admin.common.actions'), slotName: 'actions', width: 230 },
])

const previewTitle = computed(() =>
  locale.value.startsWith('en')
    ? editor.titleEn || editor.titleZh
    : editor.titleZh || editor.titleEn,
)
const previewMessage = computed(() =>
  locale.value.startsWith('en')
    ? editor.contentEn || editor.contentZh
    : editor.contentZh || editor.contentEn,
)
const previewType = computed(() => alertType(editor.level))

function alertType(level: AnnouncementLevel) {
  return {
    GREEN: 'success',
    YELLOW: 'warning',
    BLUE: 'info',
    RED: 'error',
  }[level] as 'success' | 'warning' | 'info' | 'error'
}

function levelLabel(level: AnnouncementLevel) {
  return t(`admin.announcements.levels.${level}`)
}

function placementLabel(placement: AnnouncementPlacement) {
  return t(`admin.announcements.placements.${placement}`)
}

function levelColor(level: AnnouncementLevel) {
  return {
    GREEN: 'green',
    YELLOW: 'orange',
    BLUE: 'arcoblue',
    RED: 'red',
  }[level] as 'green' | 'orange' | 'arcoblue' | 'red'
}

function displayTitle(announcement: Announcement) {
  return locale.value.startsWith('en')
    ? announcement.titleEn || announcement.titleZh
    : announcement.titleZh || announcement.titleEn
}

function publicationStatus(announcement: Announcement) {
  if (!announcement.enabled) return 'disabled'
  const now = Date.now()
  if (announcement.startsAt && new Date(announcement.startsAt).getTime() > now) {
    return 'scheduled'
  }
  if (announcement.endsAt && new Date(announcement.endsAt).getTime() <= now) {
    return 'expired'
  }
  return 'active'
}

function statusLabel(announcement: Announcement) {
  return t(`admin.announcements.publicationStatus.${publicationStatus(announcement)}`)
}

function statusColor(announcement: Announcement) {
  return {
    active: 'green',
    scheduled: 'arcoblue',
    expired: 'gray',
    disabled: 'orange',
  }[publicationStatus(announcement)] as 'green' | 'arcoblue' | 'gray' | 'orange'
}

function scheduleText(announcement: Announcement) {
  if (!announcement.startsAt && !announcement.endsAt) {
    return t('admin.announcements.alwaysVisible')
  }
  return t('admin.announcements.scheduleRange', {
    start: announcement.startsAt ? formatDate(announcement.startsAt) : t('admin.announcements.immediate'),
    end: announcement.endsAt ? formatDate(announcement.endsAt) : t('admin.announcements.neverEnds'),
  })
}

function toInput(announcement: Announcement): AnnouncementInput {
  return {
    level: announcement.level,
    placement: announcement.placement,
    titleZh: announcement.titleZh,
    titleEn: announcement.titleEn,
    contentZh: announcement.contentZh,
    contentEn: announcement.contentEn,
    enabled: announcement.enabled,
    dismissible: announcement.dismissible,
    sortOrder: announcement.sortOrder,
    startsAt: announcement.startsAt,
    endsAt: announcement.endsAt,
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(editor, emptyEditor())
  editorVisible.value = true
}

function openEdit(announcement: Announcement) {
  editingId.value = announcement.id
  Object.assign(editor, toInput(announcement))
  editorVisible.value = true
}

async function load() {
  loading.value = true
  try {
    announcements.value = await adminApi.announcements()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!(editor.titleZh.trim() || editor.titleEn.trim())) {
    Message.warning(t('admin.announcements.titleRequired'))
    return
  }
  if (!(editor.contentZh.trim() || editor.contentEn.trim())) {
    Message.warning(t('admin.announcements.contentRequired'))
    return
  }
  if (
    editor.startsAt &&
    editor.endsAt &&
    new Date(editor.endsAt).getTime() <= new Date(editor.startsAt).getTime()
  ) {
    Message.warning(t('admin.announcements.invalidSchedule'))
    return
  }
  saving.value = true
  try {
    if (editingId.value == null) {
      await adminApi.createAnnouncement(editor)
      Message.success(t('admin.announcements.created'))
    } else {
      await adminApi.updateAnnouncement(editingId.value, editor)
      Message.success(t('admin.announcements.updated'))
    }
    editorVisible.value = false
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

async function setPublished(announcement: Announcement, enabled: boolean) {
  try {
    await adminApi.updateAnnouncement(announcement.id, {
      ...toInput(announcement),
      enabled,
    })
    Message.success(enabled ? t('admin.announcements.published') : t('admin.announcements.unpublished'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

async function remove(announcement: Announcement) {
  try {
    await adminApi.deleteAnnouncement(announcement.id)
    Message.success(t('admin.common.deleted'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader
      :title="t('admin.announcements.title')"
      :description="t('admin.announcements.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.announcements.create') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      <template #title>{{ t('admin.announcements.bannerHintTitle') }}</template>
      {{ t('admin.announcements.bannerHint') }}
    </a-alert>

    <a-card class="surface-card">
      <a-table
        :loading="loading"
        :columns="columns"
        :data="announcements"
        :pagination="false"
        row-key="id"
      >
        <template #status="{ record }">
          <a-tag :color="statusColor(record)">{{ statusLabel(record) }}</a-tag>
        </template>
        <template #level="{ record }">
          <a-tag :color="levelColor(record.level)">{{ levelLabel(record.level) }}</a-tag>
        </template>
        <template #placement="{ record }">
          <a-tag color="gray">{{ placementLabel(record.placement) }}</a-tag>
        </template>
        <template #titleField="{ record }">
          <div class="title-cell">
            <strong>{{ displayTitle(record) || t('common.notAvailable') }}</strong>
            <span>#{{ record.id }}</span>
          </div>
        </template>
        <template #schedule="{ record }">
          <span class="schedule-text">{{ scheduleText(record) }}</span>
        </template>
        <template #updatedAt="{ record }">
          {{ formatDate(record.updatedAt) }}
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button size="small" @click="openEdit(record)">
              <template #icon><IconEdit /></template>
              {{ t('admin.common.edit') }}
            </a-button>
            <a-button
              v-if="record.enabled"
              size="small"
              status="warning"
              @click="setPublished(record, false)"
            >
              <template #icon><IconStop /></template>
              {{ t('admin.announcements.unpublish') }}
            </a-button>
            <a-button v-else size="small" type="primary" @click="setPublished(record, true)">
              <template #icon><IconSend /></template>
              {{ t('admin.announcements.publish') }}
            </a-button>
            <a-popconfirm
              :content="t('admin.announcements.deleteConfirm')"
              @ok="remove(record)"
            >
              <a-button size="small" status="danger">
                {{ t('admin.common.delete') }}
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
        <template #empty>
          <a-empty :description="t('admin.announcements.noData')" />
        </template>
      </a-table>
    </a-card>

    <a-drawer
      v-model:visible="editorVisible"
      :width="680"
      :footer="false"
      unmount-on-close
    >
      <template #title>
        <span class="drawer-title">
          <IconNotification />
          {{ editingId == null ? t('admin.announcements.create') : t('admin.announcements.edit') }}
        </span>
      </template>

      <a-form :model="editor" layout="vertical" @submit="save">
        <div class="editor-grid">
          <a-form-item :label="t('admin.announcements.level')" required>
            <a-select v-model="editor.level">
              <a-option value="GREEN">{{ t('admin.announcements.levels.GREEN') }}</a-option>
              <a-option value="YELLOW">{{ t('admin.announcements.levels.YELLOW') }}</a-option>
              <a-option value="BLUE">{{ t('admin.announcements.levels.BLUE') }}</a-option>
              <a-option value="RED">{{ t('admin.announcements.levels.RED') }}</a-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="t('admin.announcements.placement')" required>
            <a-select v-model="editor.placement">
              <a-option value="GLOBAL_TOP">
                {{ t('admin.announcements.placements.GLOBAL_TOP') }}
              </a-option>
              <a-option value="HOME_HERO">
                {{ t('admin.announcements.placements.HOME_HERO') }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="t('admin.announcements.sortOrder')">
            <a-input-number v-model="editor.sortOrder" :min="-10000" :max="10000" />
          </a-form-item>
          <a-form-item :label="t('admin.announcements.publishNow')">
            <a-switch v-model="editor.enabled" />
          </a-form-item>
          <a-form-item :label="t('admin.announcements.dismissible')">
            <a-switch v-model="editor.dismissible" />
          </a-form-item>
        </div>

        <a-divider orientation="left">{{ t('admin.announcements.chinese') }}</a-divider>
        <a-form-item :label="t('admin.announcements.titleZh')">
          <a-input v-model="editor.titleZh" :max-length="500" show-word-limit />
        </a-form-item>
        <a-form-item :label="t('admin.announcements.contentZh')">
          <a-textarea
            v-model="editor.contentZh"
            :auto-size="{ minRows: 3, maxRows: 7 }"
            :max-length="20000"
          />
        </a-form-item>

        <a-divider orientation="left">{{ t('admin.announcements.english') }}</a-divider>
        <a-form-item :label="t('admin.announcements.titleEn')">
          <a-input v-model="editor.titleEn" :max-length="500" show-word-limit />
        </a-form-item>
        <a-form-item :label="t('admin.announcements.contentEn')">
          <a-textarea
            v-model="editor.contentEn"
            :auto-size="{ minRows: 3, maxRows: 7 }"
            :max-length="20000"
          />
        </a-form-item>

        <a-divider orientation="left">{{ t('admin.announcements.schedule') }}</a-divider>
        <div class="editor-grid">
          <a-form-item :label="t('admin.announcements.startsAt')">
            <a-date-picker
              v-model="startsAtValue"
              show-time
              value-format="YYYY-MM-DDTHH:mm:ssZ"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item :label="t('admin.announcements.endsAt')">
            <a-date-picker
              v-model="endsAtValue"
              show-time
              value-format="YYYY-MM-DDTHH:mm:ssZ"
              style="width: 100%"
            />
          </a-form-item>
        </div>
        <p class="form-hint">{{ t('admin.announcements.scheduleHint') }}</p>

        <a-divider orientation="left">{{ t('admin.announcements.preview') }}</a-divider>
        <a-alert :type="previewType" show-icon>
          <template #title>{{ previewTitle || t('admin.announcements.previewTitle') }}</template>
          <span class="preview-content">
            {{ previewMessage || t('admin.announcements.previewMessage') }}
          </span>
        </a-alert>

        <div class="drawer-actions">
          <a-button @click="editorVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button type="primary" :loading="saving" @click="save">
            {{ t('admin.common.save') }}
          </a-button>
        </div>
      </a-form>
    </a-drawer>
  </div>
</template>

<style scoped>
.title-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.title-cell span {
  color: var(--color-text-3);
  font-size: 11px;
}

.schedule-text {
  color: var(--color-text-2);
  font-size: 12px;
  line-height: 1.5;
}

.drawer-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-hint {
  margin: -4px 0 20px;
  color: var(--color-text-3);
  font-size: 12px;
}

.preview-content {
  white-space: pre-line;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

@media (max-width: 620px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
