<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal } from '@arco-design/web-vue'
import { adminApi } from '@/api/admin'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const emit = defineEmits<{ revoked: [] }>()
const visible = ref(false)
const licenseId = ref<string>()
const licenseKey = ref('')
const reason = ref('')
const deleting = ref(false)
const returningOnly = ref(false)
const actionKey = computed(() => deleting.value ? 'admin.memberships.deleteLicense' : returningOnly.value ? 'admin.memberships.returnLicenseSlot' : 'admin.memberships.revokeLicense')
const returnSlot = ref(false)
const slotReturned = ref(false)
const saving = ref(false)
const loading = ref(false)
const page = ref(1)
const data = ref<Awaited<ReturnType<typeof adminApi.licenseRevocations>>>()
function open(id?: string, remove = false, returned = false, revoked = false) {
  returningOnly.value = revoked && !remove
  deleting.value = remove
  returnSlot.value = returningOnly.value && !returned
  slotReturned.value = returned
  licenseId.value = id
  licenseKey.value = ''
  reason.value = ''
  visible.value = true
}
defineExpose({ open })
async function load(nextPage = 1) {
  loading.value = true
  try {
    data.value = await adminApi.licenseRevocations(nextPage)
    page.value = nextPage
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    loading.value = false
  }
}
function confirm() {
  if (!reason.value.trim() || (!licenseId.value && !licenseKey.value.trim())) {
    Message.warning(t('admin.memberships.revocationRequired'))
    return
  }
  Modal.confirm({
    title: t(actionKey.value),
    content: [
      deleting.value ? t('admin.memberships.licenseDeleteConfirm') : '',
      t('admin.memberships.revocationConfirm'),
      licenseId.value ? t(returnSlot.value || slotReturned.value ? 'admin.memberships.slotReturnConfirm' : 'admin.memberships.slotKeepConfirm') : '',
    ].filter(Boolean).join(' '),
    onOk: async () => {
      saving.value = true
      try {
        await adminApi.revokeLicense({
          licenseId: licenseId.value,
          returnSlot: returnSlot.value,
          delete: deleting.value,
          licenseKey: licenseId.value ? undefined : licenseKey.value.trim(),
          reason: reason.value.trim(),
        })
        visible.value = false
        licenseKey.value = ''
        Message.success(t(deleting.value ? 'admin.memberships.licenseDeleted' : 'admin.memberships.revocationSaved'))
        emit('revoked')
        await load()
      } catch (error) {
        Message.error(errorMessage(error, t('common.requestFailed')))
      } finally {
        saving.value = false
      }
    },
  })
}
onMounted(() => load())
</script>

<template>
  <a-card class="surface-card">
    <template #title>{{ t('admin.memberships.revocationHistory') }}</template>
    <a-space wrap class="filter-actions">
      <a-button status="danger" @click="open()">{{ t('admin.memberships.independentLicense') }}</a-button>
      <a-button :loading="loading" @click="load(page)">{{ t('admin.common.search') }}</a-button>
    </a-space>
    <a-table :data="data?.items ?? []" :pagination="false" :loading="loading" row-key="fingerprint" :scroll="{ x: 720 }">
      <template #columns>
        <a-table-column :title="t('admin.memberships.revocationReason')" data-index="reason" />
        <a-table-column :title="t('admin.memberships.revocationActor')" data-index="revokedBy" />
        <a-table-column :title="t('admin.memberships.revoked')">
          <template #cell="{ record }">{{ formatDate(record.revokedAt) }}</template>
        </a-table-column>
      </template>
      <template #empty><a-empty :description="t('common.noData')" /></template>
    </a-table>
    <a-pagination :current="page" :total="data?.total ?? 0" :page-size="20" show-total @change="load" />
  </a-card>
  <a-modal v-model:visible="visible" :title="t(actionKey)" :footer="false" :width="600" :modal-style="{ maxWidth: 'calc(100vw - 32px)' }" :mask-closable="!saving" :closable="!saving">
    <a-form layout="vertical" :model="{ licenseKey, reason }">
      <a-form-item v-if="!licenseId" :label="t('admin.memberships.revocationKey')" required>
        <a-textarea v-model="licenseKey" :max-length="40000" :auto-size="{ minRows: 3, maxRows: 6 }" />
        <template #extra>{{ t('admin.memberships.revocationHelp') }}</template>
      </a-form-item>
      <a-form-item v-else :label="t('admin.memberships.licenseId')">{{ licenseId }}</a-form-item>
      <a-form-item :label="t('admin.memberships.revocationReason')" required>
        <a-textarea v-model="reason" :max-length="500" show-word-limit />
      </a-form-item>
      <a-form-item v-if="licenseId" :label="t('admin.memberships.licenseSlot')">
        <span v-if="slotReturned">{{ t('admin.memberships.slotReturned') }}</span>
        <a-checkbox v-else v-model="returnSlot" :disabled="saving || returningOnly">{{ t('admin.memberships.returnLicenseSlot') }}</a-checkbox>
        <template #extra>{{ t('admin.memberships.slotReturnHelp') }}</template>
      </a-form-item>
      <a-button status="danger" :loading="saving" @click="confirm">{{ t(actionKey) }}</a-button>
    </a-form>
  </a-modal>
</template>
