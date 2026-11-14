<script setup lang="ts">
defineProps<{ label: string; value: string; unit: string; color: string }>()
</script>

<template>
  <article class="metric-card" :class="'metric-card--' + color" :aria-label="`${label}: ${value} ${unit}`">
    <header>{{ label }}</header>
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
