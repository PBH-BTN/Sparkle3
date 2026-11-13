<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { mailUnsubscribeApi } from '@/api'
import type { MailUnsubscribeOption } from '@/api/types'

const { t } = useI18n()
const params = new URLSearchParams(window.location.hash.slice(1))
const token = params.get('token') ?? ''
const category = params.get('category')
const options = ref<MailUnsubscribeOption[]>([])
const selected = ref<string[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saved = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    options.value = await mailUnsubscribeApi.options(token)
    selected.value = options.value.filter(item => item.emailEnabled && item.category === category).map(item => item.category)
  } catch {
    error.value = t('mailUnsubscribe.invalid')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    await mailUnsubscribeApi.save(token, selected.value)
    saved.value = true
  } catch {
    error.value = t('mailUnsubscribe.failed')
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<template>
  <main class="unsubscribe-page">
    <a-card class="surface-card unsubscribe-card" :title="t('mailUnsubscribe.title')">
      <a-spin v-if="loading" />
      <a-alert v-else-if="saved" type="success">{{ t('mailUnsubscribe.saved') }}</a-alert>
      <template v-else>
        <p>{{ t('mailUnsubscribe.description') }}</p>
        <a-alert v-if="error" type="error">{{ error }}</a-alert>
        <a-checkbox-group v-model="selected" class="categories" :disabled="saving">
          <a-checkbox v-for="item in options" :key="item.category" :value="item.category" :disabled="!item.emailEnabled">
            {{ t(`notifications.categories.${item.category}.name`) }}
          </a-checkbox>
        </a-checkbox-group>
        <a-button v-if="!options.length" @click="load">{{ t('mailUnsubscribe.retry') }}</a-button>
        <template v-else>
          <p v-if="!selected.length">{{ t('mailUnsubscribe.empty') }}</p>
          <a-button type="primary" :loading="saving" :disabled="!selected.length" @click="save">
            {{ t('mailUnsubscribe.confirm') }}
          </a-button>
        </template>
      </template>
    </a-card>
  </main>
</template>

<style scoped>
.unsubscribe-page { min-height: 100vh; padding: 64px 24px; background: var(--color-fill-1); }
.unsubscribe-card { max-width: 680px; margin: 0 auto; }
.categories { display: flex; flex-direction: column; gap: 16px; margin: 24px 0; }
p { color: var(--color-text-2); line-height: 1.7; }
@media (max-width: 980px) { .unsubscribe-page { padding-top: 40px; } }
@media (max-width: 720px) { .unsubscribe-page { padding: 20px 12px; } }
</style>
