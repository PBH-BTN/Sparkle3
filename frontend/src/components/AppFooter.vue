<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconFire } from '@arco-design/web-vue/es/icon'
import { footerApi, type FooterResponse } from '@/api/footer'
import { useFormatters } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    showBrand?: boolean
    centered?: boolean
  }>(),
  {
    showBrand: true,
    centered: false,
  },
)

const { t, te, locale } = useI18n()
const { formatRatio } = useFormatters()
const data = ref<FooterResponse | null>(null)
const now = ref(Date.now())
const serverOffset = ref(0)
let clockTimer: number | undefined
let refreshTimer: number | undefined

const disclaimer = computed(() => {
  if (!data.value) return ''
  return locale.value.startsWith('zh')
    ? data.value.disclaimerZh || data.value.disclaimerEn || ''
    : data.value.disclaimerEn || data.value.disclaimerZh || ''
})

const columns = computed(() =>
  (data.value?.columns ?? [])
    .map((column) => ({
      title: locale.value.startsWith('zh')
        ? column.titleZh || column.titleEn
        : column.titleEn || column.titleZh,
      links: column.links
        .map((link) => ({
          label: locale.value.startsWith('zh')
            ? link.labelZh || link.labelEn
            : link.labelEn || link.labelZh,
          href: link.href,
        }))
        .filter((link) => link.label && link.href),
    }))
    .filter((column) => column.title || column.links.length),
)

const worldClockText = computed(() => {
  const worldTime = data.value?.worldTime
  if (!worldTime) return ''
  const instant = new Date(now.value + serverOffset.value)
  const zones = [
    worldTime.serverTimeZone,
    'Asia/Shanghai',
    'Asia/Tokyo',
    'America/New_York',
    'Europe/Berlin',
    Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
  ]

  return zones
    .map((timeZone) => {
      const canonicalZone = new Intl.DateTimeFormat('en', { timeZone }).resolvedOptions().timeZone
      const city = canonicalZone.split('/').at(-1) ?? canonicalZone
      const cityKey = 'siteFooter.worldTime.' + city
      const label = te(cityKey) ? t(cityKey) : city.replaceAll('_', ' ')
      const time = new Intl.DateTimeFormat(locale.value, {
        timeZone,
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23',
      }).format(instant)
      return `${label} ${time}`
    })
    .join(' · ')
})

const performanceItems = computed(() => {
  const performance = data.value?.performance
  if (!performance) return []
  const items: string[] = []
  const push = (label: string, value: string | null) => {
    if (value != null) items.push(`${label}: ${value}`)
  }
  const compactNumber = (value: number | null) =>
    value == null
      ? null
      : new Intl.NumberFormat(locale.value, {
          maximumFractionDigits: 1,
        }).format(value)
  const percent = (value: number | null) =>
    value == null ? null : `${compactNumber(value)}%`
  const rate = (value: number | null) => formatCompactRate(value)
  const uptime = (seconds: number | null) => formatCompactUptime(seconds)
  const loads = [performance.loadAverage1, performance.loadAverage5, performance.loadAverage15]
  push(
    t('siteFooter.performance.loadAverage'),
    loads.every((value) => value == null)
      ? null
      : loads.map((value) => (value == null ? 'N/A' : formatRatio(value))).join(', '),
  )
  push(t('siteFooter.performance.cpu'), percent(performance.cpuPercent))
  push(t('siteFooter.performance.memory'), percent(performance.memoryPercent))
  push(
    t('siteFooter.performance.systemUptime'),
    uptime(performance.systemUptimeSeconds),
  )
  push(
    t('siteFooter.performance.serverUptime'),
    uptime(performance.serverUptimeSeconds),
  )
  push(
    t('siteFooter.performance.diskIo'),
    performance.diskIoReadBytesPerSecond == null
      && performance.diskIoWriteBytesPerSecond == null
      ? null
      : `${rate(performance.diskIoReadBytesPerSecond) ?? 'N/A'}(read), ${
          rate(performance.diskIoWriteBytesPerSecond) ?? 'N/A'
        }(write)`,
  )
  push(
    t('siteFooter.performance.network'),
    performance.networkReceiveBytesPerSecond == null
      && performance.networkTransmitBytesPerSecond == null
      ? null
      : `↑${rate(performance.networkTransmitBytesPerSecond) ?? 'N/A'}, ↓${
          rate(performance.networkReceiveBytesPerSecond) ?? 'N/A'
        }`,
  )
  push(
    t('siteFooter.performance.httpRps'),
    performance.httpRequestsPerSecond == null
      ? null
      : `${compactNumber(performance.httpRequestsPerSecond)}/s`,
  )
  push(
    t('siteFooter.performance.sqlQps'),
    performance.sqlQueriesPerSecond == null
      ? null
      : `${compactNumber(performance.sqlQueriesPerSecond)}/s`,
  )
  push(
    t('siteFooter.performance.redisQps'),
    performance.redisQueriesPerSecond == null
      ? null
      : `${compactNumber(performance.redisQueriesPerSecond)}/s`,
  )
  return items
})

