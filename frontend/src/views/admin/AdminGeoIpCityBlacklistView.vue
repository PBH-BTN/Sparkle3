<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal, type TableColumnData } from '@arco-design/web-vue'
import {
  IconDelete,
  IconEdit,
  IconEye,
  IconPlus,
  IconRefresh,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  GeoIpCityBlacklistEntry,
  GeoIpCityBlacklistGenerationStatus,
  GeoIpCityBlacklistInput,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatDuration, formatNumber } = useFormatters()
const props = withDefaults(defineProps<{ embedded?: boolean }>(), {
  embedded: false,
})
const loading = ref(true)
const saving = ref(false)
const refreshing = ref(false)
const entries = ref<GeoIpCityBlacklistEntry[]>([])
const status = ref<GeoIpCityBlacklistGenerationStatus | null>(null)
const editorVisible = ref(false)
const previewVisible = ref(false)
const previewLoading = ref(false)
const generatedContent = ref('')
const editingId = ref<number | null>(null)
const form = reactive<GeoIpCityBlacklistInput>({
  countryCode: 'CN',
  province: '',
  city: '',
  enabled: true,
})
let pollTimer: number | undefined

const columns = computed<TableColumnData[]>(() => [
  {
    title: t('admin.geoIpCityBlacklist.location'),
    dataIndex: 'province',
    slotName: 'location',
    width: 240,
  },
  {
    title: t('admin.geoIpCityBlacklist.enabled'),
    dataIndex: 'enabled',
    slotName: 'enabled',
    width: 110,
  },
  {
    title: t('admin.geoIpCityBlacklist.ipv4Count'),
    dataIndex: 'ipv4CidrCount',
    slotName: 'ipv4Count',
    width: 140,
  },
  {
    title: t('admin.geoIpCityBlacklist.ipv6Count'),
    dataIndex: 'ipv6CidrCount',
    slotName: 'ipv6Count',
    width: 140,
  },
  {
    title: t('admin.geoIpCityBlacklist.lastGeneratedAt'),
    dataIndex: 'lastGeneratedAt',
    slotName: 'lastGeneratedAt',
    width: 190,
  },
  {
    title: t('admin.geoIpCityBlacklist.actions'),
    slotName: 'actions',
    width: 240,
  },
])

