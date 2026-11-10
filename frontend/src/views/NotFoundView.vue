<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { IconHome } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()
const { t } = useI18n()
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('errors.notFoundTitle')" :description="t('errors.notFoundText')" />
    <a-card class="surface-card">
      <a-result status="404" :title="t('errors.notFoundTitle')" :subtitle="t('errors.notFoundText')">
        <template #extra>
          <a-button type="primary" @click="router.push('/')">
            <template #icon><IconHome /></template>
            {{ t('common.home') }}
          </a-button>
        </template>
      </a-result>
    </a-card>
  </div>
</template>
