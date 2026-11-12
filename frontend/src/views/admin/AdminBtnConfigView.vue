<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconCheckCircle,
  IconRefresh,
  IconSave,
  IconSettings,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type { BtnConfigField, BtnConfigView } from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatDuration } = useFormatters()

const fields = ref<BtnConfigField[]>([])
const form = reactive<Record<string, string>>({})
const loading = ref(true)
const saving = ref(false)
const updatedAt = ref<string | null>(null)

const durationFields = new Set([
  'sparkle.ping.reconfigure.interval',
  'sparkle.ping.reconfigure.random-initial-delay',
  'sparkle.ping.sync-banhistory.interval',
  'sparkle.ping.sync-banhistory.random-initial-delay',
  'sparkle.ping.sync-swarm.interval',
  'sparkle.ping.sync-swarm.random-initial-delay',
  'sparkle.ping.sync-swarm.data-retention-time',
  'sparkle.ping.rule-ip-denylist.interval',
  'sparkle.ping.rule-ip-denylist.random-initial-delay',
  'sparkle.ping.rule-ip-allowlist.interval',
  'sparkle.ping.rule-ip-allowlist.random-initial-delay',
  'sparkle.ping.rule-peer-identity.interval',
  'sparkle.ping.rule-peer-identity.random-initial-delay',
  'sparkle.ping.heartbeat.interval',
  'sparkle.ping.heartbeat.random-initial-delay',
  'sparkle.ping.heartbeat.cleanup-before',
  'sparkle.ping.heartbeat.queue.flush-interval-ms',
  'sparkle.ping.heartbeat.queue.initial-delay-ms',
])

const sections = computed(() => {
  const grouped = new Map<string, BtnConfigField[]>()
  for (const field of fields.value) {
    const section = grouped.get(field.section)
    if (section) {
      section.push(field)
    } else {
      grouped.set(field.section, [field])
    }
  }
  return [...grouped.entries()].map(([id, sectionFields]) => ({
    id,
    fields: sectionFields,
  }))
})

const hasEnvironmentOverrides = computed(() =>
  fields.value.some((field) => field.environmentOverride),
)

