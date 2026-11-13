<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

h1 {
  margin: 0;
  color: var(--color-text-1);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.35;
}

p {
  max-width: 760px;
  margin: 6px 0 0;
  color: var(--color-text-3);
  line-height: 1.6;
}

.page-header-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
    gap: 12px;
  }

  .page-header-actions {
    width: 100%;
  }
}
</style>