async function load() {
  loading.value = true
  try {
    const [entryList, generationStatus] = await Promise.all([
      adminApi.geoIpCityBlacklist(),
      adminApi.geoIpCityBlacklistStatus(),
    ])
    entries.value = entryList
    status.value = generationStatus
    if (generationStatus.running) schedulePoll()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function loadStatus() {
  try {
    status.value = await adminApi.geoIpCityBlacklistStatus()
    if (status.value.running) {
      schedulePoll()
    } else {
      entries.value = await adminApi.geoIpCityBlacklist()
    }
  } catch {
    // Keep the last status visible while the backend is temporarily unavailable.
  }
}

function schedulePoll() {
  window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(loadStatus, 1800)
}

function openCreate() {
  editingId.value = null
  form.countryCode = 'CN'
  form.province = ''
  form.city = ''
  form.enabled = true
  editorVisible.value = true
}

function openEdit(entry: GeoIpCityBlacklistEntry) {
  editingId.value = entry.id
  form.countryCode = entry.countryCode
  form.province = entry.province
  form.city = entry.city
  form.enabled = entry.enabled
  editorVisible.value = true
}

async function save() {
  if (!form.countryCode.trim() || !form.province.trim() || !form.city.trim()) {
    Message.warning(t('admin.geoIpCityBlacklist.required'))
    return
  }
  saving.value = true
  try {
    const payload: GeoIpCityBlacklistInput = {
      countryCode: form.countryCode.trim().toUpperCase(),
      province: form.province.trim(),
      city: form.city.trim(),
      enabled: form.enabled,
    }
    if (editingId.value == null) {
      await adminApi.createGeoIpCityBlacklistEntry(payload)
    } else {
      await adminApi.updateGeoIpCityBlacklistEntry(editingId.value, payload)
    }
    editorVisible.value = false
    Message.success(t('admin.geoIpCityBlacklist.saved'))
    await load()
    schedulePoll()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(entry: GeoIpCityBlacklistEntry, value: string | number | boolean) {
  try {
    await adminApi.updateGeoIpCityBlacklistEntry(entry.id, {
      countryCode: entry.countryCode,
      province: entry.province,
      city: entry.city,
      enabled: Boolean(value),
    })
    Message.success(t('admin.geoIpCityBlacklist.saved'))
    await load()
    schedulePoll()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
    await load()
  }
}

function remove(entry: GeoIpCityBlacklistEntry) {
  Modal.confirm({
    title: t('admin.geoIpCityBlacklist.deleteTitle'),
    content: t('admin.geoIpCityBlacklist.deleteConfirm', {
      location: `${entry.countryCode} / ${entry.province} / ${entry.city}`,
    }),
    okButtonProps: { status: 'danger' },
    async onOk() {
      try {
        await adminApi.deleteGeoIpCityBlacklistEntry(entry.id)
        Message.success(t('admin.geoIpCityBlacklist.deleted'))
        await load()
        schedulePoll()
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
        throw error
      }
    },
  })
}

async function preview(entry: GeoIpCityBlacklistEntry) {
  previewVisible.value = true
  previewLoading.value = true
  generatedContent.value = ''
  try {
    const response = await adminApi.generatedGeoIpCityBlacklistContent(entry.id)
    generatedContent.value = response.content
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    previewLoading.value = false
  }
}

async function refreshNow() {
  refreshing.value = true
  try {
    status.value = await adminApi.refreshGeoIpCityBlacklist()
    Message.success(t('admin.geoIpCityBlacklist.refreshStarted'))
    schedulePoll()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    refreshing.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => window.clearTimeout(pollTimer))
</script>

<template>
  <div
    class="page-stack geoip-page"
    :class="{ 'geoip-page--embedded': props.embedded }"
  >
    <PageHeader
      v-if="!props.embedded"
      :title="t('admin.geoIpCityBlacklist.title')"
      :description="t('admin.geoIpCityBlacklist.description')"
    >
      <template #actions>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.geoIpCityBlacklist.create') }}
        </a-button>
        <a-button :loading="refreshing || status?.running" @click="refreshNow">
          <template #icon><IconRefresh /></template>
          {{ t('admin.geoIpCityBlacklist.regenerate') }}
        </a-button>
      </template>
    </PageHeader>
    <div v-else class="embedded-toolbar">
      <p>{{ t('admin.geoIpCityBlacklist.description') }}</p>
      <a-space>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.geoIpCityBlacklist.create') }}
        </a-button>
        <a-button :loading="refreshing || status?.running" @click="refreshNow">
          <template #icon><IconRefresh /></template>
          {{ t('admin.geoIpCityBlacklist.regenerate') }}
        </a-button>
      </a-space>
    </div>

    <a-alert type="info" show-icon>
      {{ t('admin.geoIpCityBlacklist.generationHint') }}
    </a-alert>

    <a-card class="surface-card">
      <div class="status-row">
        <a-tag :color="status?.running ? 'arcoblue' : 'green'">
          {{
            status?.running
              ? t('admin.geoIpCityBlacklist.generating')
              : t('admin.geoIpCityBlacklist.idle')
          }}
        </a-tag>
        <span>
          {{ t('admin.geoIpCityBlacklist.enabledEntries') }}:
          {{ formatNumber(status?.enabledEntries) }}
        </span>
        <span>
          {{ t('admin.geoIpCityBlacklist.ipv4Cidrs') }}:
          {{ formatNumber(status?.ipv4CidrCount) }}
        </span>
        <span>
          {{ t('admin.geoIpCityBlacklist.ipv6Cidrs') }}:
          {{ formatNumber(status?.ipv6CidrCount) }}
        </span>
        <span>
          {{ t('admin.geoIpCityBlacklist.lastFinished') }}:
          {{ formatDate(status?.lastFinishedAt) }}
        </span>
        <span>
          {{ t('admin.geoIpCityBlacklist.duration') }}:
          {{ formatDuration(status?.lastDurationMs) }}
        </span>
      </div>
      <a-alert v-if="status?.lastError" type="error" show-icon class="status-error">
        {{ status.lastError }}
      </a-alert>
    </a-card>

    <a-spin :loading="loading" class="table-spin">
      <a-card class="surface-card">
        <a-table
          :columns="columns"
          :data="entries"
          :pagination="false"
          :scroll="{ x: 1060 }"
          row-key="id"
          size="small"
          stripe
        >
          <template #location="{ record }">
            <div class="location-cell">
              <a-tag color="arcoblue">{{ record.countryCode }}</a-tag>
              <strong>{{ record.province }}</strong>
              <span>/</span>
              <span>{{ record.city }}</span>
            </div>
          </template>
          <template #enabled="{ record }">
            <a-switch
              :model-value="record.enabled"
              @change="(value) => toggleEnabled(record, value)"
            />
          </template>
          <template #ipv4Count="{ record }">
            {{ formatNumber(record.ipv4CidrCount) }}
          </template>
          <template #ipv6Count="{ record }">
            {{ formatNumber(record.ipv6CidrCount) }}
          </template>
          <template #lastGeneratedAt="{ record }">
            {{ formatDate(record.lastGeneratedAt) }}
          </template>
          <template #actions="{ record }">
            <a-space>
              <a-button size="small" type="text" @click="preview(record)">
                <template #icon><IconEye /></template>
                {{ t('admin.geoIpCityBlacklist.preview') }}
              </a-button>
              <a-button size="small" type="text" @click="openEdit(record)">
                <template #icon><IconEdit /></template>
                {{ t('admin.common.edit') }}
              </a-button>
              <a-button size="small" type="text" status="danger" @click="remove(record)">
                <template #icon><IconDelete /></template>
                {{ t('admin.common.delete') }}
              </a-button>
            </a-space>
          </template>
        </a-table>
      </a-card>
    </a-spin>

    <a-modal
      v-model:visible="editorVisible"
      :title="
        editingId == null
          ? t('admin.geoIpCityBlacklist.createTitle')
          : t('admin.geoIpCityBlacklist.editTitle')
      "
      :confirm-loading="saving"
      @ok="save"
    >
      <a-form :model="form" layout="vertical">
        <a-row :gutter="[12, 0]">
          <a-col :span="7">
            <a-form-item :label="t('admin.geoIpCityBlacklist.countryCode')">
              <a-input v-model="form.countryCode" maxlength="2" />
            </a-form-item>
          </a-col>
          <a-col :span="17">
            <a-form-item :label="t('admin.geoIpCityBlacklist.province')">
              <a-input
                v-model="form.province"
                maxlength="100"
                :placeholder="t('admin.geoIpCityBlacklist.provincePlaceholder')"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item :label="t('admin.geoIpCityBlacklist.city')">
          <a-input
            v-model="form.city"
            maxlength="100"
            :placeholder="t('admin.geoIpCityBlacklist.cityPlaceholder')"
          />
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model="form.enabled">
            {{ t('admin.geoIpCityBlacklist.enabled') }}
          </a-checkbox>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="previewVisible"
      :title="t('admin.geoIpCityBlacklist.previewTitle')"
      :footer="false"
      width="min(900px, 92vw)"
    >
      <a-spin :loading="previewLoading" class="preview-spin">
        <pre class="generated-content">{{ generatedContent || t('admin.geoIpCityBlacklist.emptyContent') }}</pre>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.geoip-page {
  width: 100%;
  min-width: 0;
}

.geoip-page--embedded {
  width: 100%;
}

.embedded-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.embedded-toolbar p {
  max-width: 720px;
  margin: 0;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.7;
}

.status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 22px;
  align-items: center;
  color: var(--color-text-3);
  font-size: 12px;
}

.status-error {
  margin-top: 12px;
}

.table-spin,
.preview-spin {
  display: block;
  width: 100%;
}

.location-cell {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.location-cell strong {
  color: var(--color-text-1);
}

.generated-content {
  max-height: 62vh;
  padding: 14px;
  margin: 0;
  overflow: auto;
  color: var(--color-text-1);
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
}

@media (max-width: 640px) {
  .embedded-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .status-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