async function load() {
  loading.value = true
  try {
    applyConfig(await adminApi.btnConfig())
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function applyConfig(config: BtnConfigView) {
  fields.value = config.fields
  updatedAt.value = config.updatedAt
  for (const key of Object.keys(form)) {
    delete form[key]
  }
  for (const field of config.fields) {
    form[field.key] = field.value ?? field.defaultValue
  }
}

async function save() {
  saving.value = true
  try {
    const result = await adminApi.updateBtnConfig(
      Object.fromEntries(fields.value.map((field) => [field.key, form[field.key] ?? ''])),
    )
    applyConfig(result)
    Message.success(t('admin.btn.saved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

function updateInteger(field: BtnConfigField, value: number | undefined) {
  form[field.key] = value == null ? '' : String(value)
}

function updateBoolean(field: BtnConfigField, value: boolean | string | number) {
  form[field.key] = Boolean(value) ? 'true' : 'false'
}

function sectionTitle(section: string) {
  return t(`admin.btn.sections.${section}`)
}

function sectionDescription(section: string) {
  return t(`admin.btn.sectionDescriptions.${section}`)
}

function fieldLabel(field: BtnConfigField) {
  return t(`admin.btn.fields.${field.field}`)
}

function fieldHelp(field: BtnConfigField) {
  const key = `admin.btn.help.${field.field}`
  const translated = t(key)
  return translated === key ? '' : translated
}

function durationHint(field: BtnConfigField) {
  if (!durationFields.has(field.key)) return ''
  const milliseconds = Number(form[field.key])
  if (!Number.isFinite(milliseconds)) return ''
  return t('admin.btn.durationHint', {
    duration: formatDuration(Math.max(0, milliseconds)),
  })
}
</script>

<template>
  <div class="page-stack btn-config-page">
    <PageHeader :title="t('admin.btn.title')" :description="t('admin.btn.description')">
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
        <a-button type="primary" :loading="saving" :disabled="loading" @click="save">
          <template #icon><IconSave /></template>
          {{ t('admin.common.save') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      <template #title>
        <IconCheckCircle />
        {{ t('admin.btn.hotReloadTitle') }}
      </template>
      {{ t('admin.btn.hotReloadText') }}
    </a-alert>

    <a-alert v-if="hasEnvironmentOverrides" type="warning" show-icon>
      {{ t('admin.btn.environmentOverrideNotice') }}
    </a-alert>

    <a-spin :loading="loading" class="btn-config-spin">
      <a-form :model="form" layout="vertical">
        <a-card
          v-for="(section, sectionIndex) in sections"
          :key="section.id"
          class="surface-card config-section-card"
        >
          <template #title>
            <span class="section-title">
              <span class="section-index">{{ sectionIndex + 1 }}</span>
              <IconSettings />
              {{ sectionTitle(section.id) }}
            </span>
          </template>
          <template #extra>
            <a-tag color="green" size="small">{{ t('admin.btn.hotReload') }}</a-tag>
          </template>

          <p class="section-description">{{ sectionDescription(section.id) }}</p>

          <div class="config-field-grid">
            <a-form-item
              v-for="field in section.fields"
              :key="field.key"
              class="config-field"
            >
              <template #label>
                <span class="field-label">
                  {{ fieldLabel(field) }}
                  <a-tag v-if="field.environmentOverride" color="orange" size="small">
                    {{ t('admin.btn.environmentOverride') }}
                  </a-tag>
                </span>
              </template>

              <a-switch
                v-if="field.valueType === 'boolean'"
                :model-value="form[field.key] === 'true'"
                @change="(value) => updateBoolean(field, value)"
              />
              <a-input-number
                v-else-if="field.valueType === 'integer'"
                :model-value="form[field.key] === '' ? undefined : Number(form[field.key])"
                :min="field.min ?? undefined"
                :max="field.max ?? undefined"
                :precision="0"
                hide-button
                @change="(value) => updateInteger(field, value)"
              />
              <a-input
                v-else
                v-model="form[field.key]"
                :placeholder="field.defaultValue"
                :max-length="field.valueType === 'cron' ? 128 : 2048"
              />

              <template #extra>
                <div class="field-meta">
                  <span v-if="fieldHelp(field)">{{ fieldHelp(field) }}</span>
                  <span v-if="durationHint(field)" class="duration-hint">
                    {{ durationHint(field) }}
                  </span>
                  <code>{{ field.key }}</code>
                </div>
              </template>
            </a-form-item>
          </div>
        </a-card>
      </a-form>
    </a-spin>

    <p class="last-updated">
      {{ t('admin.btn.updatedAt', { time: formatDate(updatedAt) }) }}
    </p>
  </div>
</template>

<style scoped>
.btn-config-page {
  width: min(1180px, 100%);
}

.btn-config-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.config-section-card {
  overflow: hidden;
}

.section-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.section-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  color: rgb(var(--primary-6));
  font-size: 12px;
  font-weight: 600;
  background: var(--color-primary-light-1);
  border-radius: 50%;
}

.section-description {
  margin: -4px 0 18px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.7;
}

.config-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.config-field {
  min-width: 0;
}

.config-field :deep(.arco-input-number) {
  width: 100%;
}

.field-label {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.field-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--color-text-3);
  font-size: 11px;
  line-height: 1.55;
}

.field-meta code {
  overflow-wrap: anywhere;
  color: var(--color-text-3);
  font-size: 10px;
  opacity: 0.82;
}

.duration-hint {
  color: rgb(var(--primary-6));
}

.last-updated {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  text-align: right;
}

@media (max-width: 760px) {
  .config-field-grid {
    grid-template-columns: 1fr;
  }

  .last-updated {
    text-align: left;
  }
}
</style>
