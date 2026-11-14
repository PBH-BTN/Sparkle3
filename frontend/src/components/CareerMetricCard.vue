<script setup lang="ts">
import { IconInfoCircle } from '@arco-design/web-vue/es/icon'
import { useI18n } from 'vue-i18n'

defineProps<{ label: string; value: string; unit: string; color: string; description?: string }>()
const { t } = useI18n()
</script>

<template>
  <article class="metric-card" :class="'metric-card--' + color" :aria-label="`${label}: ${value} ${unit}`">
    <header>
      <span>{{ label }}</span>
      <a-popover v-if="description" :trigger="['hover', 'click', 'focus']" position="top">
        <button type="button" class="metric-help" :aria-label="t('careerHelp.button', { label })">
          <IconInfoCircle />
        </button>
        <template #content>
          <p class="metric-description">{{ description }}</p>
        </template>
      </a-popover>
    </header>
    <strong>{{ value }}</strong>
    <footer>{{ unit }}</footer>
  </article>
</template>

<style scoped>
.metric-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 158px;
  padding: 20px;
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
}
.metric-card::after {
  position: absolute;
  right: -28px;
  bottom: -36px;
  width: 108px;
  height: 108px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.07;
  content: '';
  pointer-events: none;
}
header,
footer {
  z-index: 1;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.6;
}
strong {
  z-index: 1;
  margin: 10px 0 24px;
  font-size: clamp(24px, 2.4vw, 30px);
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.03em;
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
}
header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.metric-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 24px;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--color-text-4);
  font-size: 14px;
  cursor: pointer;
}
.metric-help:hover,
.metric-help:focus-visible {
  background: var(--color-fill-2);
  color: var(--color-text-2);
}
.metric-help:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: 2px;
}
.metric-description {
  max-width: min(320px, calc(100vw - 64px));
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
footer { margin-top: auto; }
.metric-card--red { color: rgb(var(--red-6)); }
.metric-card--blue { color: rgb(var(--blue-6)); }
.metric-card--green { color: rgb(var(--green-6)); }
.metric-card--cyan { color: rgb(var(--cyan-6)); }
.metric-card--purple { color: rgb(var(--purple-6)); }
.metric-card--orange { color: rgb(var(--orange-6)); }
@media (max-width: 720px) {
  .metric-card { padding: 16px; min-height: 148px; }
}
</style>
