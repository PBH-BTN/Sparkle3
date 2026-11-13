<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconHeart,
  IconRefresh,
  IconSave,
  IconSync,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  AfdianAdminBinding,
  AfdianAdminDonation,
  AfdianAdminLicense,
  AfdianSettings,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(true)
const saving = ref(false)
const syncing = ref(false)
const data = ref<AfdianSettings | null>(null)
const form = reactive({
  userId: '',
  accessToken: '',
  clearToken: false,
  apiBaseUrl: 'https://afdian.com/api/open',
  syncEnabled: true,
  licenseDonationAmount: '50',
  licenseValidYears: 100,
  licensePrivateKey: '',
  clearLicensePrivateKey: false,
})

const bindingColumns = (): TableColumnData[] => [
  { title: t('admin.afdian.btnUser'), slotName: 'btnUser', width: 190 },
  { title: t('admin.afdian.afdianUser'), slotName: 'afdianUser', width: 190 },
  { title: t('admin.afdian.total'), dataIndex: 'totalAmount', width: 110 },
  { title: t('admin.afdian.orderCount'), dataIndex: 'donationCount', width: 110 },
  { title: t('admin.afdian.boundAt'), slotName: 'boundAt', width: 160 },
  { title: t('admin.afdian.lastSync'), slotName: 'lastSyncedAt', width: 160 },
  { title: t('admin.afdian.error'), dataIndex: 'lastError', width: 220 },
]

const donationColumns = (): TableColumnData[] => [
  { title: t('admin.afdian.orderNo'), dataIndex: 'outTradeNo', width: 210 },
  { title: t('admin.afdian.afdianUser'), slotName: 'afdianUser', width: 180 },
  { title: 'Plan ID', dataIndex: 'planId', width: 150 },
  { title: t('admin.afdian.plan'), dataIndex: 'planTitle', width: 180 },
  { title: t('admin.afdian.amount'), slotName: 'amount', width: 110 },
  { title: t('admin.afdian.createdAt'), slotName: 'createdAt', width: 160 },
]

const licenseColumns = (): TableColumnData[] => [
  { title: t('admin.afdian.btnUser'), slotName: 'btnUser', width: 190 },
  { title: t('admin.afdian.afdianUser'), dataIndex: 'afdianUserId', width: 180 },
  { title: t('admin.afdian.source'), dataIndex: 'sourceType', width: 100 },
  { title: t('admin.afdian.plan'), slotName: 'plan', width: 180 },
  { title: t('admin.afdian.issuedAt'), slotName: 'issuedAt', width: 160 },
  { title: t('admin.afdian.expiresAt'), slotName: 'expireAt', width: 160 },
]

