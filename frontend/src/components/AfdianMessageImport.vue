<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { adminApi } from '@/api/admin'
import type { AfdianMessageImportStatus, AfdianPendingLicense } from '@/api/adminTypes'
import { errorMessage } from '@/utils/format'

const { t } = useI18n()
const emit = defineEmits<{ completed: [] }>()
const authToken = ref('')
const privateKey = ref('')
const starting = ref(false)
const status = ref<AfdianMessageImportStatus | null>(null)
const pending = ref<AfdianPendingLicense[]>([])
const orderIds = reactive<Record<string, string>>({})
const matching = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false

async function refresh() {
  if (timer) clearTimeout(timer)
  try {
    const previous = status.value?.state
    status.value = await adminApi.afdianMessageImportStatus()
    if (previous === 'RUNNING' && status.value.state !== 'RUNNING') emit('completed')
    if (status.value.state !== 'RUNNING') pending.value = await adminApi.afdianPendingLicenses()
    if (status.value.state === 'RUNNING' && !disposed) timer = setTimeout(refresh, 2000)
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  }
}

async function resolve(licenseId: string) {
  matching.value = licenseId
  try {
    await adminApi.matchAfdianImportedLicense({ licenseId, orderId: (orderIds[licenseId] ?? '').trim() })
    delete orderIds[licenseId]
    await refresh()
    emit('completed')
    Message.success(t('admin.afdianMessageImport.matched'))
  } catch (error) {
    Message.error(errorMessage(error, t('admin.afdianMessageImport.failed')))
  } finally {
    matching.value = ''
  }
}

async function selectKey(file: File): Promise<false> {
  try {
    if (file.size > 16384) throw new Error(t('admin.afdianMessageImport.invalidKey'))
    privateKey.value = await file.text()
  } catch (error) {
    privateKey.value = ''
    Message.error(errorMessage(error, t('admin.afdianMessageImport.invalidKey')))
  }
  // Read locally; the upload component must never send the private key file.
  return false
}

async function start() {
  starting.value = true
  try {
    status.value = await adminApi.startAfdianMessageImport({
      authToken: authToken.value.trim(),
      verificationPrivateKey: privateKey.value || undefined,
    })
    authToken.value = ''
    privateKey.value = ''
    await refresh()
  } catch (error) {
    Message.error(errorMessage(error, t('admin.afdianMessageImport.failed')))
  } finally {
    starting.value = false
  }
}

onMounted(refresh)
onUnmounted(() => {
  disposed = true
  if (timer) clearTimeout(timer)
  authToken.value = ''
  privateKey.value = ''
})
</script>

<template>
  <a-card class="surface-card" :title="t('admin.afdianMessageImport.title')">
    <div class="page-stack">
      <a-alert>{{ t('admin.afdianMessageImport.help') }}</a-alert>
      <a-input-password v-model="authToken" autocomplete="off" :max-length="512"
        :placeholder="t('admin.afdianMessageImport.token')"
        :disabled="status?.state === 'RUNNING'" />
      <div class="verification-key">
        <span id="verification-key-label">{{ t('admin.afdianMessageImport.key') }}</span>
        <a-upload accept=".pem,.key,.txt" :show-file-list="false"
          :disabled="starting || status?.state === 'RUNNING'" :on-before-upload="selectKey">
          <template #upload-button>
            <a-button :disabled="starting || status?.state === 'RUNNING'" aria-describedby="verification-key-label">
              {{ t('admin.afdianMessageImport.selectKey') }}
            </a-button>
          </template>
        </a-upload>
      </div>
      <span v-if="privateKey">{{ t('admin.afdianMessageImport.keySelected') }}</span>
      <div class="filter-actions">
        <a-button type="primary" :loading="starting || status?.state === 'RUNNING'"
          :disabled="!authToken.trim()" @click="start">{{ t('admin.afdianMessageImport.start') }}</a-button>
        <a-button @click="refresh">{{ t('admin.common.refresh') }}</a-button>
      </div>
      <a-alert v-if="status" :type="status.state === 'FAILED' ? 'error' : 'info'">
        {{ t(`admin.afdianMessageImport.states.${status.state}`) }}
        {{ t('admin.afdianMessageImport.progress', { ...status }) }}
      </a-alert>
      <a-alert v-if="status?.unmatched" type="warning">{{ t('admin.afdianMessageImport.unmatchedHelp') }}</a-alert>
      <a-alert v-if="status?.conflicts" type="warning">{{ t('admin.afdianMessageImport.conflictsHelp') }}</a-alert>
      <a-list v-if="pending.length" :header="t('admin.afdianMessageImport.pendingTitle')">
        <a-list-item v-for="license in pending" :key="license.licenseId">
          <div class="pending-match">
            <span>{{ license.licenseId }} · {{ license.externalUserId }}</span>
            <a-input v-model="orderIds[license.licenseId]" :max-length="128"
              :placeholder="t('admin.afdianMessageImport.order')" />
            <a-popconfirm :content="t('admin.afdianMessageImport.matchConfirm')" @ok="resolve(license.licenseId)">
              <a-button :disabled="!orderIds[license.licenseId]?.trim() || status?.state === 'RUNNING'"
                :loading="matching === license.licenseId">{{ t('admin.afdianMessageImport.match') }}</a-button>
            </a-popconfirm>
          </div>
        </a-list-item>
      </a-list>
    </div>
  </a-card>
</template>

<style scoped>
.verification-key {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.verification-key :deep(.arco-upload) {
  max-width: 100%;
}
.pending-match {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  width: 100%;
  align-items: center;
}
.pending-match span { overflow-wrap: anywhere; }
.pending-match :deep(.arco-input-wrapper) { flex: 1 1 220px; }
</style>
