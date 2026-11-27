<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { IconRefresh, IconSave } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import AdminRelatedLinks from '@/components/admin/AdminRelatedLinks.vue'
import { useAdminDraft } from '@/composables/useAdminDraft'
import { adminApi } from '@/api/admin'
import type { SwarmStatisticsStatus } from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const props = defineProps<{ section: 'features' | 'statistics' | 'homepage-display' | 'contact-template' }>()
const { t } = useI18n()
const { formatDate, formatNumber, formatDuration } = useFormatters()
const form = reactive({ enabled: true, refreshMinutes: 60, windowDays: 7, clientDiscoveryEnabled: false,
  reportedStaffOnly: false, confirmedStaffOnly: false, activityStaffOnly: false,
  averageLossStaffOnly: false, totalLossStaffOnly: true, subject: '', body: '' })
const loading = ref(true)
const saving = ref(false)
const failed = ref(false)
const refreshing = ref(false)
const status = ref<SwarmStatisticsStatus | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
const titleId = computed(() => ({ features: 'site-features', statistics: 'traffic-statistics', 'homepage-display': 'homepage-display', 'contact-template': 'contact-template' }[props.section]))
const audiences = [
  ['reportedStaffOnly', 'homepageReportedAudience'], ['confirmedStaffOnly', 'homepageConfirmedAudience'],
  ['activityStaffOnly', 'homepageActivityAudience'], ['averageLossStaffOnly', 'homepageAverageLossAudience'],
  ['totalLossStaffOnly', 'homepageTotalLossAudience'],
] as const
const variables = ['{{title}}', '{{content}}', '{{senderName}}', '{{senderId}}', '{{recipientName}}', '{{recipientId}}']
const draft = useAdminDraft(payload)

function payload() {
  switch (props.section) {
    case 'features': return { clientDiscoveryEnabled: form.clientDiscoveryEnabled }
    case 'statistics': return { enabled: form.enabled, refreshMinutes: form.refreshMinutes, windowDays: form.windowDays }
    case 'homepage-display': return { enabled: form.enabled, ...Object.fromEntries(audiences.map(([key]) => [key, form[key]])) }
    case 'contact-template': return { subject: form.subject, body: form.body }
  }
}

