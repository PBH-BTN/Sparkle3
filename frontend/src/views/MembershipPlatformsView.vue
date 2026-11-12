<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal } from '@arco-design/web-vue'
import {
  IconCopy,
  IconLink,
  IconRefresh,
  IconSafe,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type {
  MemberProviderBinding,
  PbhLicense,
  PbhLicenseAvailability,
} from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(true)
const providers = ref<MemberProviderBinding[]>([])
const licenses = ref<PbhLicenseAvailability | null>(null)
const proofs = reactive<Record<string, string>>({})
const busyProvider = ref('')
const generating = ref(false)

const availableSlots = computed(() => licenses.value?.totalAvailable ?? 0)
const configuredProviders = computed(() =>
  providers.value.filter((provider) => provider.configured),
)

async function load() {
  loading.value = true
  try {
    const [providerData, licenseData] = await Promise.all([
      uiApi.memberPlatforms(),
      uiApi.membershipLicenses(),
    ])
    providers.value = providerData
    licenses.value = licenseData
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function bind(provider: string) {
  const proof = proofs[provider]?.trim() ?? ''
  if (!proof) {
    Message.warning(t('memberships.proofRequired'))
    return
  }
  busyProvider.value = provider
  try {
    const binding = await uiApi.bindMemberPlatform(provider, proof)
    providers.value = providers.value.map((item) =>
      item.provider === provider ? binding : item,
    )
    proofs[provider] = ''
    licenses.value = await uiApi.membershipLicenses()
    Message.success(t('memberships.bound'))
  } catch (error) {
    Message.error(errorMessage(error, t('memberships.bindFailed')))
  } finally {
    busyProvider.value = ''
  }
}

function confirmUnbind(provider: MemberProviderBinding) {
  Modal.warning({
    title: t('memberships.unbindTitle', {
      provider: providerName(provider.provider),
    }),
    content: t('memberships.unbindConfirm'),
    hideCancel: false,
    onOk: async () => {
      busyProvider.value = provider.provider
      try {
        await uiApi.unbindMemberPlatform(provider.provider)
        Message.success(t('memberships.unbound'))
        await load()
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
      } finally {
        busyProvider.value = ''
      }
    },
  })
}

function generateLicense() {
  if (!availableSlots.value) return
  Modal.confirm({
    title: t('memberships.generateTitle'),
    content: t('memberships.generateConfirm', {
      product: licenses.value?.availableProductLicenses ?? 0,
      donation: licenses.value?.availableDonationLicenses ?? 0,
    }),
    okText: t('memberships.generate'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      generating.value = true
      try {
        const license = await uiApi.generateMembershipLicense()
        licenses.value = await uiApi.membershipLicenses()
        Message.success(t('memberships.generated'))
        await copy(license)
      } catch (error) {
        Message.error(errorMessage(error, t('memberships.generateFailed')))
      } finally {
        generating.value = false
      }
    },
  })
}

async function copy(license: PbhLicense) {
  try {
    await navigator.clipboard.writeText(license.licenseKey)
    Message.success(t('memberships.licenseCopied'))
  } catch {
    Message.warning(t('memberships.copyFailed'))
  }
}

function providerName(provider: string) {
  return t(`memberships.providers.${provider}`)
}

onMounted(load)
</script>

<template>
  <div class="membership-page">
    <PageHeader
      :title="t('memberships.title')"
      :description="t('memberships.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('common.retry') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :loading="loading" class="membership-spin">
      <a-empty
        v-if="!loading && !configuredProviders.length"
        :description="t('memberships.empty')"
      />

      <template v-if="configuredProviders.length">
        <a-card
          v-if="licenses?.configured || licenses?.totalAvailable || licenses?.licenses.length"
          class="surface-card license-card"
        >
        <template #title>
          <span class="card-title">
            <IconSafe />
            {{ t('memberships.licenseTitle') }}
          </span>
        </template>
        <div class="slot-grid">
          <div>
            <span>{{ t('memberships.productSlots') }}</span>
            <strong>{{ licenses?.availableProductLicenses ?? 0 }}</strong>
          </div>
          <div>
            <span>{{ t('memberships.donationSlots') }}</span>
            <strong>{{ licenses?.availableDonationLicenses ?? 0 }}</strong>
          </div>
          <div>
            <span>{{ t('memberships.donationUnit') }}</span>
            <strong>
              {{ licenses?.baseCurrency }} {{ licenses?.donationLicenseAmount }}
            </strong>
          </div>
          <div>
            <span>{{ t('memberships.availableSlots') }}</span>
            <strong>{{ availableSlots }}</strong>
          </div>
        </div>
        <a-alert type="info" show-icon class="license-hint">
          {{
            t('memberships.licenseHint', {
              currency: licenses?.baseCurrency,
              years: licenses?.validYears,
            })
          }}
        </a-alert>
        <a-button
          type="primary"
          :disabled="availableSlots <= 0 || !licenses?.configured"
          :loading="generating"
          @click="generateLicense"
        >
          <template #icon><IconSafe /></template>
          {{ t('memberships.generate') }}
        </a-button>

        <div v-if="licenses?.licenses.length" class="license-list">
          <article
            v-for="license in licenses.licenses"
            :key="license.licenseId"
            class="license-item"
          >
            <div class="license-meta">
              <a-tag :color="license.sourceType === 'PRODUCT' ? 'purple' : 'orange'">
                {{ t(`memberships.sources.${license.sourceType}`) }}
              </a-tag>
              <span>{{ formatDate(license.issuedAt) }}</span>
              <span>{{ t('memberships.expiresAt', { time: formatDate(license.expireAt) }) }}</span>
            </div>
            <code>{{ license.licenseKey }}</code>
            <a-button size="small" @click="copy(license)">
              <template #icon><IconCopy /></template>
              {{ t('common.copy') }}
            </a-button>
          </article>
        </div>
        </a-card>

        <a-tabs type="rounded" class="provider-tabs">
        <a-tab-pane
          v-for="provider in configuredProviders"
          :key="provider.provider"
          :title="providerName(provider.provider)"
        >
          <a-card class="surface-card">
            <template v-if="provider.bound">
              <div class="provider-account">
                <div>
                  <h2>{{ provider.externalUserName || provider.externalUserId }}</h2>
                  <code>{{ provider.externalUserId }}</code>
                  <span>{{ t('memberships.boundAt', { time: formatDate(provider.boundAt) }) }}</span>
                  <span>
                    {{ t('memberships.lastReconciled', {
                      time: formatDate(provider.lastSyncedAt),
                    }) }}
                  </span>
                  <a-alert
                    v-if="provider.lastError"
                    type="warning"
                    show-icon
                    class="sync-error"
                  >
                    {{ provider.lastError }}
                  </a-alert>
                </div>
                <div class="account-total">
                  <span>{{ t('memberships.totalDonation') }}</span>
                  <strong>
                    <small>{{ provider.baseCurrency }}</small>
                    {{ provider.totalBase }}
                  </strong>
                  <span>
                    {{ t('memberships.donationCount', { count: provider.donationCount }) }}
                  </span>
                </div>
              </div>
              <div class="account-actions">
                <a-button
                  status="danger"
                  :loading="busyProvider === provider.provider"
                  @click="confirmUnbind(provider)"
                >
                  {{ t('memberships.unbind') }}
                </a-button>
              </div>

              <a-divider>{{ t('memberships.transactions') }}</a-divider>
              <a-empty
                v-if="!provider.transactions.length"
                :description="t('memberships.noTransactions')"
              />
              <div v-else class="transaction-list">
                <article
                  v-for="transaction in provider.transactions"
                  :key="transaction.transactionId"
                  class="transaction-row"
                >
                  <div class="transaction-main">
                    <div>
                      <a-tag :color="transaction.type === 'PRODUCT' ? 'purple' : 'orange'">
                        {{ t(`memberships.types.${transaction.type}`) }}
                      </a-tag>
                      <strong>
                        {{ transaction.productName || transaction.sku || transaction.transactionId }}
                      </strong>
                    </div>
                    <code>{{ transaction.transactionId }}</code>
                  </div>
                  <div class="transaction-value">
                    <strong>
                      {{ transaction.currency }} {{ transaction.amount }}
                    </strong>
                    <span v-if="transaction.baseCurrency !== transaction.currency">
                      {{ transaction.baseCurrency }} {{ transaction.amountBase }}
                    </span>
                    <span>{{ formatDate(transaction.occurredAt) }}</span>
                    <div class="transaction-tags">
                      <a-tag color="green">{{ transaction.status }}</a-tag>
                      <a-tag v-if="!transaction.remotePresent" color="red">
                        {{ t('memberships.remoteMissing') }}
                      </a-tag>
                    </div>
                  </div>
                </article>
              </div>
            </template>

            <template v-else>
              <div class="bind-header">
                <IconLink />
                <div>
                  <h2>{{ t('memberships.bindTitle', { provider: providerName(provider.provider) }) }}</h2>
                  <p>{{ t('memberships.bindHint') }}</p>
                </div>
              </div>
              <a-form
                :model="{ proof: proofs[provider.provider] }"
                layout="vertical"
                class="bind-form"
                @submit="bind(provider.provider)"
              >
                <a-form-item :label="t('memberships.proof')" required>
                  <a-input
                    v-model="proofs[provider.provider]"
                    :max-length="255"
                    :placeholder="t('memberships.proofPlaceholder')"
                  />
                </a-form-item>
                <a-button
                  type="primary"
                  html-type="submit"
                  :loading="busyProvider === provider.provider"
                >
                  {{ t('memberships.bind') }}
                </a-button>
              </a-form>
            </template>
          </a-card>
        </a-tab-pane>
        </a-tabs>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.membership-page,
.membership-spin {
  width: 100%;
}

.membership-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.license-card {
  overflow: hidden;
}

.card-title,
.bind-header {
  display: flex;
  align-items: center;
  gap: 9px;
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.slot-grid > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 13px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.slot-grid span {
  color: var(--color-text-3);
  font-size: 11px;
}

.slot-grid strong {
  font-size: 21px;
}

.license-hint,
.sync-error {
  margin: 12px 0;
}

.license-list,
.transaction-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin-top: 18px;
}

.license-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 9px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.license-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--color-text-3);
  font-size: 11px;
}

.license-item code {
  grid-column: 1 / -1;
  overflow-wrap: anywhere;
  font-size: 11px;
}

.provider-tabs :deep(.arco-tabs-content) {
  padding-top: 12px;
}

.provider-account {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.provider-account > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.provider-account h2,
.bind-header h2 {
  margin: 0;
}

.provider-account code,
.provider-account span {
  color: var(--color-text-3);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.account-total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 5px;
  min-width: 190px;
  padding: 16px 20px;
  color: #fff;
  background: linear-gradient(135deg, #d97706, #b45309);
  border-radius: 12px;
}

.account-total span {
  color: rgb(255 255 255 / 74%);
  font-size: 11px;
}

.account-total strong {
  font-size: 27px;
}

.account-total small {
  margin-right: 5px;
  font-size: 11px;
}

.account-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.bind-header {
  align-items: flex-start;
}

.bind-header p {
  max-width: 720px;
  margin: 5px 0 0;
  color: var(--color-text-3);
}

.bind-form {
  max-width: 620px;
  margin-top: 18px;
}

.transaction-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.transaction-main,
.transaction-value {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.transaction-main > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.transaction-main code,
.transaction-value span {
  color: var(--color-text-3);
  font-size: 11px;
  overflow-wrap: anywhere;
}

.transaction-value {
  align-items: flex-end;
  text-align: right;
}

.transaction-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 5px;
}

@media (max-width: 720px) {
  .slot-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .provider-account,
  .transaction-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .account-total {
    align-items: flex-start;
    width: 100%;
  }

  .transaction-value {
    align-items: flex-start;
    text-align: left;
  }
}
</style>
