<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  current: number
  pageSize: number
  total: number
  recordsLength: number
}>()

const emit = defineEmits<{
  change: [page: number]
  pageSizeChange: [pageSize: number]
}>()

const { locale, t } = useI18n()

function formatNumber(value: number) {
  return new Intl.NumberFormat(locale.value).format(value)
}
</script>

<template>
  <div v-if="total > 0" class="table-pagination">
    <span class="pager-info">
      {{
        t('common.pageRange', {
          start: formatNumber(Math.min((current - 1) * pageSize + 1, total)),
          end: formatNumber(Math.min(current * pageSize, total)),
          total: formatNumber(total),
        })
      }}
    </span>
    <a-pagination
      :current="current"
      :page-size="pageSize"
      :total="total"
      :page-size-options="[20, 50, 100]"
      show-page-size
      show-jumper
      @change="emit('change', $event)"
      @page-size-change="emit('pageSizeChange', $event)"
    />
  </div>

  <div
    v-else-if="recordsLength > 0 || current > 1"
    class="table-pagination table-pagination--unknown"
  >
    <span class="pager-info">
      {{
        t('common.pageRange', {
          start: (current - 1) * pageSize + 1,
          end: (current - 1) * pageSize + recordsLength,
          total: '?',
        })
      }}
    </span>
    <a-space>
      <a-button :disabled="current <= 1" @click="emit('change', current - 1)">
        ‹
      </a-button>
      <span class="pager-current">{{ current }}</span>
      <a-button
        :disabled="recordsLength < pageSize"
        @click="emit('change', current + 1)"
      >
        ›
      </a-button>
      <a-select
        :model-value="pageSize"
        :options="[20, 50, 100].map((value) => ({ label: String(value), value }))"
        size="small"
        class="page-size-select"
        @change="emit('pageSizeChange', Number($event))"
      />
    </a-space>
  </div>
</template>

<style scoped>
.table-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
}

.pager-info {
  color: var(--color-text-3);
  font-size: 12px;
}

.pager-current {
  min-width: 24px;
  color: var(--color-text-1);
  text-align: center;
  font-weight: 600;
}

.page-size-select {
  width: 76px;
}

@media (max-width: 720px) {
  .table-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