function formatCompactRate(value: number | null) {
  if (value == null || !Number.isFinite(value)) return null
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB']
  let scaled = Math.max(0, value)
  let index = 0
  while (index < units.length - 1 && scaled >= 1024) {
    scaled /= 1024
    index++
  }
  return `${new Intl.NumberFormat(locale.value, {
    maximumFractionDigits: index === 0 ? 0 : 2,
  }).format(scaled)}${units[index]}/s`
}

function formatCompactUptime(seconds: number | null) {
  if (seconds == null || !Number.isFinite(seconds)) return null
  const totalMinutes = Math.max(0, Math.floor(seconds / 60))
  const days = Math.floor(totalMinutes / (24 * 60))
  const hours = Math.floor((totalMinutes % (24 * 60)) / 60)
  const minutes = totalMinutes % 60
  const clock = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
  return days > 0 ? `${days} ${days === 1 ? 'day' : 'days'}, ${clock}` : clock
}

const httpPerformanceItems = computed(() => {
  const http = data.value?.performance?.httpResponses
  if (!http) return []
  return [
    {
      label: t('siteFooter.performance.api'),
      average: http.apiAverageMillis,
      p99: http.apiP99Millis,
    },
    {
      label: t('siteFooter.performance.btnPing'),
      average: http.btnPingAverageMillis,
      p99: http.btnPingP99Millis,
    },
    {
      label: t('siteFooter.performance.other'),
      average: http.otherAverageMillis,
      p99: http.otherP99Millis,
    },
  ]
    .filter((item) => item.average != null || item.p99 != null)
    .map((item) => {
      const parts: string[] = []
      if (item.average != null) {
        parts.push(
          `${t('siteFooter.performance.average')} ${formatDecimal(item.average)} ms`,
        )
      }
      if (item.p99 != null) {
        parts.push(
          `${t('siteFooter.performance.p99')} ${formatDecimal(item.p99)} ms`,
        )
      }
      return `${item.label}: ${parts.join(' / ')}`
    })
})

const visitorText = computed(() => {
  const visitor = data.value?.visitor
  if (!visitor) return ''
  const parts = [
    t('siteFooter.login.from', {
      ip: visitor.ip,
      geo: visitor.geo || t('siteFooter.login.unknown'),
    }),
    t('siteFooter.login.systemBrowser', {
      system: visitor.system || t('siteFooter.login.unknown'),
      browser: visitor.browser || t('siteFooter.login.unknown'),
    }),
  ]
  if (visitor.proxyProvider === 'CLOUDFLARE') {
    parts.push(t('siteFooter.login.viaCloudflare'))
  } else if (visitor.proxyProvider === 'TENCENT_EDGEONE') {
    parts.push(t('siteFooter.login.viaEdgeOne'))
  } else if (visitor.proxyProvider === 'RELAY') {
    parts.push(t('siteFooter.login.viaRelay'))
  } else {
    parts.push(t('siteFooter.login.viaDirect'))
  }
  return parts.join(' · ')
})

const hasContent = computed(
  () =>
    Boolean(disclaimer.value) ||
    Boolean(data.value?.icp) ||
    columns.value.length > 0 ||
    Boolean(worldClockText.value) ||
    performanceItems.value.length > 0 ||
    httpPerformanceItems.value.length > 0 ||
    Boolean(visitorText.value),
)

function formatDecimal(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return 'N/A'
  return new Intl.NumberFormat(locale.value, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value)
}

