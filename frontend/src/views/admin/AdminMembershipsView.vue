<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconLink,
  IconRefresh,
  IconSave,
  IconSync,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  AfdianSettings,
  LicenseSettings,
  MemberPlatformConfig,
  MemberPlatformSettings,
  MemberReconciliation,
  MemberTransactionAdmin,
  MemberTransactionPage,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

interface ProviderForm {
  enabled: boolean
  accountId: string
  accessToken: string
  clearAccessToken: boolean
  webhookToken: string
  clearWebhookToken: boolean
  currency: string
  exchangeRate: string
}

const { t } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(true)
const savingProvider = ref('')
const syncingProvider = ref('')
const savingCurrency = ref(false)
const syncing = ref(false)
const settings = ref<MemberPlatformSettings | null>(null)
const afdianData = ref<AfdianSettings | null>(null)
const licenseData = ref<LicenseSettings | null>(null)
const transactionPage = ref<MemberTransactionPage | null>(null)
const reconciliation = ref<MemberReconciliation>({ runs: [], events: [] })
const providerForms = reactive<Record<string, ProviderForm>>({})
const afdianForm = reactive({
  userId: '',
  accessToken: '',
  clearToken: false,
  apiBaseUrl: 'https://afdian.com/api/open',
  syncEnabled: true,
})
const licenseForm = reactive({
  donationLicenseAmount: '50',
  validYears: 100,
  privateKey: '',
  clearPrivateKey: false,
})
const savingAfdian = ref(false)
const savingLicense = ref(false)
const currencyForm = reactive({
  baseCurrency: 'CNY',
  exchangeRateApiUrl: 'https://open.er-api.com/v6/latest/{currency}',
})
const filterProvider = ref('')
const filterKeyword = ref('')
const filterType = ref('')
const filterStatus = ref('')
const filterRemotePresent = ref<'' | 'true' | 'false'>('')
const filterPage = ref(1)
const filterSize = ref(20)

const providerOptions = computed(() => [
  { label: t('memberships.providers.AFDIAN'), value: 'AFDIAN' },
  ...(settings.value?.providers ?? [])
    .filter((provider) => provider.supported)
    .map((provider) => ({
      label: providerLabel(provider.provider),
      value: provider.provider,
    })),
])

const transactionColumns = (): TableColumnData[] => [
  { title: t('admin.memberships.provider'), slotName: 'provider', width: 110 },
  { title: t('admin.memberships.transactionId'), dataIndex: 'transactionId', width: 220 },
  { title: t('admin.memberships.user'), slotName: 'user', width: 190 },
  { title: t('admin.memberships.type'), dataIndex: 'type', width: 110 },
  { title: t('admin.memberships.product'), slotName: 'product', width: 210 },
  { title: t('admin.memberships.amount'), slotName: 'amount', width: 140 },
  { title: t('admin.memberships.status'), dataIndex: 'status', width: 110 },
  { title: t('admin.memberships.remote'), slotName: 'remote', width: 120 },
  { title: t('admin.memberships.occurredAt'), slotName: 'occurredAt', width: 165 },
  { title: t('admin.memberships.lastReconciled'), slotName: 'lastReconciledAt', width: 165 },
]

const runColumns = (): TableColumnData[] => [
  { title: 'ID', dataIndex: 'id', width: 75 },
  { title: t('admin.memberships.provider'), dataIndex: 'provider', width: 110 },
  { title: t('admin.memberships.runStatus'), dataIndex: 'status', width: 120 },
  { title: t('admin.memberships.startedAt'), slotName: 'startedAt', width: 165 },
  { title: t('admin.memberships.pages'), dataIndex: 'pagesFetched', width: 80 },
  { title: t('admin.memberships.seen'), dataIndex: 'recordsSeen', width: 90 },
  { title: t('admin.memberships.created'), dataIndex: 'insertedCount', width: 90 },
  { title: t('admin.memberships.updated'), dataIndex: 'updatedCount', width: 90 },
  { title: t('admin.memberships.missing'), dataIndex: 'missingCount', width: 90 },
  { title: t('admin.memberships.error'), dataIndex: 'errorMessage', width: 260 },
]

