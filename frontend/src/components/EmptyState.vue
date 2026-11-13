<script setup lang="ts">
import { IconArchive } from '@arco-design/web-vue/es/icon'

withDefaults(
  defineProps<{
    title: string
    description?: string
  }>(),
  {
    description: '',
  },
)
</script>

<template>
  <div class="empty-state">
    <span class="empty-icon"><IconArchive /></span>
    <a-empty :description="title">
      <template #image>
        <span />
      </template>
      <p v-if="description">{{ description }}</p>
    </a-empty>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 200px;
  height: 100%;
  padding: 36px 12px;
  text-align: center;
}

.empty-state :deep(.arco-empty) {
  min-height: 0;
}

.empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 12px;
  color: rgb(var(--primary-6));
  font-size: 28px;
  background: var(--color-primary-light-1);
  border-radius: 50%;
}

p {
  margin: 6px 0 0;
  color: var(--color-text-3);
}

:deep(.arco-empty-image) {
  display: none;
}
</style>
