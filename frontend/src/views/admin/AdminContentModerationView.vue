<script setup lang="ts">
import { reactive, ref, shallowRef, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { adminApi } from '@/api/admin'
import type { ContentModerationSettings } from '@/api/adminTypes'
import PageHeader from '@/components/PageHeader.vue'

const { t } = useI18n()
const settings = shallowRef<ContentModerationSettings>()
const form = reactive({ enabled: false, words: '' })
const busy = ref(false)
const failed = ref(false)

function apply(value: ContentModerationSettings) {
  settings.value = value
  form.enabled = value.enabled
  form.words = value.words
}

async function load() {
  busy.value = true
  failed.value = false
  try {
    apply(await adminApi.contentModeration())
  } catch {
    failed.value = true
  } finally {
    busy.value = false
  }
}

async function save() {
  if (!settings.value || busy.value) return
  busy.value = true
  try {
    apply(await adminApi.saveContentModeration({
      version: settings.value.version,
      enabled: form.enabled,
      words: form.words,
    }))
    Message.success(t('admin.contentModeration.saved'))
  } catch {
    // The request handler reports the failure. Keep the editor's local text.
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('admin.nav.contentModeration')" />
    <a-card class="surface-card">
      <a-result v-if="failed" status="error" :title="t('admin.contentModeration.loadFailed')">
        <template #extra>
          <a-button @click="load">{{ t('admin.contentModeration.reload') }}</a-button>
        </template>
      </a-result>
      <a-spin v-else :loading="busy" style="width: 100%">
        <a-form v-if="settings" :model="form" layout="vertical" :disabled="busy" @submit-success="save">
          <a-form-item :label="t('admin.contentModeration.enabled')">
            <a-switch v-model="form.enabled" />
          </a-form-item>
          <a-form-item :label="t('admin.contentModeration.words')" :extra="t('admin.contentModeration.hint')">
            <a-textarea v-model="form.words" :max-length="1000000" :rows="16" :spellcheck="false" />
          </a-form-item>
          <p>{{ t('admin.contentModeration.versions', { saved: settings.version, active: settings.activeVersion }) }}</p>
          <p>{{ t('admin.contentModeration.hits', { swarm: settings.swarmHits, ban: settings.banHits }) }}</p>
          <a-space wrap>
            <a-button type="primary" html-type="submit" :loading="busy">
              {{ t('admin.contentModeration.save') }}
            </a-button>
            <a-popconfirm :content="t('admin.contentModeration.reloadConfirm')" @ok="load">
              <a-button :disabled="busy">{{ t('admin.contentModeration.reload') }}</a-button>
            </a-popconfirm>
          </a-space>
        </a-form>
      </a-spin>
    </a-card>
  </div>
</template>