async function load() {
  try {
    data.value = await footerApi.get()
    if (data.value.worldTime) {
      serverOffset.value = data.value.worldTime.serverEpochMillis - Date.now()
    }
  } catch {
    data.value = null
  }
}

onMounted(() => {
  load()
  clockTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 1_000)
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') load()
  }, 30_000)
})

onBeforeUnmount(() => {
  window.clearInterval(clockTimer)
  window.clearInterval(refreshTimer)
})
</script>

<template>
  <footer
    v-if="props.showBrand || hasContent"
    class="app-footer"
    :class="{ 'app-footer--centered': props.centered }"
  >
    <div v-if="props.showBrand" class="footer-heading">
      <div>
        <IconFire class="footer-icon" />
        <strong>{{ t('app.footer') }}</strong>
      </div>
      <a href="https://github.com/PBH-BTN" target="_blank" rel="noopener noreferrer">
        {{ t('app.footerBy') }}
      </a>
    </div>

    <div v-if="columns.length" class="footer-columns">
      <section
        v-for="(column, index) in columns"
        :key="`${index}-${column.title}`"
        class="footer-column"
      >
        <strong>{{ column.title }}</strong>
        <a
          v-for="link in column.links"
          :key="`${column.title}-${link.href}-${link.label}`"
          :href="link.href"
          :target="link.href.startsWith('/') ? undefined : '_blank'"
          :rel="link.href.startsWith('/') ? undefined : 'noopener noreferrer'"
        >
          {{ link.label }}
        </a>
      </section>
    </div>

    <p v-if="disclaimer" class="footer-text footer-disclaimer">
      {{ disclaimer }}
    </p>

    <p v-if="worldClockText" class="footer-text footer-clock">
      {{ worldClockText }}
    </p>

    <div v-if="performanceItems.length || httpPerformanceItems.length" class="footer-performance">
      <p v-if="performanceItems.length" class="footer-text">
        {{ performanceItems.join('; ') }}
      </p>
      <p v-if="httpPerformanceItems.length" class="footer-text">
        {{ httpPerformanceItems.join(' · ') }}
      </p>
    </div>

    <p v-if="visitorText" class="footer-text">{{ visitorText }}</p>

    <a
      v-if="data?.icp"
      class="footer-icp"
      href="https://beian.miit.gov.cn/"
      target="_blank"
      rel="noopener noreferrer"
    >
      {{ data.icp }}
    </a>
  </footer>
</template>

<style scoped>
.app-footer {
  display: flex;
  flex-direction: column;
  gap: 13px;
  width: min(1440px, calc(100% - 40px));
  margin: 0 auto;
  padding: 26px 0 30px;
  color: var(--color-text-3);
  font-size: 12px;
  border-top: 1px solid var(--color-border-2);
}

.app-footer--centered {
  width: min(760px, 100%);
  margin-top: 28px;
  text-align: center;
}

.footer-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.footer-heading > div {
  color: var(--color-text-2);
}

.app-footer a {
  color: var(--color-text-3);
  text-decoration: none;
}

.app-footer a:hover {
  color: rgb(var(--primary-6));
}

.footer-icon {
  margin-right: 6px;
  color: rgb(var(--primary-6));
}

.footer-columns {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 240px));
  gap: 24px 48px;
  max-width: 1100px;
  padding: 4px 0 6px;
}

.footer-column {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 7px;
  min-width: 0;
}

.footer-column strong {
  margin-bottom: 2px;
  color: var(--color-text-2);
  font-weight: 600;
}

.footer-column a {
  overflow-wrap: anywhere;
}

.app-footer--centered .footer-column {
  align-items: center;
}

.footer-text {
  max-width: 1200px;
  margin: 0;
  line-height: 1.7;
}

.footer-disclaimer {
  white-space: pre-line;
}

.footer-clock,
.footer-performance {
  font-variant-numeric: tabular-nums;
}

.footer-performance {
  display: grid;
  gap: 5px;
}

.footer-icp {
  align-self: flex-start;
}

.app-footer--centered .footer-icp {
  align-self: center;
}

@media (max-width: 720px) {
  .app-footer {
    width: min(100% - 24px, 1440px);
  }

  .footer-heading {
    flex-direction: column;
  }

  .footer-columns {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px;
  }
}

@media (max-width: 460px) {
  .footer-columns {
    grid-template-columns: 1fr;
  }
}
</style>
