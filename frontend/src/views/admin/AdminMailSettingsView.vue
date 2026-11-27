<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { IconEmail, IconRefresh, IconSave } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { useAdminDraft } from '@/composables/useAdminDraft'
import AdminRelatedLinks from '@/components/admin/AdminRelatedLinks.vue'
import { adminApi } from '@/api/admin'
import type { MailSettings, MailSettingsUpdate } from '@/api/adminTypes'
import { useSessionStore } from '@/stores/session'
import { errorMessage, useFormatters } from '@/utils/format'
const { t } = useI18n()
const { formatDate, formatNumber } = useFormatters()
const session = useSessionStore()
const loading = ref(true)
const saving = ref(false)
const failed = ref(false)
const mailAction = ref<'smtp' | 'pop3' | 'scan' | null>(null)
const smtpPassword = ref('')
const pop3Password = ref('')
const clearSmtpPassword = ref(false)
const clearPop3Password = ref(false)
const testRecipient = ref('')
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
const mailSecurityOptions = computed(() => [
  { label: t('admin.settings.mailSecurityNone'), value: 'NONE' },
  { label: t('admin.settings.mailSecurityStarttls'), value: 'STARTTLS' },
  { label: t('admin.settings.mailSecuritySsl'), value: 'SSL' },
])
const draft = useAdminDraft(toMailUpdate)

function applyMailSettings(settings: MailSettings) {
  const normalized = structuredClone(settings)
  Object.assign(mail, normalized)
  smtpPassword.value = ''
  pop3Password.value = ''
  clearSmtpPassword.value = false
  clearPop3Password.value = false
  draft.accept()
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


async function load() {
  if (!(await draft.confirmDiscard())) return
  loading.value = true
  failed.value = false
  try { applyMailSettings(await adminApi.mailSettings()); testRecipient.value ||= session.user?.email ?? '' }
  catch (error) { failed.value = true; Message.error(errorMessage(error, t('common.loadFailed'))) }
  finally { loading.value = false }
}
async function save() {
  if (failed.value || loading.value || !validateMail()) return
  saving.value = true
  try { await persistMailSettings(); Message.success(t('admin.common.updated')) }
  catch (error) { Message.error(errorMessage(error, t('common.requestFailed'))) }
  finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <div class="page-stack">
    <PageHeader :title="t('admin.organization.pages.mail')">
      <template #actions><a-button type="primary" :loading="saving" :disabled="loading || failed || mailAction !== null" @click="save"><template #icon><IconSave /></template>{{ t('admin.common.save') }}</a-button></template>
    </PageHeader>
    <a-alert v-if="failed" type="error">{{ t('admin.organization.loadFailed') }}<template #action><a-button @click="load">{{ t('common.retry') }}</a-button></template></a-alert>
    <a-spin :loading="loading" class="settings-spin">
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
            :disabled="mailAction !== null || loading || failed || saving"
            @click="testMailSmtp"
          >
            {{ t('admin.settings.testSmtp') }}
          </a-button>
          <a-button
            type="outline"
            :loading="mailAction === 'pop3'"
            :disabled="mailAction !== null || loading || failed || saving"
            @click="testMailPop3"
          >
            {{ t('admin.settings.testPop3') }}
          </a-button>
          <a-button
            type="outline"
            :loading="mailAction === 'scan'"
            :disabled="mailAction !== null || !mail.pop3.enabled || loading || failed || saving"
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

    </a-spin>
    <AdminRelatedLinks :paths="['/admin/btn/mail-identities', '/admin/contact-template', '/admin/user-contact-emails', '/admin/tasks']" />
  </div>
</template>
<style scoped src="./adminSettings.css"></style>
