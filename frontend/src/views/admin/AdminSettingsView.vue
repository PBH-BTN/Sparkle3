<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconEmail,
  IconLock,
  IconRefresh,
  IconSave,
  IconSettings,
  IconThunderbolt,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  MailSettings,
  MailSettingsUpdate,
  RuntimeConfig,
  SwarmStatisticsStatus,
} from '@/api/adminTypes'
import {
  footerApi,
  type FooterConfig,
  type FooterMetricKey,
} from '@/api/footer'
import { useSessionStore } from '@/stores/session'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatDuration, formatNumber } = useFormatters()
const session = useSessionStore()
const loading = ref(true)
const saving = ref(false)
const refreshStarting = ref(false)
const mailAction = ref<'smtp' | 'pop3' | 'scan' | null>(null)
const smtpPassword = ref('')
const pop3Password = ref('')
const clearSmtpPassword = ref(false)
const clearPop3Password = ref(false)
const testRecipient = ref('')
const status = ref<SwarmStatisticsStatus | null>(null)
const subjectTemplateToken = '{{title}}'
const contentTemplateToken = '{{content}}'
const contactEmailVariables = [
  '{{senderName}}',
  '{{senderId}}',
  '{{recipientName}}',
  '{{recipientId}}',
]
const form = reactive<RuntimeConfig>({
  protocolMinVersion: 20,
  protocolMaxVersion: 20,
  queryIpPowCaptcha: false,
  clientDiscoveryEnabled: false,
  swarmStatisticsEnabled: true,
  swarmStatisticsRefreshMinutes: 60,
  swarmStatisticsWindowDays: 7,
  userContactEmailSubjectTemplate: '[Sparkle BTN] {{title}}',
  userContactEmailBodyTemplate:
    '{{senderName}}（UID {{senderId}}）通过 Sparkle BTN 向你发送了一条消息：\n\n{{content}}',
})
const mail = reactive<MailSettings>({
  smtp: {
    enabled: false,
    host: '',
    port: 587,
    username: '',
    security: 'STARTTLS',
    auth: true,
    connectionTimeoutSeconds: 10,
    readTimeoutSeconds: 15,
    writeTimeoutSeconds: 15,
    fromAddress: '',
    fromName: 'Sparkle BTN',
  },
  pop3: {
    enabled: false,
    host: '',
    port: 995,
    username: '',
    security: 'SSL',
    replyAddress: '',
    pollIntervalSeconds: 60,
    connectionTimeoutSeconds: 15,
    maxMessagesPerPoll: 100,
  },
  systemSender: { address: '', name: 'Sparkle BTN 系统通知' },
  userSender: { address: '', name: 'Sparkle BTN 用户邮件' },
  smtpPasswordConfigured: false,
  pop3PasswordConfigured: false,
  ingestion: {
    running: false,
    lastScanAt: null,
    lastSuccessAt: null,
    nextScanAt: null,
    lastImported: 0,
    lastSkipped: 0,
    lastFailed: 0,
    totalImported: 0,
    lastError: null,
  },
})
const performanceMetricKeys: FooterMetricKey[] = [
  'loadAverage',
  'cpu',
  'memory',
  'systemUptime',
  'serverUptime',
  'diskIo',
  'network',
  'httpRps',
  'sqlQps',
  'redisQps',
  'httpApiAverage',
  'httpApiP99',
  'httpPingAverage',
  'httpPingP99',
  'httpOtherAverage',
  'httpOtherP99',
]
const footerForm = reactive<FooterConfig>({
  disclaimerZh: '',
  disclaimerEn: '',
  disclaimerAudience: 'AUTHENTICATED',
  icp: '',
  icpAudience: 'AUTHENTICATED',
  columns: [],
  columnsAudience: 'AUTHENTICATED',
  timeAudience: 'AUTHENTICATED',
  loginAudience: 'AUTHENTICATED',
  performanceMetrics: performanceMetricKeys.map((key) => ({
    key,
    enabled: true,
    audience: 'ADMIN',
  })),
})
const audienceOptions = computed(() => [
  {
    label: t('siteFooterAdmin.authenticated'),
    value: 'AUTHENTICATED',
  },
  {
    label: t('siteFooterAdmin.adminOnly'),
    value: 'ADMIN',
  },
])
const mailSecurityOptions = computed(() => [
  { label: t('admin.settings.mailSecurityNone'), value: 'NONE' },
  { label: t('admin.settings.mailSecurityStarttls'), value: 'STARTTLS' },
  { label: t('admin.settings.mailSecuritySsl'), value: 'SSL' },
])
let pollTimer: number | undefined

