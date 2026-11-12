<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal } from '@arco-design/web-vue'
import {
  IconCopy,
  IconHeart,
  IconLink,
  IconRefresh,
  IconSafe,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type {
  AfdianBinding,
  PbhLicense,
  PbhLicenseAvailability,
} from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(true)
const bindingLoading = ref(false)
const generating = ref(false)
const binding = ref<AfdianBinding | null>(null)
const licenses = ref<PbhLicenseAvailability | null>(null)
const orderNo = ref('')

const availableSlots = computed(() => licenses.value?.totalAvailable ?? 0)

async function load() {
  loading.value = true
  try {
    const [bindingData, licenseData] = await Promise.all([
      uiApi.afdianBinding(),
      uiApi.afdianLicenses(),
    ])
    binding.value = bindingData
    licenses.value = licenseData
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function bind() {
  const value = orderNo.value.trim()
  if (!value) {
    Message.warning(t('afdian.orderRequired'))
    return
  }
  bindingLoading.value = true
  try {
    binding.value = await uiApi.bindAfdian(value)
    orderNo.value = ''
    licenses.value = await uiApi.afdianLicenses()
    Message.success(t('afdian.bound'))
  } catch (error) {
    Message.error(errorMessage(error, t('afdian.bindFailed')))
  } finally {
    bindingLoading.value = false
  }
}

function confirmUnbind() {
  Modal.warning({
    title: t('afdian.unbindTitle'),
    content: t('afdian.unbindConfirm'),
    hideCancel: false,
    onOk: async () => {
      try {
        await uiApi.unbindAfdian()
        Message.success(t('afdian.unbound'))
        await load()
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
      }
    },
  })
}

function generateLicense() {
  if (!availableSlots.value) return
  Modal.confirm({
    title: t('afdian.generateTitle'),
    content: t('afdian.generateConfirm', {
      product: licenses.value?.availableProductLicenses ?? 0,
      donation: licenses.value?.availableDonationLicenses ?? 0,
    }),
    okText: t('afdian.generate'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      generating.value = true
      try {
        const license = await uiApi.generateAfdianLicense()
        licenses.value = await uiApi.afdianLicenses()
        Message.success(t('afdian.generated'))
        await copy(license)
      } catch (error) {
        Message.error(errorMessage(error, t('afdian.generateFailed')))
      } finally {
        generating.value = false
      }
    },
  })
}

async function copy(license: PbhLicense) {
  try {
    await navigator.clipboard.writeText(license.licenseKey)
    Message.success(t('afdian.licenseCopied'))
  } catch {
    Message.warning(t('afdian.copyFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="afdian-page">
    <PageHeader :title="t('afdian.title')" :description="t('afdian.description')">
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('common.retry') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :loading="loading" class="afdian-spin">
      <a-empty
        v-if="!loading && binding && !binding.configured"
        :description="t('afdian.unavailable')"
      />
      <a-card
        v-else-if="binding && !binding.bound"
        class="surface-card bind-card"
      >
        <template #title>
          <span class="card-title"><IconLink />{{ t('afdian.bindTitle') }}</span>
        </template>
        <a-alert type="info" show-icon>
          {{ t('afdian.bindHint') }}
        </a-alert>
        <a-form :model="{ orderNo }" layout="vertical" class="bind-form" @submit="bind">
          <a-form-item :label="t('afdian.orderNo')" required>
            <a-input
              v-model="orderNo"
              :max-length="128"
              :placeholder="t('afdian.orderPlaceholder')"
            />
          </a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            :loading="bindingLoading"
          >
            <template #icon><IconHeart /></template>
            {{ t('afdian.bind') }}
          </a-button>
        </a-form>
      </a-card>

      <template v-else-if="binding">
        <a-card class="surface-card account-card">
          <div class="afdian-account">
            <a-avatar :size="64" :image-url="binding.afdianAvatar ?? undefined">
              <IconHeart />
            </a-avatar>
            <div class="account-copy">
              <h2>{{ binding.afdianName || t('afdian.unnamed') }}</h2>
              <code>{{ binding.afdianUserId }}</code>
              <span>
                {{ t('afdian.boundAt', { time: formatDate(binding.boundAt) }) }}
              </span>
              <span>
                {{ t('afdian.lastSync', { time: formatDate(binding.lastSyncedAt) }) }}
              </span>
            </div>
            <div class="account-total">
              <span>{{ t('afdian.totalDonation') }}</span>
              <strong>
                <small>CNY</small>
                {{ binding.totalAmount }}
              </strong>
              <span>{{ t('afdian.donationCount', { count: binding.donationCount }) }}</span>
            </div>
          </div>
          <div class="account-actions">
            <a-button status="danger" @click="confirmUnbind">
              {{ t('afdian.unbind') }}
            </a-button>
          </div>
        </a-card>

        <a-card class="surface-card license-card">
          <template #title>
            <span class="card-title">
              <IconSafe />
              {{ t('afdian.licenseTitle') }}
            </span>
          </template>
          <div class="slot-grid">
            <div>
              <span>{{ t('afdian.productSlots') }}</span>
              <strong>{{ licenses?.availableProductLicenses ?? 0 }}</strong>
            </div>
            <div>
              <span>{{ t('afdian.donationSlots') }}</span>
              <strong>{{ licenses?.availableDonationLicenses ?? 0 }}</strong>
            </div>
            <div>
              <span>{{ t('afdian.donationUnit') }}</span>
              <strong>
                {{ licenses?.baseCurrency }} {{ licenses?.donationLicenseAmount }}
              </strong>
            </div>
            <div>
              <span>{{ t('afdian.availableSlots') }}</span>
              <strong>{{ availableSlots }}</strong>
            </div>
          </div>
          <a-alert type="info" show-icon class="license-hint">
            {{
              t('afdian.licenseHint', {
                currency: licenses?.baseCurrency,
                years: licenses?.validYears,
              })
            }}
          </a-alert>
          <a-button
            type="primary"
            size="large"
            :disabled="availableSlots <= 0 || !licenses?.configured"
            :loading="generating"
            @click="generateLicense"
          >
            <template #icon><IconSafe /></template>
            {{ t('afdian.generate') }}
          </a-button>

          <div v-if="licenses?.licenses.length" class="license-list">
            <article
              v-for="license in licenses.licenses"
              :key="license.licenseId"
              class="license-item"
            >
              <div class="license-meta">
                <a-tag :color="license.sourceType === 'PRODUCT' ? 'purple' : 'orange'">
                  {{ t(`afdian.sources.${license.sourceType}`) }}
                </a-tag>
                <span>{{ formatDate(license.issuedAt) }}</span>
                <span>{{ t('afdian.expiresAt', { time: formatDate(license.expireAt) }) }}</span>
                <a-tag v-if="license.revokedAt" color="red">{{ t('afdian.revoked') }}</a-tag>
              </div>
              <code class="license-key">{{ license.licenseKey }}</code>
              <a-button size="small" @click="copy(license)">
                <template #icon><IconCopy /></template>
                {{ t('common.copy') }}
              </a-button>
            </article>
          </div>
        </a-card>

        <a-card class="surface-card donation-card">
          <template #title>{{ t('afdian.donations') }}</template>
          <a-empty
            v-if="!binding.donations.length"
            :description="t('afdian.noDonations')"
          />
          <div v-else class="donation-list">
            <div
              v-for="donation in binding.donations"
              :key="donation.outTradeNo"
              class="donation-row"
            >
              <div>
                <div class="donation-title">
                  <a-tag :color="donation.type === 'PRODUCT' ? 'purple' : 'orange'">
                    {{ t(`memberships.types.${donation.type}`) }}
                  </a-tag>
                  <strong>{{ donation.planTitle || t('afdian.donation') }}</strong>
                  <a-tag v-if="!donation.remotePresent" color="red">
                    {{ t('memberships.remoteMissing') }}
                  </a-tag>
                </div>
                <span>{{ donation.remark || donation.outTradeNo }}</span>
              </div>
              <div class="donation-value">
                <strong>CNY {{ donation.amount }}</strong>
                <span>{{ donation.status }} · {{ formatDate(donation.createdAt) }}</span>
              </div>
            </div>
          </div>
        </a-card>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.afdian-page {
  width: 100%;
}

.afdian-spin {
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

.bind-form {
  max-width: 620px;
  margin-top: 18px;
}

.afdian-account {
  display: flex;
  align-items: center;
  gap: 18px;
}

.account-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.account-copy h2 {
  margin: 0;
}

.account-copy code,
.account-copy span {
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
  font-size: 28px;
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

.license-hint {
  margin-bottom: 16px;
}

.license-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;
}

.license-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding: 13px;
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

.license-key {
  grid-column: 1 / -1;
  overflow-wrap: anywhere;
  color: var(--color-text-2);
  font-size: 11px;
  line-height: 1.6;
}

.donation-list {
  display: flex;
  flex-direction: column;
}

.donation-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 0;
  border-bottom: 1px solid var(--color-border-2);
}

.donation-row:last-child {
  border-bottom: 0;
}

.donation-row > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.donation-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.donation-row span {
  color: var(--color-text-3);
  font-size: 11px;
  overflow-wrap: anywhere;
}

.donation-value {
  align-items: flex-end;
  text-align: right;
}

@media (max-width: 720px) {
  .afdian-account {
    align-items: flex-start;
    flex-direction: column;
  }

  .account-total {
    align-items: flex-start;
    width: 100%;
  }

  .slot-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
