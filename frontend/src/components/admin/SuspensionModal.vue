<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'

const props = defineProps<{
  title: string
  subject: string
  submitLabel: string
  saving: boolean
  initialReason?: string | null
  initialUnbanAt?: string | null
}>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ submit: [reason: string, unbanAt: string | null] }>()

const { t } = useI18n()
const presetKeys = ['malicious', 'abnormal', 'duplicate', 'clockDrift'] as const
const preset = ref<string>()
const reason = ref('')
const unbanAt = ref<number>()

watch(visible, (open) => {
  if (!open) return
  preset.value = undefined
  reason.value = props.initialReason ?? ''
  unbanAt.value = props.initialUnbanAt ? Date.parse(props.initialUnbanAt) : undefined
})

function applyPreset(value: unknown) {
  preset.value = typeof value === 'string' ? value : undefined
  if (preset.value) reason.value = t(`admin.suspension.presets.${preset.value}.reason`)
}

function submit() {
  if (props.saving) return false
  const text = reason.value.trim()
  if (!text) {
    Message.warning(t('admin.suspension.reasonRequired'))
    return false
  }
  const time = unbanAt.value
  if (time != null && (!Number.isFinite(time) || time <= Date.now())) {
    Message.warning(t('admin.suspension.futureTime'))
    return false
  }
  emit('submit', text, time == null ? null : new Date(time).toISOString())
  return false
}
</script>

<template>
  <a-modal
    v-model:visible="visible"
    :title="title"
    width="min(520px, calc(100vw - 32px))"
    :mask-closable="false"
    :closable="!saving"
    :esc-to-close="!saving"
    :ok-text="submitLabel"
    :ok-loading="saving"
    :ok-button-props="{ status: 'danger', disabled: !reason.trim() }"
    :cancel-text="t('admin.common.cancel')"
    :cancel-button-props="{ disabled: saving }"
    :on-before-ok="submit"
  >
    <p class="suspension-subject">{{ subject }}</p>
    <a-form :model="{ reason, unbanAt }" layout="vertical">
      <a-form-item :label="t('admin.suspension.preset')">
        <a-select :model-value="preset" :placeholder="t('admin.suspension.presetPlaceholder')" allow-clear @change="applyPreset">
          <a-option v-for="key in presetKeys" :key="key" :value="key">
            {{ t(`admin.suspension.presets.${key}.label`) }}
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('admin.suspension.reason')" required>
        <a-textarea
          v-model="reason"
          :placeholder="t('admin.suspension.reasonPlaceholder')"
          :max-length="500"
          :auto-size="{ minRows: 3, maxRows: 6 }"
          show-word-limit
          @input="preset = undefined"
        />
      </a-form-item>
      <a-form-item :label="t('admin.suspension.unbanAt')" :extra="t('admin.suspension.unbanHint')">
        <a-date-picker
          v-model="unbanAt"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="timestamp"
          show-time
          allow-clear
          style="width: 100%"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped>
.suspension-subject {
  margin: 0 0 16px;
  overflow-wrap: anywhere;
  color: var(--color-text-2);
}
</style>