const auditColumns = (): TableColumnData[] => [
  { title: t('admin.memberships.event'), dataIndex: 'eventType', width: 150 },
  { title: t('admin.memberships.provider'), dataIndex: 'provider', width: 110 },
  { title: t('admin.memberships.transactionId'), dataIndex: 'transactionId', width: 230 },
  { title: t('admin.memberships.changedFields'), slotName: 'changedFields', width: 260 },
  { title: t('admin.memberships.detectedAt'), slotName: 'createdAt', width: 165 },
]

async function load() {
  loading.value = true
  try {
    const [data, afdian, license, reconciliationData] = await Promise.all([
      adminApi.memberPlatformSettings(),
      adminApi.afdianSettings(),
      adminApi.membershipLicenseSettings(),
      adminApi.memberPlatformReconciliation({ limit: 50 }),
    ])
    settings.value = data
    applyAfdian(afdian)
    applyLicense(license)
    reconciliation.value = reconciliationData
    currencyForm.baseCurrency = data.baseCurrency
    currencyForm.exchangeRateApiUrl = data.exchangeRateApiUrl
    data.providers.forEach(applyProvider)
    await loadTransactions()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function applyAfdian(data: AfdianSettings) {
  afdianData.value = data
  Object.assign(afdianForm, {
    userId: data.userId,
    accessToken: '',
    clearToken: false,
    apiBaseUrl: data.apiBaseUrl,
    syncEnabled: data.syncEnabled,
  })
}

function applyLicense(data: LicenseSettings) {
  licenseData.value = data
  Object.assign(licenseForm, {
    donationLicenseAmount: data.donationLicenseAmount,
    validYears: data.validYears,
    privateKey: '',
    clearPrivateKey: false,
  })
}

async function saveAfdian() {
  savingAfdian.value = true
  try {
    applyAfdian(
      await adminApi.updateAfdianSettings({
        userId: afdianForm.userId.trim(),
        accessToken: afdianForm.accessToken.trim() || undefined,
        clearToken: afdianForm.clearToken,
        apiBaseUrl: afdianForm.apiBaseUrl.trim(),
        syncEnabled: afdianForm.syncEnabled,
      }),
    )
    Message.success(t('admin.afdian.saved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingAfdian.value = false
  }
}

async function saveLicense() {
  savingLicense.value = true
  try {
    applyLicense(
      await adminApi.updateMembershipLicenseSettings({
        donationLicenseAmount: licenseForm.donationLicenseAmount.trim(),
        validYears: licenseForm.validYears,
        privateKey: licenseForm.privateKey.trim() || undefined,
        clearPrivateKey: licenseForm.clearPrivateKey,
      }),
    )
    Message.success(t('admin.memberships.licenseSaved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingLicense.value = false
  }
}

async function syncAfdianNow() {
  syncingProvider.value = 'AFDIAN'
  try {
    const result = await adminApi.syncAfdian()
    Message.success(t('admin.afdian.syncDone', { ...result }))
    await Promise.all([loadTransactions(), loadReconciliation(), refreshProviders()])
  } catch (error) {
    Message.error(errorMessage(error, t('admin.afdian.syncFailed')))
  } finally {
    syncingProvider.value = ''
  }
}

function applyProvider(provider: MemberPlatformConfig) {
  providerForms[provider.provider] = {
    enabled: provider.enabled,
    accountId: provider.accountId ?? '',
    accessToken: '',
    clearAccessToken: false,
    webhookToken: '',
    clearWebhookToken: false,
    currency: provider.currency || 'CNY',
    exchangeRate: provider.exchangeRate || '1',
  }
}

async function saveProvider(provider: MemberPlatformConfig) {
  const form = providerForms[provider.provider]
  if (!form) return
  savingProvider.value = provider.provider
  try {
    const saved = await adminApi.updateMemberPlatform(provider.provider, {
      enabled: form.enabled,
      accountId: form.accountId.trim(),
      accessToken: form.accessToken.trim() || undefined,
      clearAccessToken: form.clearAccessToken,
      webhookToken: form.webhookToken.trim() || undefined,
      clearWebhookToken: form.clearWebhookToken,
      currency: form.currency.trim(),
      exchangeRate: form.exchangeRate.trim(),
    })
    replaceProvider(saved)
    Message.success(t('admin.memberships.providerSaved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingProvider.value = ''
  }
}

function replaceProvider(saved: MemberPlatformConfig) {
  if (!settings.value) return
  settings.value.providers = settings.value.providers.map((provider) =>
    provider.provider === saved.provider ? saved : provider,
  )
  applyProvider(saved)
}

async function syncProvider(provider: MemberPlatformConfig) {
  syncingProvider.value = provider.provider
  try {
    const result = await adminApi.syncMemberPlatform(provider.provider)
    Message.success(
      result.remoteCount >= 0
        ? t('admin.memberships.syncOneDoneWithRemote', {
            provider: providerLabel(provider.provider),
            count: result.records,
            remote: result.remoteCount,
          })
        : t('admin.memberships.syncOneDone', {
            provider: providerLabel(provider.provider),
            count: result.records,
          }),
    )
    await Promise.all([loadTransactions(), loadReconciliation()])
  } catch (error) {
    Message.error(
      errorMessage(
        error,
        t('admin.memberships.syncOneFailed', {
          provider: providerLabel(provider.provider),
        }),
      ),
    )
  } finally {
    syncingProvider.value = ''
  }
}

async function saveCurrency() {
  savingCurrency.value = true
  try {
    const result = await adminApi.updateMembershipCurrency({
      baseCurrency: currencyForm.baseCurrency.trim(),
      exchangeRateApiUrl: currencyForm.exchangeRateApiUrl.trim(),
    })
    if (settings.value) {
      settings.value.baseCurrency = result.baseCurrency
      settings.value.exchangeRateApiUrl = result.exchangeRateApiUrl
    }
    Message.success(
      t('admin.memberships.currencySaved', {
        count: result.recalculatedTransactions,
      }),
    )
    await loadTransactions()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingCurrency.value = false
  }
}

async function syncAll() {
  syncing.value = true
  const results = await Promise.allSettled([
    adminApi.syncMemberPlatforms(),
    adminApi.syncAfdian(),
  ])
  const succeeded = results.filter((result) => result.status === 'fulfilled').length
  if (succeeded) {
    Message.success(t('admin.memberships.syncFinished', { count: succeeded }))
  } else {
    Message.error(t('admin.memberships.syncFailed'))
  }
  await Promise.all([loadTransactions(), loadReconciliation(), refreshProviders()])
  syncing.value = false
}

async function refreshProviders() {
  try {
    const [data, afdian] = await Promise.all([
      adminApi.memberPlatformSettings(),
      adminApi.afdianSettings(),
    ])
    settings.value = data
    applyAfdian(afdian)
    data.providers.forEach(applyProvider)
  } catch {
    // The table refresh remains useful even when provider settings fail.
  }
}

async function loadTransactions() {
  transactionPage.value = await adminApi.memberPlatformTransactions({
    provider: filterProvider.value || undefined,
    keyword: filterKeyword.value.trim() || undefined,
    type: filterType.value || undefined,
    status: filterStatus.value.trim() || undefined,
    remotePresent:
      filterRemotePresent.value === ''
        ? undefined
        : filterRemotePresent.value === 'true',
    page: filterPage.value,
    size: filterSize.value,
  })
}

async function searchTransactions() {
  filterPage.value = 1
  try {
    await loadTransactions()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

async function changePage(page: number) {
  filterPage.value = page
  try {
    await loadTransactions()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

async function loadReconciliation() {
  reconciliation.value = await adminApi.memberPlatformReconciliation({
    provider: filterProvider.value || undefined,
    limit: 50,
  })
}

async function changeProviderFilter(value: unknown) {
  filterProvider.value = value ? String(value) : ''
  filterPage.value = 1
  try {
    await Promise.all([loadTransactions(), loadReconciliation()])
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

function providerLabel(provider: string) {
  return t(`memberships.providers.${provider}`)
}

function webhookUrl(provider: string) {
  return `${window.location.origin}/api/integrations/memberships/${provider}`
}

function fieldLabel(key: string) {
  if (key === 'webhookToken') return t('admin.memberships.webhookToken')
  if (key === 'accountId') return t('admin.memberships.accountId')
  return t('admin.memberships.accessToken')
}

function fieldPlaceholder(provider: string, key: string) {
  return t(`admin.memberships.fields.${provider}.${key}Placeholder`)
}

function fieldHelp(provider: string, key: string) {
  return t(`admin.memberships.fields.${provider}.${key}Help`)
}

function runStatusColor(status: string | null) {
  if (status === 'COMPLETED') return 'green'
  if (status === 'RUNNING') return 'arcoblue'
  if (status === 'FAILED') return 'red'
  if (status === 'INCOMPLETE') return 'orange'
  return 'gray'
}

function transactionRowClass(record: MemberTransactionAdmin) {
  return record.remotePresent ? '' : 'remote-missing-row'
}

onMounted(load)
</script>

<template>
  <div class="page-stack membership-admin-page">
    <PageHeader
      :title="t('admin.memberships.title')"
      :description="t('admin.memberships.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
        <a-button type="primary" :loading="syncing" @click="syncAll">
          <template #icon><IconSync /></template>
          {{ t('admin.memberships.syncAll') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      {{ t('admin.memberships.integrationHint') }}
    </a-alert>

    <a-spin :loading="loading" class="admin-spin">
      <div class="provider-grid">
        <a-card class="surface-card provider-card">
          <template #title>
            <div class="provider-title">
              <strong>{{ t('memberships.providers.AFDIAN') }}</strong>
              <a-tag color="green">{{ t('admin.memberships.historySync') }}</a-tag>
              <a-tag :color="afdianData?.configured ? 'arcoblue' : 'gray'">
                {{
                  afdianData?.configured
                    ? t('admin.memberships.ready')
                    : t('admin.memberships.notConfigured')
                }}
              </a-tag>
              <a-tag v-if="afdianData?.tokenConfigured" color="green">
                {{ t('admin.afdian.tokenConfigured') }}
              </a-tag>
            </div>
          </template>
          <a-form :model="afdianForm" layout="vertical">
            <a-form-item :label="t('admin.afdian.userId')">
              <a-input v-model="afdianForm.userId" :max-length="128" />
            </a-form-item>
            <a-form-item :label="t('admin.afdian.apiBaseUrl')">
              <a-input v-model="afdianForm.apiBaseUrl" :max-length="2048" />
            </a-form-item>
            <a-form-item :label="t('admin.afdian.accessToken')">
              <a-input-password
                v-model="afdianForm.accessToken"
                :max-length="4096"
                :placeholder="
                  afdianData?.tokenConfigured
                    ? t('admin.afdian.tokenKeepPlaceholder')
                    : t('admin.afdian.tokenPlaceholder')
                "
              />
              <template #extra>
                <a-checkbox
                  v-if="afdianData?.tokenConfigured"
                  v-model="afdianForm.clearToken"
                >
                  {{ t('admin.afdian.clearToken') }}
                </a-checkbox>
              </template>
            </a-form-item>
            <div class="provider-switch">
              <a-switch v-model="afdianForm.syncEnabled" />
              <span>{{ t('admin.afdian.syncEnabled') }}</span>
            </div>
            <div class="provider-status">
              <span>
                {{ t('admin.memberships.lastSync') }}:
                {{ formatDate(afdianData?.lastSyncedAt) }}
              </span>
            </div>
            <div class="provider-actions">
              <a-button type="primary" :loading="savingAfdian" @click="saveAfdian">
                <template #icon><IconSave /></template>
                {{ t('admin.common.save') }}
              </a-button>
              <a-button
                :loading="syncingProvider === 'AFDIAN'"
                :disabled="!afdianData?.configured"
                @click="syncAfdianNow"
              >
                <template #icon><IconSync /></template>
                {{ t('admin.memberships.syncNow') }}
              </a-button>
            </div>
          </a-form>
        </a-card>

        <a-card class="surface-card provider-card">
          <template #title>
            <div class="provider-title">
              <strong>{{ t('admin.memberships.licenseTitle') }}</strong>
              <a-tag :color="licenseData?.privateKeyConfigured ? 'green' : 'gray'">
                {{
                  licenseData?.privateKeyConfigured
                    ? t('admin.memberships.ready')
                    : t('admin.memberships.notConfigured')
                }}
              </a-tag>
            </div>
          </template>
          <a-form :model="licenseForm" layout="vertical">
            <a-form-item :label="t('admin.afdian.donationLicenseAmount')" required>
              <a-input
                v-model="licenseForm.donationLicenseAmount"
                :add-before="licenseData?.baseCurrency || 'CNY'"
                :max-length="32"
              />
              <template #extra>{{ t('admin.afdian.donationLicenseHelp') }}</template>
            </a-form-item>
            <a-form-item :label="t('admin.afdian.validYears')" required>
              <a-input-number v-model="licenseForm.validYears" :min="1" :max="1000" />
            </a-form-item>
            <a-form-item :label="t('admin.afdian.privateKey')">
              <a-textarea
                v-model="licenseForm.privateKey"
                :auto-size="{ minRows: 3, maxRows: 8 }"
                :placeholder="
                  licenseData?.privateKeyConfigured
                    ? t('admin.afdian.privateKeyKeepPlaceholder')
                    : t('admin.afdian.privateKeyPlaceholder')
                "
              />
              <template #extra>
                <a-checkbox
                  v-if="licenseData?.privateKeyConfigured"
                  v-model="licenseForm.clearPrivateKey"
                >
                  {{ t('admin.afdian.clearPrivateKey') }}
                </a-checkbox>
              </template>
            </a-form-item>
            <a-button type="primary" :loading="savingLicense" @click="saveLicense">
              <template #icon><IconSave /></template>
              {{ t('admin.common.save') }}
            </a-button>
          </a-form>
        </a-card>
      </div>

      <a-card class="surface-card">
        <template #title>{{ t('admin.memberships.currencySettings') }}</template>
        <div class="settings-grid">
          <a-form-item :label="t('admin.memberships.baseCurrency')">
            <a-input v-model="currencyForm.baseCurrency" :max-length="8" />
          </a-form-item>
          <a-form-item
            :label="t('admin.memberships.rateApi', { currency: '{currency}' })"
          >
            <a-input v-model="currencyForm.exchangeRateApiUrl" :max-length="2048" />
          </a-form-item>
        </div>
        <a-alert type="warning" show-icon class="inline-alert">
          {{ t('admin.memberships.currencyRecalculateHint') }}
        </a-alert>
        <a-button type="primary" :loading="savingCurrency" @click="saveCurrency">
          <template #icon><IconSave /></template>
          {{ t('admin.memberships.saveCurrency') }}
        </a-button>
      </a-card>

      <div class="provider-grid">
        <a-card
          v-for="provider in settings?.providers ?? []"
          :key="provider.provider"
          class="surface-card provider-card"
        >
          <template #title>
            <div class="provider-title">
              <strong>{{ providerLabel(provider.provider) }}</strong>
              <a-tag v-if="!provider.supported" color="gray">
                {{ t('admin.memberships.unsupported') }}
              </a-tag>
              <a-tag v-else-if="provider.historySync" color="green">
                {{ t('admin.memberships.historySync') }}
              </a-tag>
              <a-tag v-else color="orange">
                {{ t('admin.memberships.webhookOnly') }}
              </a-tag>
              <a-tag :color="provider.enabled ? 'green' : 'gray'">
                {{ provider.enabled ? t('admin.common.enabled') : t('admin.common.disabled') }}
              </a-tag>
              <a-tag v-if="provider.ready" color="arcoblue">
                {{ t('admin.memberships.ready') }}
              </a-tag>
              <a
                v-if="provider.documentationUrl"
                class="docs-link"
                :href="provider.documentationUrl"
                target="_blank"
                rel="noreferrer"
              >
                <IconLink />
                {{ t('admin.memberships.docs') }}
              </a>
            </div>
          </template>

          <a-alert
            v-if="!provider.supported"
            type="warning"
            show-icon
            class="inline-alert"
          >
            {{ t('admin.memberships.unsupportedHint', { provider: providerLabel(provider.provider) }) }}
          </a-alert>

          <a-form
            v-else-if="providerForms[provider.provider]"
            :model="providerForms[provider.provider]"
            layout="vertical"
          >
            <div class="provider-switch">
              <a-switch v-model="providerForms[provider.provider].enabled" />
              <span>{{ t('admin.memberships.enabled') }}</span>
            </div>

            <template
              v-for="field in provider.configFields"
              :key="field"
            >
              <a-form-item
                v-if="field === 'accountId'"
                :label="fieldLabel(field)"
              >
                <a-input
                  v-model="providerForms[provider.provider].accountId"
                  :max-length="255"
                  :placeholder="fieldPlaceholder(provider.provider, field)"
                />
                <template #extra>
                  {{ fieldHelp(provider.provider, field) }}
                </template>
              </a-form-item>

              <a-form-item
                v-else-if="field === 'accessToken'"
                :label="fieldLabel(field)"
              >
                <a-input-password
                  v-model="providerForms[provider.provider].accessToken"
                  :max-length="4096"
                  :placeholder="
                    provider.accessTokenConfigured
                      ? t('admin.memberships.credentialKeep')
                      : fieldPlaceholder(provider.provider, field)
                  "
                />
                <template #extra>
                  <div class="field-extra">
                    <span>{{ fieldHelp(provider.provider, field) }}</span>
                    <a-checkbox
                      v-if="provider.accessTokenConfigured"
                      v-model="providerForms[provider.provider].clearAccessToken"
                    >
                      {{ t('admin.memberships.clearCredential') }}
                    </a-checkbox>
                  </div>
                </template>
              </a-form-item>

              <a-form-item
                v-else-if="field === 'webhookToken'"
                :label="fieldLabel(field)"
              >
                <a-input-password
                  v-model="providerForms[provider.provider].webhookToken"
                  :max-length="4096"
                  :placeholder="
                    provider.webhookTokenConfigured
                      ? t('admin.memberships.webhookKeep')
                      : fieldPlaceholder(provider.provider, field)
                  "
                />
                <template #extra>
                  <div class="field-extra">
                    <span>{{ fieldHelp(provider.provider, field) }}</span>
                    <a-checkbox
                      v-if="provider.webhookTokenConfigured"
                      v-model="providerForms[provider.provider].clearWebhookToken"
                    >
                      {{ t('admin.memberships.clearWebhook') }}
                    </a-checkbox>
                  </div>
                </template>
              </a-form-item>
            </template>

            <div class="compact-grid">
              <a-form-item :label="t('admin.memberships.paymentCurrency')">
                <a-input
                  v-model="providerForms[provider.provider].currency"
                  :max-length="8"
                />
              </a-form-item>
              <a-form-item :label="t('admin.memberships.fallbackRate')">
                <a-input
                  v-model="providerForms[provider.provider].exchangeRate"
                  :max-length="32"
                />
              </a-form-item>
            </div>

            <div v-if="provider.webhook" class="endpoint-row">
              <span>{{ t('admin.memberships.webhookUrl') }}</span>
              <code>{{ webhookUrl(provider.provider) }}</code>
            </div>

            <div class="provider-status">
              <span>
                {{ t('admin.memberships.lastRun') }}:
                <a-tag :color="runStatusColor(provider.lastRunStatus)">
                  {{ provider.lastRunStatus || t('admin.memberships.runNever') }}
                </a-tag>
                {{ formatDate(provider.lastRunAt) }}
              </span>
              <span>
                {{ t('admin.memberships.lastSync') }}: {{ formatDate(provider.lastSyncedAt) }}
              </span>
            </div>
            <a-alert
              v-if="provider.lastRunError"
              type="error"
              show-icon
              class="inline-alert"
            >
              {{ provider.lastRunError }}
            </a-alert>
            <a-alert
              v-if="provider.webhook"
              type="info"
              show-icon
              class="inline-alert"
            >
              {{ t('admin.memberships.webhookHint', { provider: providerLabel(provider.provider) }) }}
            </a-alert>

            <div class="provider-actions">
              <a-button
                type="primary"
                :loading="savingProvider === provider.provider"
                @click="saveProvider(provider)"
              >
                <template #icon><IconSave /></template>
                {{ t('admin.common.save') }}
              </a-button>
              <a-button
                v-if="provider.historySync"
                :loading="syncingProvider === provider.provider"
                :disabled="!provider.ready"
                @click="syncProvider(provider)"
              >
                <template #icon><IconSync /></template>
                {{ t('admin.memberships.syncNow') }}
              </a-button>
            </div>
          </a-form>
        </a-card>
      </div>

      <a-card class="surface-card">
        <template #title>{{ t('admin.memberships.queryTitle') }}</template>
        <div class="query-bar">
          <a-select
            :model-value="filterProvider"
            :options="[{ label: t('admin.common.all'), value: '' }, ...providerOptions]"
            :placeholder="t('admin.memberships.provider')"
            allow-clear
            @change="changeProviderFilter"
          />
          <a-input
            v-model="filterKeyword"
            :placeholder="t('admin.memberships.keywordPlaceholder')"
            allow-clear
            @press-enter="searchTransactions"
          />
          <a-select v-model="filterType" :placeholder="t('admin.memberships.type')" allow-clear>
            <a-option value="PRODUCT">{{ t('admin.memberships.productType') }}</a-option>
            <a-option value="DONATION">{{ t('admin.memberships.donationType') }}</a-option>
          </a-select>
          <a-select
            v-model="filterRemotePresent"
            :placeholder="t('admin.memberships.remote')"
            allow-clear
          >
            <a-option value="true">{{ t('admin.memberships.remotePresent') }}</a-option>
            <a-option value="false">{{ t('admin.memberships.remoteMissing') }}</a-option>
          </a-select>
          <a-input
            v-model="filterStatus"
            :placeholder="t('admin.memberships.status')"
            allow-clear
            @press-enter="searchTransactions"
          />
          <a-button type="primary" @click="searchTransactions">
            {{ t('admin.common.search') }}
          </a-button>
        </div>
        <a-table
          :columns="transactionColumns()"
          :data="transactionPage?.items ?? []"
          :pagination="false"
          :scroll="{ x: 1540 }"
          :row-class="transactionRowClass"
          row-key="id"
        >
          <template #empty><a-empty :description="t('common.noData')" /></template>
          <template #provider="{ record }">
            {{ providerLabel(record.provider) }}
          </template>
          <template #user="{ record }">
            <div class="stack-cell">
              <strong>{{ record.externalUserName || '-' }}</strong>
              <span>{{ record.externalUserId }}</span>
            </div>
          </template>
          <template #product="{ record }">
            <div class="stack-cell">
              <strong>{{ record.productName || '-' }}</strong>
              <span>{{ record.sku || '-' }}</span>
            </div>
          </template>
          <template #amount="{ record }">
            <div class="stack-cell">
              <strong>{{ record.currency }} {{ record.amount }}</strong>
              <span>{{ record.baseCurrency }} {{ record.amountBase }}</span>
            </div>
          </template>
          <template #remote="{ record }">
            <a-tag :color="record.remotePresent ? 'green' : 'red'">
              {{
                record.remotePresent
                  ? t('admin.memberships.remotePresent')
                  : t('admin.memberships.remoteMissing')
              }}
            </a-tag>
          </template>
          <template #occurredAt="{ record }">{{ formatDate(record.occurredAt) }}</template>
          <template #lastReconciledAt="{ record }">
            {{ formatDate(record.lastReconciledAt) }}
          </template>
        </a-table>
        <a-pagination
          class="pager"
          :current="transactionPage?.page ?? 1"
          :page-size="transactionPage?.size ?? filterSize"
          :total="transactionPage?.total ?? 0"
          show-total
          @change="changePage"
        />
      </a-card>

      <a-card class="surface-card">
        <template #title>{{ t('admin.memberships.reconciliationTitle') }}</template>
        <a-table
          :columns="runColumns()"
          :data="reconciliation.runs"
          :pagination="false"
          :scroll="{ x: 1260 }"
          row-key="id"
        >
          <template #empty><a-empty :description="t('common.noData')" /></template>
          <template #startedAt="{ record }">
            {{ formatDate(record.startedAt) }}
          </template>
        </a-table>

        <a-divider>{{ t('admin.memberships.auditTitle') }}</a-divider>
        <a-table
          :columns="auditColumns()"
          :data="reconciliation.events"
          :pagination="false"
          :scroll="{ x: 920 }"
          row-key="id"
        >
          <template #empty><a-empty :description="t('common.noData')" /></template>
          <template #changedFields="{ record }">
            {{ record.changedFields.join(', ') || '-' }}
          </template>
          <template #createdAt="{ record }">
            {{ formatDate(record.createdAt) }}
          </template>
        </a-table>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.membership-admin-page {
  width: min(1280px, 100%);
}

.admin-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.settings-grid,
.compact-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.inline-alert {
  margin-bottom: 14px;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.provider-title,
.endpoint-row,
.provider-actions,
.provider-status,
.provider-switch,
.field-extra {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.provider-switch {
  margin-bottom: 14px;
  color: var(--color-text-2);
  font-size: 12px;
}

.docs-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: rgb(var(--arcoblue-6));
  font-size: 12px;
}

.endpoint-row {
  margin-bottom: 14px;
  padding: 9px;
  color: var(--color-text-3);
  background: var(--color-fill-1);
  border-radius: 7px;
  font-size: 11px;
}

.endpoint-row code {
  overflow-wrap: anywhere;
  color: var(--color-text-2);
}

.provider-status {
  margin-bottom: 12px;
  color: var(--color-text-3);
  font-size: 11px;
}

.provider-actions {
  gap: 10px;
}

.field-extra {
  justify-content: space-between;
  width: 100%;
}

.query-bar {
  display: grid;
  grid-template-columns: 150px minmax(180px, 1fr) 140px 150px minmax(130px, 0.7fr) auto;
  gap: 9px;
  margin-bottom: 14px;
}

.stack-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.stack-cell span {
  color: var(--color-text-3);
  font-size: 11px;
  overflow-wrap: anywhere;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

:deep(.remote-missing-row) {
  opacity: 0.62;
}

@media (max-width: 1080px) {
  .provider-grid,
  .query-bar {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .settings-grid,
  .compact-grid {
    grid-template-columns: 1fr;
  }
}
</style>