async function load() {
  loading.value = true
  try {
    const [runtimeConfig, footerConfig, mailSettings] = await Promise.all([
      adminApi.config(),
      footerApi.config(),
      adminApi.mailSettings(),
    ])
    Object.assign(form, runtimeConfig)
    applyFooterConfig(footerConfig)
    applyMailSettings(mailSettings)
    if (!testRecipient.value) {
      testRecipient.value = session.user?.email ?? ''
    }
    await loadStatus()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function loadStatus() {
  try {
    status.value = await adminApi.swarmStatisticsStatus()
    if (status.value.running) {
      scheduleStatusPoll()
    }
  } catch {
    // The main settings form remains usable when the status endpoint is temporarily unavailable.
  }
}

function scheduleStatusPoll() {
  window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(async () => {
    await loadStatus()
    if (status.value?.running) scheduleStatusPoll()
  }, 2500)
}

async function save() {
  if (
    form.protocolMinVersion < 1 ||
    form.protocolMaxVersion < form.protocolMinVersion
  ) {
    Message.warning(t('admin.settings.invalidRange'))
    return
  }
  if (form.swarmStatisticsRefreshMinutes < 1 || form.swarmStatisticsWindowDays < 1) {
    Message.warning(t('admin.settings.invalidStatistics'))
    return
  }
  if (
    !form.userContactEmailSubjectTemplate.includes('{{title}}') ||
    !form.userContactEmailBodyTemplate.includes('{{content}}')
  ) {
    Message.warning(t('admin.settings.invalidEmailTemplate'))
    return
  }
  if (!validateFooter()) return
  if (!validateMail()) return
  saving.value = true
  try {
    const [runtimeConfig, footerConfig, mailSettings] = await Promise.all([
      adminApi.updateConfig(form),
      footerApi.updateConfig(footerForm),
      adminApi.updateMailSettings(toMailUpdate()),
    ])
    Object.assign(form, runtimeConfig)
    applyFooterConfig(footerConfig)
    applyMailSettings(mailSettings)
    await loadStatus()
    if (!form.swarmStatisticsEnabled) {
      session.traffic = null
    }
    await session.load().catch(() => undefined)
    Message.success(t('admin.common.updated'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

function applyMailSettings(settings: MailSettings) {
  const normalized = structuredClone(settings)
  Object.assign(mail, normalized)
  smtpPassword.value = ''
  pop3Password.value = ''
  clearSmtpPassword.value = false
  clearPop3Password.value = false
}

function toMailUpdate(): MailSettingsUpdate {
  return {
    smtpEnabled: mail.smtp.enabled,
    smtpHost: mail.smtp.host.trim(),
    smtpPort: mail.smtp.port,
    smtpUsername: mail.smtp.username.trim(),
    smtpPassword: smtpPassword.value || null,
    clearSmtpPassword: clearSmtpPassword.value,
    smtpSecurity: mail.smtp.security,
    smtpAuth: mail.smtp.auth,
    smtpConnectionTimeoutSeconds: mail.smtp.connectionTimeoutSeconds,
    smtpReadTimeoutSeconds: mail.smtp.readTimeoutSeconds,
    smtpWriteTimeoutSeconds: mail.smtp.writeTimeoutSeconds,
    smtpFromAddress: mail.smtp.fromAddress.trim(),
    smtpFromName: mail.smtp.fromName.trim(),
    pop3Enabled: mail.pop3.enabled,
    pop3Host: mail.pop3.host.trim(),
    pop3Port: mail.pop3.port,
    pop3Username: mail.pop3.username.trim(),
    pop3Password: pop3Password.value || null,
    clearPop3Password: clearPop3Password.value,
    pop3Security: mail.pop3.security,
    pop3ReplyAddress: mail.pop3.replyAddress.trim(),
    pop3PollIntervalSeconds: mail.pop3.pollIntervalSeconds,
    pop3ConnectionTimeoutSeconds: mail.pop3.connectionTimeoutSeconds,
    pop3MaxMessagesPerPoll: mail.pop3.maxMessagesPerPoll,
    systemSender: {
      address: mail.systemSender.address.trim(),
      name: mail.systemSender.name.trim(),
    },
    userSender: {
      address: mail.userSender.address.trim(),
      name: mail.userSender.name.trim(),
    },
  }
}

function validateMail() {
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (mail.smtp.port < 1 || mail.smtp.port > 65535) {
    Message.warning(t('admin.settings.invalidMailPort'))
    return false
  }
  if (
    mail.smtp.enabled &&
    (!mail.smtp.host.trim() ||
      (mail.smtp.auth && !mail.smtp.username.trim()) ||
      (mail.smtp.auth && !smtpPassword.value && !mail.smtpPasswordConfigured))
  ) {
    Message.warning(t('admin.settings.invalidSmtp'))
    return false
  }
  for (const address of [
    mail.smtp.fromAddress,
    mail.systemSender.address,
    mail.userSender.address,
    mail.pop3.replyAddress,
  ]) {
    if (address.trim() && !emailPattern.test(address.trim())) {
      Message.warning(t('admin.settings.invalidMailAddress'))
      return false
    }
  }
  if (
    mail.smtp.enabled &&
    (!mail.systemSender.address.trim() || !mail.userSender.address.trim())
  ) {
    Message.warning(t('admin.settings.invalidMailSender'))
    return false
  }
  if (mail.pop3.port < 1 || mail.pop3.port > 65535) {
    Message.warning(t('admin.settings.invalidMailPort'))
    return false
  }
  if (
    mail.pop3.enabled &&
    (!mail.pop3.host.trim() ||
      !mail.pop3.username.trim() ||
      (!pop3Password.value && !mail.pop3PasswordConfigured) ||
      !emailPattern.test(mail.pop3.replyAddress.trim() || mail.pop3.username.trim()))
  ) {
    Message.warning(t('admin.settings.invalidPop3'))
    return false
  }
  return true
}

async function persistMailSettings() {
  const settings = await adminApi.updateMailSettings(toMailUpdate())
  applyMailSettings(settings)
}

async function testMailSmtp() {
  if (!validateMail() || !testRecipient.value.trim()) {
    if (!testRecipient.value.trim()) {
      Message.warning(t('admin.settings.testRecipientRequired'))
    }
    return
  }
  mailAction.value = 'smtp'
  try {
    await persistMailSettings()
    const result = await adminApi.testSmtp(testRecipient.value.trim())
    Message.success(result.message)
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    mailAction.value = null
  }
}

async function testMailPop3() {
  if (!validateMail()) return
  mailAction.value = 'pop3'
  try {
    await persistMailSettings()
    const result = await adminApi.testPop3()
    Message.success(result.message)
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    mailAction.value = null
  }
}

async function scanMailNow() {
  mailAction.value = 'scan'
  try {
    mail.ingestion = await adminApi.scanMail()
    Message.success(t('admin.settings.mailScanComplete'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    mailAction.value = null
  }
}

function applyFooterConfig(config: FooterConfig) {
  const normalized = structuredClone(config)
  const configuredMetrics = new Map(
    normalized.performanceMetrics.map((metric) => [metric.key, metric]),
  )
  normalized.performanceMetrics = performanceMetricKeys.map(
    (key) =>
      configuredMetrics.get(key) ?? {
        key,
        enabled: true,
        audience: 'ADMIN',
      },
  )
  Object.assign(footerForm, normalized)
}

function validateFooter() {
  for (const column of footerForm.columns) {
    if (!column.titleZh.trim() && !column.titleEn.trim()) {
      Message.warning(t('siteFooterAdmin.invalidColumn'))
      return false
    }
    for (const link of column.links) {
      if (
        (!link.labelZh.trim() && !link.labelEn.trim()) ||
        !link.href.trim() ||
        (!link.href.startsWith('/') &&
          !/^https?:\/\/[^\s]+$/i.test(link.href.trim()))
      ) {
        Message.warning(t('siteFooterAdmin.invalidLink'))
        return false
      }
    }
  }
  return true
}

function addFooterColumn() {
  if (footerForm.columns.length >= 12) return
  footerForm.columns.push({
    titleZh: '',
    titleEn: '',
    links: [],
  })
}

function removeFooterColumn(index: number) {
  footerForm.columns.splice(index, 1)
}

function addFooterLink(columnIndex: number) {
  const column = footerForm.columns[columnIndex]
  if (!column || column.links.length >= 30) return
  column.links.push({
    labelZh: '',
    labelEn: '',
    href: '',
  })
}

function removeFooterLink(columnIndex: number, linkIndex: number) {
  footerForm.columns[columnIndex]?.links.splice(linkIndex, 1)
}

function footerMetricLabel(key: FooterMetricKey) {
  const httpMetric = key.match(
    /^http(Api|Ping|Other)(Average|P99)$/,
  )
  if (httpMetric) {
    const categoryKey =
      httpMetric[1] === 'Api'
        ? 'api'
        : httpMetric[1] === 'Ping'
          ? 'btnPing'
          : 'other'
    const valueKey = httpMetric[2] === 'Average' ? 'average' : 'p99'
    return `${t(`siteFooter.performance.${categoryKey}`)} ${t(
      `siteFooter.performance.${valueKey}`,
    )}`
  }
  return t(`siteFooter.performance.${key}`)
}

async function refreshStatistics() {
  refreshStarting.value = true
  try {
    status.value = await adminApi.refreshSwarmStatistics()
    Message.success(
      status.value.running
        ? t('admin.settings.refreshStarted')
        : t('admin.settings.refreshBusy'),
    )
    scheduleStatusPoll()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    refreshStarting.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => window.clearTimeout(pollTimer))
</script>

<template>
  <div class="page-stack settings-page">
    <PageHeader
      :title="t('admin.settings.title')"
      :description="t('admin.settings.description')"
    >
      <template #actions>
        <a-button type="primary" :loading="saving" @click="save">
          <template #icon><IconSave /></template>
          {{ t('admin.common.save') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      <template #title>
        {{ t('admin.settings.hotReloadTitle') }}
      </template>
      {{ t('admin.settings.hotReloadText') }}
    </a-alert>

    <a-spin :loading="loading" class="settings-spin">
      <a-card class="surface-card">
        <template #title>
          <span class="card-title">
            <IconThunderbolt />
            {{ t('admin.settings.statisticsTitle') }}
          </span>
        </template>
        <template #extra>
          <a-button
            :loading="refreshStarting || status?.running"
            :disabled="status?.running || !status?.enabled"
            @click="refreshStatistics"
          >
            <template #icon><IconRefresh /></template>
            {{ status?.running ? t('admin.settings.refreshing') : t('admin.settings.refreshNow') }}
          </a-button>
        </template>

        <a-alert
          v-if="status?.lastError"
          type="error"
          show-icon
          class="status-alert"
        >
          <template #title>{{ t('admin.settings.refreshError') }}</template>
          {{ status.lastError }}
        </a-alert>
        <a-alert
          v-else-if="status?.stale"
          type="warning"
          show-icon
          class="status-alert"
        >
          {{ t('admin.settings.dataStale') }}
        </a-alert>

        <div class="status-grid">
          <div class="status-item">
            <span>{{ t('admin.settings.refreshState') }}</span>
            <a-tag :color="status?.running ? 'arcoblue' : status?.enabled ? 'green' : 'gray'">
              {{
                status?.running
                  ? t('admin.settings.stateRunning')
                  : status?.enabled
                    ? t('admin.settings.stateEnabled')
                    : t('admin.settings.stateDisabled')
              }}
            </a-tag>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.dataUpdatedAt') }}</span>
            <strong>{{ formatDate(status?.dataUpdatedAt) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.lastDuration') }}</span>
            <strong>{{ formatDuration(status?.lastDurationMs) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.updatedUsers') }}</span>
            <strong>{{ formatNumber(status?.updatedUsers) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.lastTrigger') }}</span>
            <strong>
              {{
                status?.trigger === 'MANUAL'
                  ? t('admin.settings.manualTrigger')
                  : status?.trigger === 'SCHEDULED'
                    ? t('admin.settings.scheduledTrigger')
                    : t('common.notAvailable')
              }}
            </strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.lastFinishedAt') }}</span>
            <strong>{{ formatDate(status?.lastFinishedAt) }}</strong>
          </div>
        </div>

        <a-divider />
        <a-form :model="form" layout="vertical">
          <div class="settings-grid">
            <a-form-item :label="t('admin.settings.statisticsEnabled')">
              <a-switch v-model="form.swarmStatisticsEnabled" />
            </a-form-item>
            <div />
            <a-form-item :label="t('admin.settings.refreshInterval')" required>
              <a-input-number
                v-model="form.swarmStatisticsRefreshMinutes"
                :min="1"
                :max="10080"
              >
                <template #suffix>{{ t('admin.settings.minutes') }}</template>
              </a-input-number>
            </a-form-item>
            <a-form-item :label="t('admin.settings.windowDays')" required>
              <a-input-number
                v-model="form.swarmStatisticsWindowDays"
                :min="1"
                :max="90"
              >
                <template #suffix>{{ t('admin.settings.days') }}</template>
              </a-input-number>
            </a-form-item>
          </div>
          <p class="form-help">{{ t('admin.settings.statisticsHelp') }}</p>
        </a-form>
      </a-card>

      <a-card class="surface-card">
        <template #title>
          <span class="card-title">
            <IconEmail />
            {{ t('admin.settings.contactEmailTitle') }}
          </span>
        </template>
        <a-form :model="form" layout="vertical" @submit="save">
          <a-form-item :label="t('admin.settings.contactEmailSubjectTemplate')" required>
            <a-input
              v-model="form.userContactEmailSubjectTemplate"
              :max-length="255"
              show-word-limit
            />
            <template #extra>
              {{ t('admin.settings.contactEmailTemplateHint') }}
              <code>{{ subjectTemplateToken }}</code>
            </template>
          </a-form-item>
          <a-form-item :label="t('admin.settings.contactEmailBodyTemplate')" required>
            <a-textarea
              v-model="form.userContactEmailBodyTemplate"
              :auto-size="{ minRows: 7, maxRows: 14 }"
              :max-length="20000"
            />
            <template #extra>
              {{ t('admin.settings.contactEmailBodyHint') }}
              <code>{{ contentTemplateToken }}</code>
            </template>
          </a-form-item>
          <p class="template-variables">
            {{ t('admin.settings.availableTemplateVariables') }}
            <code v-for="variable in contactEmailVariables" :key="variable">
              {{ variable }}
            </code>
          </p>
        </a-form>
      </a-card>

      <a-card class="surface-card mail-settings-card">
        <template #title>
          <span class="card-title">
            <IconEmail />
            {{ t('admin.settings.mailTitle') }}
          </span>
        </template>
        <template #extra>
          <span class="mail-settings-description">
            {{ t('admin.settings.mailDescription') }}
          </span>
        </template>

        <a-alert
          v-if="mail.ingestion.lastError"
          type="warning"
          show-icon
          class="status-alert"
        >
          <template #title>{{ t('admin.settings.mailLastError') }}</template>
          {{ mail.ingestion.lastError }}
        </a-alert>

        <a-form :model="mail" layout="vertical">
          <section class="mail-settings-section">
            <div class="mail-section-heading">
              <div>
                <strong>{{ t('admin.settings.smtpTitle') }}</strong>
                <p>{{ t('admin.settings.smtpDescription') }}</p>
              </div>
              <a-switch v-model="mail.smtp.enabled">
                <template #checked>{{ t('common.enabled') }}</template>
                <template #unchecked>{{ t('common.disabled') }}</template>
              </a-switch>
            </div>

            <div class="settings-grid">
              <a-form-item :label="t('admin.settings.mailHost')">
                <a-input
                  v-model="mail.smtp.host"
                  placeholder="smtp.example.com"
                  :max-length="255"
                />
              </a-form-item>
              <a-form-item :label="t('admin.settings.mailPort')">
                <a-input-number v-model="mail.smtp.port" :min="1" :max="65535" />
              </a-form-item>
              <a-form-item :label="t('admin.settings.mailSecurity')">
                <a-select
                  v-model="mail.smtp.security"
                  :options="mailSecurityOptions"
                />
              </a-form-item>
              <a-form-item :label="t('admin.settings.smtpAuth')">
                <a-switch v-model="mail.smtp.auth" />
              </a-form-item>
              <a-form-item :label="t('admin.settings.mailUsername')">
                <a-input
                  v-model="mail.smtp.username"
                  autocomplete="off"
                  :max-length="320"
                />
              </a-form-item>
              <a-form-item :label="t('admin.settings.smtpPassword')">
                <a-input-password
                  v-model="smtpPassword"
                  autocomplete="new-password"
                  :disabled="!mail.smtp.auth"
                  :placeholder="
                    mail.smtpPasswordConfigured
                      ? t('admin.settings.passwordKeepHint')
                      : t('admin.settings.passwordRequiredHint')
                  "
                />
                <template #extra>
                  <a-checkbox v-model="clearSmtpPassword">
                    {{ t('admin.settings.clearPassword') }}
                  </a-checkbox>
                </template>
              </a-form-item>
            </div>

            <div class="settings-grid">
              <a-form-item :label="t('admin.settings.smtpFallbackAddress')">
                <a-input
                  v-model="mail.smtp.fromAddress"
                  placeholder="sparkle@example.com"
                  :max-length="320"
                />
              </a-form-item>
              <a-form-item :label="t('admin.settings.smtpFallbackName')">
                <a-input v-model="mail.smtp.fromName" :max-length="255" />
              </a-form-item>
              <a-form-item :label="t('admin.settings.systemSenderAddress')">
                <a-input v-model="mail.systemSender.address" :max-length="320" />
              </a-form-item>
              <a-form-item :label="t('admin.settings.systemSenderName')">
                <a-input v-model="mail.systemSender.name" :max-length="255" />
              </a-form-item>
              <a-form-item :label="t('admin.settings.userSenderAddress')">
                <a-input v-model="mail.userSender.address" :max-length="320" />
              </a-form-item>
              <a-form-item :label="t('admin.settings.userSenderName')">
                <a-input v-model="mail.userSender.name" :max-length="255" />
              </a-form-item>
            </div>

            <div class="mail-timeout-grid">
              <a-form-item :label="t('admin.settings.smtpConnectTimeout')">
                <a-input-number
                  v-model="mail.smtp.connectionTimeoutSeconds"
                  :min="1"
                  :max="120"
                />
              </a-form-item>
              <a-form-item :label="t('admin.settings.smtpReadTimeout')">
                <a-input-number
                  v-model="mail.smtp.readTimeoutSeconds"
                  :min="1"
                  :max="120"
                />
              </a-form-item>
              <a-form-item :label="t('admin.settings.smtpWriteTimeout')">
                <a-input-number
                  v-model="mail.smtp.writeTimeoutSeconds"
                  :min="1"
                  :max="120"
                />
              </a-form-item>
            </div>
            <p class="form-help">{{ t('admin.settings.smtpSecurityHint') }}</p>
          </section>

          <a-divider />

          <section class="mail-settings-section">
            <div class="mail-section-heading">
              <div>
                <strong>{{ t('admin.settings.pop3Title') }}</strong>
                <p>{{ t('admin.settings.pop3Description') }}</p>
              </div>
              <a-switch v-model="mail.pop3.enabled">
                <template #checked>{{ t('common.enabled') }}</template>
                <template #unchecked>{{ t('common.disabled') }}</template>
              </a-switch>
            </div>

            <div class="settings-grid">
              <a-form-item :label="t('admin.settings.mailHost')">
                <a-input
                  v-model="mail.pop3.host"
                  placeholder="pop.example.com"
                  :max-length="255"
                />
              </a-form-item>
              <a-form-item :label="t('admin.settings.mailPort')">
                <a-input-number v-model="mail.pop3.port" :min="1" :max="65535" />
              </a-form-item>
              <a-form-item :label="t('admin.settings.mailSecurity')">
                <a-select
                  v-model="mail.pop3.security"
                  :options="mailSecurityOptions"
                />
              </a-form-item>
              <a-form-item :label="t('admin.settings.mailUsername')">
                <a-input
                  v-model="mail.pop3.username"
                  autocomplete="off"
                  :max-length="320"
                />
              </a-form-item>
              <a-form-item :label="t('admin.settings.pop3Password')">
                <a-input-password
                  v-model="pop3Password"
                  autocomplete="new-password"
                  :placeholder="
                    mail.pop3PasswordConfigured
                      ? t('admin.settings.passwordKeepHint')
                      : t('admin.settings.passwordRequiredHint')
                  "
                />
                <template #extra>
                  <a-checkbox v-model="clearPop3Password">
                    {{ t('admin.settings.clearPassword') }}
                  </a-checkbox>
                </template>
              </a-form-item>
              <a-form-item :label="t('admin.settings.pop3ReplyAddress')">
                <a-input
                  v-model="mail.pop3.replyAddress"
                  placeholder="inbox@example.com"
                  :max-length="320"
                />
                <template #extra>
                  {{ t('admin.settings.pop3ReplyAddressHint') }}
                </template>
              </a-form-item>
              <a-form-item :label="t('admin.settings.pop3PollInterval')">
                <a-input-number
                  v-model="mail.pop3.pollIntervalSeconds"
                  :min="15"
                  :max="86400"
                >
                  <template #suffix>{{ t('admin.settings.seconds') }}</template>
                </a-input-number>
              </a-form-item>
              <a-form-item :label="t('admin.settings.pop3ConnectionTimeout')">
                <a-input-number
                  v-model="mail.pop3.connectionTimeoutSeconds"
                  :min="1"
                  :max="120"
                >
                  <template #suffix>{{ t('admin.settings.seconds') }}</template>
                </a-input-number>
              </a-form-item>
              <a-form-item :label="t('admin.settings.pop3MaxMessages')">
                <a-input-number
                  v-model="mail.pop3.maxMessagesPerPoll"
                  :min="1"
                  :max="1000"
                />
              </a-form-item>
            </div>
            <p class="form-help">{{ t('admin.settings.pop3ReplyHint') }}</p>
          </section>
        </a-form>

        <a-divider />
        <div class="mail-test-row">
          <a-input
            v-model="testRecipient"
            :placeholder="t('admin.settings.testRecipient')"
            :max-length="320"
          />
          <a-button
            type="outline"
            :loading="mailAction === 'smtp'"
            :disabled="mailAction !== null"
            @click="testMailSmtp"
          >
            {{ t('admin.settings.testSmtp') }}
          </a-button>
          <a-button
            type="outline"
            :loading="mailAction === 'pop3'"
            :disabled="mailAction !== null"
            @click="testMailPop3"
          >
            {{ t('admin.settings.testPop3') }}
          </a-button>
          <a-button
            type="outline"
            :loading="mailAction === 'scan'"
            :disabled="mailAction !== null || !mail.pop3.enabled"
            @click="scanMailNow"
          >
            <template #icon><IconRefresh /></template>
            {{ t('admin.settings.scanMailNow') }}
          </a-button>
        </div>
        <p class="form-help">{{ t('admin.settings.mailTestHint') }}</p>

        <div class="status-grid mail-status-grid">
          <div class="status-item">
            <span>{{ t('admin.settings.mailLastScan') }}</span>
            <strong>{{ formatDate(mail.ingestion.lastScanAt) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.mailNextScan') }}</span>
            <strong>{{ formatDate(mail.ingestion.nextScanAt) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.mailTotalImported') }}</span>
            <strong>{{ formatNumber(mail.ingestion.totalImported) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.mailLastImported') }}</span>
            <strong>{{ formatNumber(mail.ingestion.lastImported) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.mailLastSkipped') }}</span>
            <strong>{{ formatNumber(mail.ingestion.lastSkipped) }}</strong>
          </div>
          <div class="status-item">
            <span>{{ t('admin.settings.mailLastFailed') }}</span>
            <strong>{{ formatNumber(mail.ingestion.lastFailed) }}</strong>
          </div>
        </div>
      </a-card>

      <a-card class="surface-card footer-settings-card">
        <template #title>
          <span class="card-title">
            <IconSettings />
            {{ t('siteFooterAdmin.title') }}
          </span>
        </template>
        <template #extra>
          <span class="footer-settings-description">
            {{ t('siteFooterAdmin.description') }}
          </span>
        </template>

        <a-form :model="footerForm" layout="vertical">
          <section class="footer-settings-section">
            <div class="footer-section-heading">
              <div>
                <strong>{{ t('siteFooterAdmin.disclaimerTitle') }}</strong>
                <p>{{ t('siteFooterAdmin.disclaimerDescription') }}</p>
              </div>
              <a-select
                v-model="footerForm.disclaimerAudience"
                :options="audienceOptions"
                class="audience-select"
              />
            </div>
            <div class="settings-grid">
              <a-form-item :label="t('siteFooterAdmin.disclaimerZh')">
                <a-textarea
                  v-model="footerForm.disclaimerZh"
                  :auto-size="{ minRows: 5, maxRows: 12 }"
                  :max-length="20000"
                />
              </a-form-item>
              <a-form-item :label="t('siteFooterAdmin.disclaimerEn')">
                <a-textarea
                  v-model="footerForm.disclaimerEn"
                  :auto-size="{ minRows: 5, maxRows: 12 }"
                  :max-length="20000"
                />
              </a-form-item>
            </div>
            <div class="settings-grid">
              <a-form-item :label="t('siteFooterAdmin.icp')">
                <a-input v-model="footerForm.icp" :max-length="128" />
                <template #extra>{{ t('siteFooterAdmin.icpHint') }}</template>
              </a-form-item>
              <a-form-item :label="t('siteFooterAdmin.icpAudience')">
                <a-select
                  v-model="footerForm.icpAudience"
                  :options="audienceOptions"
                />
              </a-form-item>
            </div>
          </section>

          <a-divider />

          <section class="footer-settings-section">
            <div class="footer-section-heading">
              <div>
                <strong>{{ t('siteFooterAdmin.columnsTitle') }}</strong>
                <p>{{ t('siteFooterAdmin.columnsDescription') }}</p>
              </div>
              <div class="footer-section-actions">
                <a-select
                  v-model="footerForm.columnsAudience"
                  :options="audienceOptions"
                  class="audience-select"
                />
                <a-button type="outline" @click="addFooterColumn">
                  {{ t('siteFooterAdmin.addColumn') }}
                </a-button>
              </div>
            </div>

            <p v-if="footerForm.columns.length === 0" class="empty-columns">
              {{ t('siteFooterAdmin.noColumns') }}
            </p>
            <div class="footer-column-editor-list">
              <div
                v-for="(column, columnIndex) in footerForm.columns"
                :key="columnIndex"
                class="footer-column-editor"
              >
                <div class="footer-column-editor-heading">
                  <strong>
                    {{
                      t('siteFooterAdmin.column', {
                        index: columnIndex + 1,
                      })
                    }}
                  </strong>
                  <a-button
                    type="text"
                    size="small"
                    @click="removeFooterColumn(columnIndex)"
                  >
                    {{ t('siteFooterAdmin.removeColumn') }}
                  </a-button>
                </div>
                <div class="settings-grid">
                  <a-form-item :label="t('siteFooterAdmin.columnTitleZh')">
                    <a-input v-model="column.titleZh" :max-length="100" />
                  </a-form-item>
                  <a-form-item :label="t('siteFooterAdmin.columnTitleEn')">
                    <a-input v-model="column.titleEn" :max-length="100" />
                  </a-form-item>
                </div>

                <div class="footer-link-list">
                  <div class="field-label">{{ t('siteFooterAdmin.links') }}</div>
                  <div
                    v-for="(link, linkIndex) in column.links"
                    :key="linkIndex"
                    class="footer-link-editor"
                  >
                    <a-input
                      v-model="link.labelZh"
                      :placeholder="t('siteFooterAdmin.linkLabelZh')"
                      :max-length="100"
                    />
                    <a-input
                      v-model="link.labelEn"
                      :placeholder="t('siteFooterAdmin.linkLabelEn')"
                      :max-length="100"
                    />
                    <a-input
                      v-model="link.href"
                      :placeholder="t('siteFooterAdmin.linkUrlPlaceholder')"
                      :max-length="2048"
                    />
                    <a-button type="text" @click="removeFooterLink(columnIndex, linkIndex)">
                      {{ t('siteFooterAdmin.removeLink') }}
                    </a-button>
                  </div>
                  <a-button
                    type="dashed"
                    long
                    @click="addFooterLink(columnIndex)"
                  >
                    {{ t('siteFooterAdmin.addLink') }}
                  </a-button>
                </div>
              </div>
            </div>
          </section>

          <a-divider />

          <section class="footer-settings-section">
            <div class="settings-grid">
              <a-form-item :label="t('siteFooterAdmin.timeAudience')">
                <a-select
                  v-model="footerForm.timeAudience"
                  :options="audienceOptions"
                />
                <template #extra>
                  {{ t('siteFooterAdmin.timeDescription') }}
                </template>
              </a-form-item>
              <a-form-item :label="t('siteFooterAdmin.loginAudience')">
                <a-select
                  v-model="footerForm.loginAudience"
                  :options="audienceOptions"
                />
                <template #extra>
                  {{ t('siteFooterAdmin.loginDescription') }}
                </template>
              </a-form-item>
            </div>
          </section>

          <a-divider />

          <section class="footer-settings-section">
            <div class="footer-section-heading">
              <div>
                <strong>{{ t('siteFooterAdmin.performanceTitle') }}</strong>
                <p>{{ t('siteFooterAdmin.performanceDescription') }}</p>
              </div>
            </div>
            <div class="metric-visibility-grid">
              <template
                v-for="(metric, metricIndex) in footerForm.performanceMetrics"
                :key="metric.key"
              >
                <div v-if="metricIndex === 10" class="metric-group-heading">
                  <strong>{{ t('siteFooterAdmin.httpResponses') }}</strong>
                  <span>{{ t('siteFooterAdmin.httpResponsesHint') }}</span>
                </div>
                <div class="metric-visibility-row">
                  <span>{{ footerMetricLabel(metric.key) }}</span>
                  <a-switch v-model="metric.enabled" size="small" />
                  <a-select
                    v-model="metric.audience"
                    :options="audienceOptions"
                    size="small"
                    :disabled="!metric.enabled"
                  />
                </div>
              </template>
            </div>
          </section>
        </a-form>
      </a-card>

      <a-card class="surface-card security-card">
        <template #title>
          <span class="card-title">
            <IconLock />
            {{ t('admin.settings.securityTitle') }}
          </span>
        </template>
        <p>{{ t('admin.settings.securityNotice') }}</p>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.settings-page {
  width: min(1080px, 100%);
}

.settings-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.status-alert {
  margin-bottom: 16px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.status-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 62px;
  padding: 12px 14px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}

.status-item > span {
  color: var(--color-text-3);
  font-size: 12px;
}

.status-item strong {
  overflow-wrap: anywhere;
  font-size: 13px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.settings-grid :deep(.arco-input-number) {
  width: 100%;
}

.form-help,
.security-card p {
  margin: 0;
  color: var(--color-text-2);
  line-height: 1.75;
}

.template-variables {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin: -4px 0 8px;
  color: var(--color-text-3);
  font-size: 12px;
}

.template-variables code {
  color: rgb(var(--primary-6));
}

.mail-settings-card :deep(.arco-card-header) {
  align-items: flex-start;
}

.mail-settings-description {
  display: block;
  max-width: 570px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
  text-align: right;
}

.mail-settings-section {
  min-width: 0;
}

.mail-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.mail-section-heading strong {
  color: var(--color-text-1);
  font-size: 14px;
}

.mail-section-heading p {
  margin: 4px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.65;
}

.mail-timeout-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 18px;
}

.mail-timeout-grid :deep(.arco-input-number) {
  width: 100%;
}

.mail-test-row {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) auto auto auto;
  gap: 8px;
  align-items: center;
}

.mail-status-grid {
  margin-top: 16px;
}

.footer-settings-card :deep(.arco-card-header) {
  align-items: flex-start;
}

.footer-settings-description {
  display: block;
  max-width: 570px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
  text-align: right;
}

.footer-settings-section {
  min-width: 0;
}

.footer-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.footer-section-heading strong {
  color: var(--color-text-1);
  font-size: 14px;
}

.footer-section-heading p {
  margin: 4px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.65;
}

.footer-section-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.audience-select {
  width: 150px;
}

.empty-columns {
  margin: 0 0 12px;
  padding: 18px;
  color: var(--color-text-3);
  text-align: center;
  background: var(--color-fill-1);
  border: 1px dashed var(--color-border-2);
  border-radius: var(--app-radius);
}

.footer-column-editor-list {
  display: grid;
  gap: 12px;
}

.footer-column-editor {
  padding: 16px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
}

.footer-column-editor-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.footer-link-list {
  display: grid;
  gap: 8px;
}

.field-label {
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 500;
}

.footer-link-editor {
  display: grid;
  grid-template-columns: minmax(110px, 0.8fr) minmax(110px, 0.8fr) minmax(220px, 1.7fr) auto;
  gap: 8px;
  align-items: center;
}

.metric-visibility-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.metric-visibility-row {
  display: grid;
  grid-template-columns: minmax(130px, 1fr) auto 130px;
  gap: 10px;
  align-items: center;
  min-height: 38px;
  padding: 6px 10px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}

.metric-visibility-row > span {
  overflow: hidden;
  color: var(--color-text-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-group-heading {
  display: flex;
  grid-column: 1 / -1;
  flex-direction: column;
  gap: 3px;
  margin-top: 12px;
  padding: 12px 10px 4px;
  border-top: 1px solid var(--color-border-2);
}

.metric-group-heading span {
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .status-grid,
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .footer-settings-description {
    display: none;
  }

  .mail-settings-description {
    display: none;
  }

  .footer-section-heading,
  .mail-section-heading,
  .footer-section-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .audience-select {
    width: 100%;
  }

  .footer-link-editor,
  .metric-visibility-grid,
  .mail-timeout-grid,
  .mail-test-row {
    grid-template-columns: 1fr;
  }

  .metric-visibility-row {
    grid-template-columns: minmax(0, 1fr) auto 130px;
  }
}
</style>
