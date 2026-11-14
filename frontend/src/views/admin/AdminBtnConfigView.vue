<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
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
const loadError = ref('')

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
  'sparkle.query.query-ip.bans-counting-duration',
  'sparkle.query.query-ip.swarms-counting-duration',
  'sparkle.query.query-ip.heartbeat-query-duration',
  'sparkle.query.query-ip.traffic-measure-duration',
  'sparkle.query.query-ip.torrents-counting-duration',
  'sparkle.admin.user-ip-warning.duration',
  'sparkle.measure.expired-after',
  'sparkle.user-avatar.cache-duration',
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
  loadError.value = ''
  try {
    applyConfig(await adminApi.btnConfig())
  } catch (error) {
    loadError.value = errorMessage(error, t('common.loadFailed'))
    Message.error(loadError.value)
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
    form[field.key] = field.sensitive ? '' : (field.value ?? field.defaultValue)
    form[secretClearKey(field.key)] = 'false'
  }
}

async function save() {
  saving.value = true
  try {
    const result = await adminApi.updateBtnConfig(
      Object.fromEntries(
        fields.value.flatMap((field) => {
          if (field.sensitive) {
            if (form[secretClearKey(field.key)] === 'true') {
              return [[field.key, '']]
            }
            const value = form[field.key]?.trim()
            return value ? [[field.key, value]] : []
          }
          return [[field.key, form[field.key] ?? '']]
        }),
      ),
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

function secretClearKey(key: string) {
  return `${key}:clear`
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

onMounted(load)
</script>

<template>
  <div class="page-stack btn-config-page">
    <PageHeader :title="t('admin.btn.title')" :description="t('admin.btn.description')">
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
        <a-button
          type="primary"
          :loading="saving"
          :disabled="loading || Boolean(loadError) || fields.length === 0"
          @click="save"
        >
          <template #icon><IconSave /></template>
          {{ t('admin.common.save') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      <template #title>
        {{ t('admin.btn.hotReloadTitle') }}
      </template>
      {{ t('admin.btn.hotReloadText') }}
    </a-alert>

    <a-alert v-if="loadError" type="error" show-icon>
      {{ loadError }}
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
                v-else-if="field.valueType === 'integer' || field.valueType === 'decimal'"
                :model-value="form[field.key] === '' ? undefined : Number(form[field.key])"
                :min="field.min ?? undefined"
                :max="field.max ?? undefined"
                :precision="field.valueType === 'integer' ? 0 : 6"
                :step="field.valueType === 'integer' ? 1 : 0.1"
                hide-button
                @change="(value) => updateInteger(field, value)"
              />
              <template v-else-if="field.valueType === 'secret'">
                <a-input-password
                  v-model="form[field.key]"
                  :placeholder="
                    field.valueConfigured
                      ? t('admin.btn.secretKeepPlaceholder')
                      : t('admin.btn.secretPlaceholder')
                  "
                  :max-length="4096"
                />
                <div v-if="field.valueConfigured" class="secret-status">
                  <a-tag color="green" size="small">
                    {{ t('admin.btn.secretConfigured') }}
                  </a-tag>
                  <a-checkbox
                    :model-value="form[secretClearKey(field.key)] === 'true'"
                    @change="
                      (value) =>
                        (form[secretClearKey(field.key)] = value ? 'true' : 'false')
                    "
                  >
                    {{ t('admin.btn.clearSecret') }}
                  </a-checkbox>
                </div>
              </template>
              <a-textarea
                v-else-if="field.valueType === 'text' || field.valueType === 'list'"
                v-model="form[field.key]"
                :auto-size="{ minRows: 3, maxRows: 8 }"
                :max-length="16384"
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
  width: 100%;
  min-width: 0;
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
  border-radius: var(--app-radius);
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

.secret-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
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