async function load() {
  loading.value = true
  try {
    apply(await adminApi.afdianSettings())
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function apply(settings: AfdianSettings) {
  data.value = settings
  Object.assign(form, {
    userId: settings.userId,
    accessToken: '',
    clearToken: false,
    apiBaseUrl: settings.apiBaseUrl,
    syncEnabled: settings.syncEnabled,
    licenseDonationAmount: settings.licenseDonationAmount,
    licenseValidYears: settings.licenseValidYears,
    licensePrivateKey: '',
    clearLicensePrivateKey: false,
  })
}

async function save() {
  saving.value = true
  try {
    apply(
      await adminApi.updateAfdianSettings({
        userId: form.userId.trim(),
        accessToken: form.accessToken.trim() || undefined,
        clearToken: form.clearToken,
        apiBaseUrl: form.apiBaseUrl.trim(),
        syncEnabled: form.syncEnabled,
        licenseDonationAmount: form.licenseDonationAmount.trim(),
        licenseValidYears: form.licenseValidYears,
        licensePrivateKey: form.licensePrivateKey.trim() || undefined,
        clearLicensePrivateKey: form.clearLicensePrivateKey,
      }),
    )
    Message.success(t('admin.afdian.saved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    saving.value = false
  }
}

async function sync() {
  syncing.value = true
  try {
    const result = await adminApi.syncAfdian()
    if (result.success) {
      Message.success(
        t('admin.afdian.syncDone', {
          pages: result.pages,
          orders: result.ordersSeen,
          bindings: result.bindingsUpdated,
        }),
      )
    } else {
      Message.warning(result.message)
    }
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('admin.afdian.syncFailed')))
  } finally {
    syncing.value = false
  }
}

function displayAfdianUser(row: AfdianAdminBinding) {
  return row.afdianName || row.afdianUserId
}

function displayDonationUser(row: AfdianAdminDonation) {
  return row.afdianUserName || row.afdianUserId
}

onMounted(load)
</script>

<template>
  <div class="page-stack afdian-admin-page">
    <PageHeader :title="t('admin.afdian.title')" :description="t('admin.afdian.description')">
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
        <a-button
          type="primary"
          :loading="syncing"
          :disabled="!data?.configured"
          @click="sync"
        >
          <template #icon><IconSync /></template>
          {{ t('admin.afdian.syncNow') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      {{ t('admin.afdian.cacheHint') }}
    </a-alert>

    <a-spin :loading="loading" class="afdian-admin-spin">
      <a-card class="surface-card">
        <template #title>{{ t('admin.afdian.apiSettings') }}</template>
        <a-form :model="form" layout="vertical">
          <div class="settings-grid">
            <a-form-item :label="t('admin.afdian.apiBaseUrl')" required>
              <a-input v-model="form.apiBaseUrl" :max-length="2048" />
            </a-form-item>
            <a-form-item :label="t('admin.afdian.userId')">
              <a-input v-model="form.userId" :max-length="128" />
            </a-form-item>
            <a-form-item :label="t('admin.afdian.accessToken')">
              <a-input-password
                v-model="form.accessToken"
                :max-length="4096"
                :placeholder="
                  data?.tokenConfigured
                    ? t('admin.afdian.tokenKeepPlaceholder')
                    : t('admin.afdian.tokenPlaceholder')
                "
              />
              <template #extra>
                <div class="token-row">
                  <a-tag :color="data?.tokenConfigured ? 'green' : 'gray'">
                    {{
                      data?.tokenConfigured
                        ? t('admin.afdian.tokenConfigured')
                        : t('admin.afdian.tokenMissing')
                    }}
                  </a-tag>
                  <a-checkbox v-if="data?.tokenConfigured" v-model="form.clearToken">
                    {{ t('admin.afdian.clearToken') }}
                  </a-checkbox>
                </div>
              </template>
            </a-form-item>
            <a-form-item :label="t('admin.afdian.syncEnabled')">
              <a-switch v-model="form.syncEnabled" />
            </a-form-item>
          </div>

          <a-divider>{{ t('admin.afdian.licenseSettings') }}</a-divider>
          <div class="settings-grid">
            <a-form-item :label="t('admin.afdian.donationLicenseAmount')" required>
              <a-input
                v-model="form.licenseDonationAmount"
                :add-before="data?.baseCurrency || 'CNY'"
                :max-length="32"
              />
              <template #extra>{{ t('admin.afdian.donationLicenseHelp') }}</template>
            </a-form-item>
            <a-form-item :label="t('admin.afdian.validYears')" required>
              <a-input-number v-model="form.licenseValidYears" :min="1" :max="1000" />
            </a-form-item>
            <a-form-item :label="t('admin.afdian.privateKey')">
              <a-textarea
                v-model="form.licensePrivateKey"
                :auto-size="{ minRows: 3, maxRows: 8 }"
                :placeholder="
                  data?.licensePrivateKeyConfigured
                    ? t('admin.afdian.privateKeyKeepPlaceholder')
                    : t('admin.afdian.privateKeyPlaceholder')
                "
              />
              <template #extra>
                <div class="token-row">
                  <a-tag :color="data?.licensePrivateKeyConfigured ? 'green' : 'gray'">
                    {{
                      data?.licensePrivateKeyConfigured
                        ? t('admin.afdian.privateKeyConfigured')
                        : t('admin.afdian.privateKeyMissing')
                    }}
                  </a-tag>
                  <a-checkbox
                    v-if="data?.licensePrivateKeyConfigured"
                    v-model="form.clearLicensePrivateKey"
                  >
                    {{ t('admin.afdian.clearPrivateKey') }}
                  </a-checkbox>
                </div>
              </template>
            </a-form-item>
          </div>

          <a-button type="primary" :loading="saving" @click="save">
            <template #icon><IconSave /></template>
            {{ t('admin.common.save') }}
          </a-button>
        </a-form>
      </a-card>

      <a-card class="surface-card">
        <template #title>{{ t('admin.afdian.bindings') }}</template>
        <a-table
          :columns="bindingColumns()"
          :data="data?.bindings ?? []"
          :pagination="false"
          :scroll="{ x: 1180 }"
          row-key="btnUserId"
        >
          <template #empty><a-empty :description="t('common.noData')" /></template>
          <template #btnUser="{ record }">
            <div class="stack-cell">
              <strong>{{ record.btnNickname }}</strong>
              <span>UID {{ record.btnUserId }}</span>
            </div>
          </template>
          <template #afdianUser="{ record }">
            <div class="stack-cell">
              <strong>{{ displayAfdianUser(record) }}</strong>
              <span>{{ record.afdianUserId }}</span>
            </div>
          </template>
          <template #boundAt="{ record }">{{ formatDate(record.boundAt) }}</template>
          <template #lastSyncedAt="{ record }">
            {{ formatDate(record.lastSyncedAt) }}
          </template>
        </a-table>
      </a-card>

      <a-card class="surface-card">
        <template #title>{{ t('admin.afdian.donations') }}</template>
        <a-table
          :columns="donationColumns()"
          :data="data?.recentDonations ?? []"
          :pagination="false"
          :scroll="{ x: 1100 }"
          row-key="outTradeNo"
        >
          <template #empty><a-empty :description="t('common.noData')" /></template>
          <template #afdianUser="{ record }">
            <div class="stack-cell">
              <strong>{{ displayDonationUser(record) }}</strong>
              <span>{{ record.afdianUserId }}</span>
            </div>
          </template>
          <template #amount="{ record }">CNY {{ record.showAmount }}</template>
          <template #createdAt="{ record }">
            {{ formatDate(record.createdAt) }}
          </template>
        </a-table>
      </a-card>

      <a-card class="surface-card">
        <template #title>
          <span class="card-title"><IconHeart />{{ t('admin.afdian.licenses') }}</span>
        </template>
        <a-table
          :columns="licenseColumns()"
          :data="data?.recentLicenses ?? []"
          :pagination="false"
          :scroll="{ x: 1000 }"
          row-key="licenseId"
        >
          <template #empty><a-empty :description="t('common.noData')" /></template>
          <template #btnUser="{ record }">
            <div class="stack-cell">
              <strong>{{ record.btnNickname }}</strong>
              <span>UID {{ record.btnUserId }}</span>
            </div>
          </template>
          <template #plan="{ record }">
            {{ record.planId || record.sourceOrderNo || '-' }}
          </template>
          <template #issuedAt="{ record }">{{ formatDate(record.issuedAt) }}</template>
          <template #expireAt="{ record }">{{ formatDate(record.expireAt) }}</template>
        </a-table>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.afdian-admin-page {
  width: min(1200px, 100%);
}

.afdian-admin-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.token-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
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

@media (max-width: 760px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