async function load() {
  if (!(await draft.confirmDiscard())) return
  loading.value = true
  failed.value = false
  try {
    Object.assign(form, await adminApi.settingsSection(props.section))
    draft.accept()
    if (props.section === 'statistics') await loadStatus()
  } catch (error) {
    failed.value = true
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally { loading.value = false }
}

async function save() {
  if (loading.value || failed.value) return
  if (props.section === 'statistics' && (!Number.isInteger(form.refreshMinutes) || form.refreshMinutes < 1 || form.refreshMinutes > 10080 || !Number.isInteger(form.windowDays) || form.windowDays < 1 || form.windowDays > 90)) {
    Message.warning(t('admin.settings.invalidStatistics')); return
  }
  if (props.section === 'contact-template' && (!form.subject.includes('{{title}}') || !form.body.includes('{{content}}'))) {
    Message.warning(t('admin.settings.invalidEmailTemplate')); return
  }
  saving.value = true
  try {
    Object.assign(form, await adminApi.updateSettingsSection(props.section, payload()))
    draft.accept()
    Message.success(t('admin.common.updated'))
    if (props.section === 'statistics') await loadStatus()
  } catch (error) { Message.error(errorMessage(error, t('common.requestFailed'))) }
  finally { saving.value = false }
}

async function loadStatus() {
  try {
    status.value = await adminApi.swarmStatisticsStatus()
    clearTimeout(timer)
    if (status.value.running) timer = setTimeout(loadStatus, 2500)
  } catch (error) { Message.error(errorMessage(error, t('common.loadFailed'))) }
}

async function refreshStatistics() {
  refreshing.value = true
  try {
    status.value = await adminApi.refreshSwarmStatistics()
    Message.success(t('admin.settings.refreshStarted'))
    await loadStatus()
  } catch (error) { Message.error(errorMessage(error, t('common.requestFailed'))) }
  finally { refreshing.value = false }
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t(`admin.organization.pages.${titleId}`)" :description="t('admin.organization.independentSave')">
      <template #actions>
        <a-button :loading="loading" @click="load">{{ t('admin.common.refresh') }}</a-button>
        <a-button type="primary" :loading="saving" :disabled="loading || failed" @click="save"><template #icon><IconSave /></template>{{ t('admin.common.save') }}</a-button>
      </template>
    </PageHeader>
    <a-alert v-if="failed" type="error">{{ t('admin.organization.loadFailed') }}<template #action><a-button @click="load">{{ t('common.retry') }}</a-button></template></a-alert>
    <a-spin :loading="loading" class="settings-spin">
      <a-card class="surface-card">
        <a-form :model="form" layout="vertical" @submit="save">
          <template v-if="section === 'features'">
            <a-form-item :label="t('admin.settings.clientDiscovery')"><a-switch v-model="form.clientDiscoveryEnabled" /></a-form-item>
          </template>
          <template v-else-if="section === 'statistics'">
            <a-form-item :label="t('admin.settings.statisticsEnabled')"><a-switch v-model="form.enabled" /></a-form-item>
            <a-form-item :label="t('admin.settings.refreshInterval')" required><a-input-number v-model="form.refreshMinutes" :min="1" :max="10080" :precision="0"><template #suffix>{{ t('admin.settings.minutes') }}</template></a-input-number></a-form-item>
            <a-form-item :label="t('admin.settings.windowDays')" required><a-input-number v-model="form.windowDays" :min="1" :max="90" :precision="0"><template #suffix>{{ t('admin.settings.days') }}</template></a-input-number></a-form-item>
            <p class="form-help">{{ t('admin.settings.statisticsHelp') }}</p>
          </template>
          <template v-else-if="section === 'homepage-display'">
            <a-form-item :label="t('admin.settings.homepageNetworkTrafficEnabled')"><a-switch v-model="form.enabled" /></a-form-item>
            <a-form-item v-for="[key, label] in audiences" :key="key" :label="t(`admin.settings.${label}`)">
              <a-select v-model="form[key]"><a-option :value="false">{{ t('admin.settings.trafficAudienceSignedIn') }}</a-option><a-option :value="true">{{ t('admin.settings.trafficAudienceStaff') }}</a-option></a-select>
            </a-form-item>
          </template>
          <template v-else>
            <a-form-item :label="t('admin.settings.contactEmailSubjectTemplate')" required><a-input v-model="form.subject" :max-length="255" show-word-limit /></a-form-item>
            <a-form-item :label="t('admin.settings.contactEmailBodyTemplate')" required><a-textarea v-model="form.body" :max-length="20000" :auto-size="{ minRows: 7, maxRows: 14 }" /></a-form-item>
            <p>{{ t('admin.settings.contactEmailTemplateHint') }} <code v-pre>{{title}}</code></p>
            <p>{{ t('admin.settings.contactEmailBodyHint') }} <code v-pre>{{content}}</code></p>
            <p class="template-variables">{{ t('admin.settings.availableTemplateVariables') }} <code v-for="variable in variables" :key="variable">{{ variable }}</code></p>
          </template>
        </a-form>
      </a-card>
      <a-card v-if="section === 'statistics'" class="surface-card" :title="t('admin.settings.refreshState')">
        <template #extra><a-button :loading="refreshing || status?.running" :disabled="!status?.enabled || loading || failed" @click="refreshStatistics"><template #icon><IconRefresh /></template>{{ t('admin.settings.refreshNow') }}</a-button></template>
        <a-alert v-if="status?.lastError" type="error">{{ status.lastError }}</a-alert>
        <a-alert v-else-if="status?.stale" type="warning">{{ t('admin.settings.dataStale') }}</a-alert>
        <a-descriptions :column="1">
          <a-descriptions-item :label="t('admin.settings.refreshState')">{{ t(status?.running ? 'admin.settings.stateRunning' : status?.enabled ? 'admin.settings.stateEnabled' : 'admin.settings.stateDisabled') }}</a-descriptions-item>
          <a-descriptions-item :label="t('admin.settings.dataUpdatedAt')">{{ formatDate(status?.dataUpdatedAt) }}</a-descriptions-item>
          <a-descriptions-item :label="t('admin.settings.lastDuration')">{{ formatDuration(status?.lastDurationMs) }}</a-descriptions-item>
          <a-descriptions-item :label="t('admin.settings.updatedUsers')">{{ formatNumber(status?.updatedUsers) }}</a-descriptions-item>
          <a-descriptions-item :label="t('admin.settings.lastTrigger')">{{ t(status?.trigger === 'MANUAL' ? 'admin.settings.manualTrigger' : 'admin.settings.scheduledTrigger') }}</a-descriptions-item>
          <a-descriptions-item :label="t('admin.settings.lastFinishedAt')">{{ formatDate(status?.lastFinishedAt) }}</a-descriptions-item>
        </a-descriptions>
      </a-card>
    </a-spin>
    <AdminRelatedLinks v-if="section === 'statistics' || section === 'homepage-display'" :paths="['/admin/homepage-display', '/admin/traffic-statistics', '/admin/homepage-statistics', '/admin/tasks']" />
    <AdminRelatedLinks v-if="section === 'contact-template'" :paths="['/admin/mail', '/admin/btn/mail-identities', '/admin/user-contact-emails']" />
  </div>
</template>

<style scoped src="./adminSettings.css"></style>
