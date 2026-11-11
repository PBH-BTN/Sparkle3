<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import { IconRefresh } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import TablePager from '@/components/TablePager.vue'
import { uiApi } from '@/api'
import type {
  PointCategoryPoints,
  PointsLogEntry,
  PointsOverview,
} from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatNumber, formatPointBytes } = useFormatters()
const overview = ref<PointsOverview | null>(null)
const logs = ref<PointsLogEntry[]>([])
const loading = ref(true)
const logsLoading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const balanceDisplay = computed(() =>
  formatPointAmount(overview.value?.balanceRaw ?? '0'),
)

interface PointsDisplayLog extends PointsLogEntry {
  groupCount: number
  groupPointsRaw: string
  groupEndAt: string
}

const displayLogs = computed<PointsDisplayLog[]>(() => {
  const result: PointsDisplayLog[] = []
  for (const record of logs.value) {
    const previous = result[result.length - 1]
    const sameConsecutive =
      previous &&
      previous.eventType === record.eventType &&
      previous.pointsRaw === record.pointsRaw &&
      previous.details?.reason === record.details?.reason
    if (sameConsecutive) {
      previous.groupCount += 1
      previous.groupPointsRaw = (
        BigInt(previous.groupPointsRaw) + BigInt(record.pointsRaw)
      ).toString()
      previous.groupEndAt = record.occurredAt
      continue
    }
    result.push({
      ...record,
      groupCount: 1,
      groupPointsRaw: record.pointsRaw,
      groupEndAt: record.occurredAt,
    })
  }
  return result
})

function pointBytes(value: string | number | null | undefined) {
  if (value == null) return null
  try {
    return BigInt(
      typeof value === 'number' ? Math.trunc(value) : String(value),
    )
  } catch {
    return null
  }
}

function formatPointAmount(value: string | number | null | undefined) {
  const points = pointBytes(value)
  if (points == null) return 'N/A'
  const absolute = points < 0n ? -points : points
  if (absolute < 1024n) {
    return `${formatNumber(Number(points))} Bytes`
  }
  return formatPointBytes(points)
}

function showPointRaw(value: string | number | null | undefined) {
  const points = pointBytes(value)
  if (points == null) return false
  const absolute = points < 0n ? -points : points
  return absolute >= 1024n
}

const fixedProgress = computed(() => {
  if (!overview.value) return 0
  const awarded = Number(overview.value.fixed.cappedPointsTodayRaw)
  const cap = Number(overview.value.fixed.dailyCapRaw)
  if (!Number.isFinite(awarded) || !Number.isFinite(cap) || cap <= 0) return 0
  return Math.min(1, awarded / cap)
})

const columns = computed<TableColumnData[]>(() => [
  {
    title: t('points.logs.time'),
    dataIndex: 'occurredAt',
    slotName: 'time',
    width: 190,
  },
  {
    title: t('points.logs.event'),
    dataIndex: 'eventType',
    slotName: 'event',
    width: 220,
  },
  {
    title: t('points.logs.change'),
    dataIndex: 'pointsRaw',
    slotName: 'points',
    width: 170,
  },
  {
    title: t('points.logs.details'),
    slotName: 'details',
  },
])

function categoryLabel(category: string) {
  const key = `points.categories.${category}`
  const translated = t(key)
  return translated === key ? category : translated
}

function eventLabel(eventType: string) {
  const key = `points.events.${eventType}`
  const translated = t(key)
  return translated === key ? eventType : translated
}

function pointsDeltaClass(pointsRaw: string) {
  try {
    return BigInt(pointsRaw) < 0n ? 'negative' : 'positive'
  } catch {
    return ''
  }
}

function groupedPoints(record: PointsDisplayLog, value: string | number) {
  try {
    return (BigInt(value) * BigInt(record.groupCount)).toString()
  } catch {
    return String(value)
  }
}

function categoryPercent(category: PointCategoryPoints) {
  if (!category.dailyCapRaw) return null
  const points = Number(category.pointsRaw)
  const cap = Number(category.dailyCapRaw)
  return cap > 0 ? Math.min(1, points / cap) : 0
}

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await uiApi.pointsOverview()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  logsLoading.value = true
  try {
    const result = await uiApi.pointsLogs(page.value, pageSize.value)
    logs.value = result.records
    total.value = result.total
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    logsLoading.value = false
  }
}

function changePage(nextPage: number) {
  page.value = nextPage
  loadLogs()
}

function changePageSize(nextSize: number) {
  pageSize.value = nextSize
  page.value = 1
  loadLogs()
}

function refresh() {
  Promise.all([loadOverview(), loadLogs()])
}

onMounted(refresh)
</script>

<template>
  <div class="points-page page-stack">
    <PageHeader :title="t('points.title')" :description="t('points.description')">
      <template #actions>
        <a-button :loading="loading || logsLoading" @click="refresh">
          <template #icon><IconRefresh /></template>
          {{ t('common.retry') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :loading="loading" class="points-spin">
      <template v-if="overview">
        <a-card class="surface-card balance-card">
          <div class="balance-copy">
            <span class="eyebrow">{{ t('points.unit') }}</span>
            <strong>{{ balanceDisplay }}</strong>
            <span v-if="showPointRaw(overview.balanceRaw)" class="raw-value">
              {{ overview.balanceRaw }} Bytes
            </span>
          </div>
          <div class="balance-mark" aria-hidden="true">B</div>
        </a-card>

        <div class="summary-grid">
          <a-card class="surface-card summary-card">
            <div class="card-heading">
              <div>
                <h2>{{ t('points.fixed.title') }}</h2>
              </div>
              <a-tag color="arcoblue">{{ t('points.fixed.badge') }}</a-tag>
            </div>
            <div class="summary-value">
              <strong>{{ formatPointAmount(overview.fixed.pointsTodayRaw) }}</strong>
              <span
                v-if="showPointRaw(overview.fixed.pointsTodayRaw)"
                class="raw-value"
              >
                {{ overview.fixed.pointsTodayRaw }} Bytes
              </span>
            </div>
            <div class="progress-heading">
              <span>{{ t('points.fixed.progress') }}</span>
              <span>
                {{ formatPointAmount(overview.fixed.cappedPointsTodayRaw) }}
                /
                {{ formatPointAmount(overview.fixed.dailyCapRaw) }}
              </span>
            </div>
            <a-progress
              type="line"
              :percent="fixedProgress"
              :show-text="false"
              :stroke-width="8"
            />
            <div class="average-line">
              <span>{{ t('points.last24HoursAverage') }}</span>
              <span class="average-number">
                <strong>{{ formatPointAmount(overview.fixed.averagePerHourRaw) }} / h</strong>
                <small v-if="showPointRaw(overview.fixed.averagePerHourRaw)">
                  {{ overview.fixed.averagePerHourRaw }} Bytes/h
                </small>
              </span>
            </div>
            <div class="category-grid">
              <div
                v-for="category in overview.fixed.categories.filter((item) => item.enabled)"
                :key="category.category"
                class="category-row"
              >
                <div class="category-label">
                  <span>{{ categoryLabel(category.category) }}</span>
                  <span v-if="showPointRaw(category.pointsRaw)" class="raw-inline">
                    {{ category.pointsRaw }} Bytes
                  </span>
                </div>
                <a-progress
                  v-if="categoryPercent(category) !== null"
                  type="line"
                  size="small"
                  :percent="categoryPercent(category) ?? 0"
                  :show-text="false"
                  :stroke-width="4"
                />
                <span v-if="category.dailyCapRaw" class="category-cap">
                  {{ formatPointAmount(category.pointsRaw) }} /
                  {{ formatPointAmount(category.dailyCapRaw) }}
                </span>
                <span v-else class="category-cap">
                  {{ formatPointAmount(category.pointsRaw) }}
                </span>
              </div>
            </div>
          </a-card>

          <div class="side-panels">
            <a-card
              v-if="overview.oneTime.newTorrentEnabled || overview.oneTime.clientDiscoveryEnabled"
              class="surface-card info-card one-time-card"
            >
              <div class="card-heading">
                <div>
                  <h2>{{ t('points.oneTime.title') }}</h2>
                </div>
                <span class="panel-orbit"></span>
              </div>
              <div class="summary-value compact">
                <strong>{{ formatPointAmount(overview.oneTime.pointsTodayRaw) }}</strong>
                <span
                  v-if="showPointRaw(overview.oneTime.pointsTodayRaw)"
                  class="raw-value"
                >
                  {{ overview.oneTime.pointsTodayRaw }} Bytes
                </span>
              </div>
              <div class="metric-pair">
                <div v-if="overview.oneTime.newTorrentEnabled">
                  <span>{{ t('points.oneTime.newTorrents') }}</span>
                  <strong>{{ formatNumber(overview.oneTime.newTorrentsToday) }}</strong>
                </div>
                <div v-if="overview.oneTime.clientDiscoveryEnabled">
                  <span>{{ t('points.oneTime.discoveries') }}</span>
                  <strong>{{ formatNumber(overview.oneTime.clientDiscoveriesToday) }}</strong>
                </div>
              </div>
              <div class="average-line">
                <span>{{ t('points.last24HoursAverage') }}</span>
                <span class="average-number">
                  <strong>{{ formatPointAmount(overview.oneTime.averagePerHourRaw) }} / h</strong>
                  <small v-if="showPointRaw(overview.oneTime.averagePerHourRaw)">
                    {{ overview.oneTime.averagePerHourRaw }} Bytes/h
                  </small>
                </span>
              </div>
              <p class="panel-note">{{ t('points.oneTime.hint') }}</p>
            </a-card>

            <a-card
              v-if="overview.features.dynamicPoints && overview.dynamic"
              class="surface-card info-card dynamic-card"
            >
              <div class="card-heading">
                <div>
                  <h2>{{ t('points.dynamic.title') }}</h2>
                </div>
                <span class="live-dot"></span>
              </div>
              <div class="summary-value compact">
                <strong>{{ formatPointAmount(overview.dynamic?.pointsTodayRaw) }}</strong>
                <span
                  v-if="showPointRaw(overview.dynamic?.pointsTodayRaw)"
                  class="raw-value"
                >
                  {{ overview.dynamic?.pointsTodayRaw ?? '0' }} Bytes
                </span>
              </div>
              <div class="dynamic-breakdown">
                <div v-if="overview.dynamic?.seedUploadEnabled">
                  <span>{{ t('points.categories.DYNAMIC_SEED_UPLOAD') }}</span>
                  <strong>{{ formatPointAmount(overview.dynamic?.seedUploadPointsTodayRaw) }}</strong>
                  <small v-if="showPointRaw(overview.dynamic?.seedUploadPointsTodayRaw)">
                    {{ overview.dynamic?.seedUploadPointsTodayRaw ?? '0' }} Bytes
                  </small>
                </div>
                <div v-if="overview.dynamic?.downloadEnabled">
                  <span>{{ t('points.categories.DYNAMIC_DOWNLOAD') }}</span>
                  <strong>{{ formatPointAmount(overview.dynamic?.downloadPointsTodayRaw) }}</strong>
                  <small v-if="showPointRaw(overview.dynamic?.downloadPointsTodayRaw)">
                    {{ overview.dynamic?.downloadPointsTodayRaw ?? '0' }} Bytes
                  </small>
                </div>
                <div v-if="overview.dynamic?.hotPeerEnabled">
                  <span>{{ t('points.categories.DYNAMIC_HOT_PEER') }}</span>
                  <strong>{{ formatPointAmount(overview.dynamic?.hotPeerPointsTodayRaw) }}</strong>
                  <small v-if="showPointRaw(overview.dynamic?.hotPeerPointsTodayRaw)">
                    {{ overview.dynamic?.hotPeerPointsTodayRaw ?? '0' }} Bytes
                  </small>
                </div>
              </div>
              <div class="average-line">
                <span>{{ t('points.last24HoursAverage') }}</span>
                <span class="average-number">
                  <strong>{{ formatPointAmount(overview.dynamic?.averagePerHourRaw) }} / h</strong>
                  <small v-if="showPointRaw(overview.dynamic?.averagePerHourRaw)">
                    {{ overview.dynamic?.averagePerHourRaw ?? 0 }} Bytes/h
                  </small>
                </span>
              </div>
            </a-card>
          </div>
        </div>

        <a-card class="surface-card logs-card">
          <template #title>{{ t('points.logs.title') }}</template>
          <template #extra>
            <span class="logs-retention">{{ t('points.logs.retention') }}</span>
          </template>
          <a-table
            :columns="columns"
            :data="displayLogs"
            :loading="logsLoading"
            :pagination="false"
            row-key="id"
            stripe
          >
            <template #empty>
              <a-empty :description="t('points.logs.empty')" />
            </template>
            <template #time="{ record }">{{ formatDate(record.occurredAt) }}</template>
            <template #event="{ record }">
              <div class="event-cell">
                <a-tag color="arcoblue">{{ eventLabel(record.eventType) }}</a-tag>
                <a-tag v-if="record.groupCount > 1" color="gray" size="small">
                  {{ t('points.logs.groupCount', { count: record.groupCount }) }}
                </a-tag>
              </div>
            </template>
            <template #points="{ record }">
              <div class="points-delta" :class="pointsDeltaClass(record.groupPointsRaw)">
                <strong>{{ formatPointAmount(record.groupPointsRaw) }}</strong>
                <span v-if="showPointRaw(record.groupPointsRaw)">
                  {{ record.groupPointsRaw }} Bytes
                </span>
              </div>
            </template>
            <template #details="{ record }">
              <div class="breakdown-tags">
                <a-tag
                  v-if="record.eventType === 'ADMIN_ADJUSTMENT'"
                  color="purple"
                  size="small"
                >
                  {{ t('points.categories.ADMIN_ADJUSTMENT') }}
                  · {{ formatPointAmount(groupedPoints(record, record.manualPointsRaw)) }}
                </a-tag>
                <a-tag
                  v-for="entry in record.details?.breakdown ?? []"
                  :key="entry.category"
                  size="small"
                  :color="entry.component === 'FIXED'
                    ? 'arcoblue'
                    : entry.component === 'ONE_TIME'
                      ? 'gold'
                      : 'green'"
                >
                  {{ categoryLabel(entry.category) }}
                  · {{ formatPointAmount(groupedPoints(record, entry.awardedPoints)) }}
                </a-tag>
                <span v-if="!(record.details?.breakdown?.length)" class="muted">
                  {{ record.details?.reason || t('common.noData') }}
                </span>
              </div>
            </template>
          </a-table>
          <TablePager
            :current="page"
            :page-size="pageSize"
            :total="total"
            :records-length="logs.length"
            @change="changePage"
            @page-size-change="changePageSize"
          />
        </a-card>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.points-page {
  width: min(1180px, 100%);
  margin: 0 auto;
}

.points-spin {
  display: block;
  width: 100%;
}

.balance-card {
  overflow: hidden;
  background: #155e75;
  border: 0;
  border-radius: 16px;
}

.balance-card :deep(.arco-card-body) {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 190px;
  padding: 32px 36px;
  color: #fff;
  background:
    radial-gradient(circle at 90% 20%, rgb(255 255 255 / 24%), transparent 28%),
    linear-gradient(135deg, #0e7490, #155e75 52%, #164e63);
  border-radius: 0;
}

.balance-copy {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  max-width: 760px;
}

.eyebrow {
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.balance-card .eyebrow {
  color: rgb(255 255 255 / 68%);
}

.balance-copy > strong {
  margin-top: 8px;
  font-size: clamp(38px, 7vw, 68px);
  line-height: 1.05;
  letter-spacing: -0.04em;
}

.balance-copy p {
  max-width: 640px;
  margin: 18px 0 0;
  color: rgb(255 255 255 / 74%);
  line-height: 1.7;
}

.raw-value {
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}

.balance-card .raw-value {
  margin-top: 8px;
  color: rgb(255 255 255 / 58%);
}

.balance-mark {
  position: absolute;
  right: 32px;
  bottom: -36px;
  color: rgb(255 255 255 / 8%);
  font-size: 190px;
  font-weight: 800;
  line-height: 1;
}

.summary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.8fr);
  gap: 16px;
  margin-top: 16px;
}

.summary-card :deep(.arco-card-body),
.info-card :deep(.arco-card-body) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.side-panels {
  display: grid;
  gap: 16px;
}

.card-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.card-heading h2 {
  margin: 4px 0 0;
  font-size: 19px;
}

.summary-value {
  display: flex;
  flex-direction: column;
  margin: 22px 0;
}

.summary-value strong {
  color: rgb(var(--primary-6));
  font-size: 34px;
  line-height: 1.15;
}

.summary-value.compact strong {
  font-size: 28px;
}

.progress-heading,
.average-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-text-3);
  font-size: 12px;
}

.progress-heading {
  margin-bottom: 8px;
}

.average-line {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-2);
}

.average-line strong {
  color: var(--color-text-1);
}

.average-number {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.average-number small,
.dynamic-breakdown small {
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 9px;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
  margin-top: 22px;
}

.category-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px 10px;
  padding: 10px 12px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.category-label {
  display: flex;
  flex-direction: column;
  min-width: 0;
  font-size: 12px;
}

.raw-inline {
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
}

.category-row :deep(.arco-progress) {
  grid-column: 1 / -1;
  margin: 0;
}

.category-cap {
  color: var(--color-text-3);
  font-size: 11px;
  white-space: nowrap;
}

.metric-pair,
.dynamic-breakdown {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
}

.dynamic-breakdown {
  grid-template-columns: 1fr;
  margin-top: 4px;
}

.metric-pair > div,
.dynamic-breakdown > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.metric-pair span,
.dynamic-breakdown span {
  color: var(--color-text-3);
  font-size: 11px;
}

.metric-pair strong,
.dynamic-breakdown strong {
  font-size: 14px;
}

.panel-note {
  margin: 16px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.panel-orbit,
.live-dot {
  width: 12px;
  height: 12px;
  margin-top: 4px;
  background: rgb(var(--gold-6));
  border-radius: 999px;
  box-shadow: 0 0 0 6px rgb(var(--gold-2));
}

.live-dot {
  background: rgb(var(--green-6));
  box-shadow: 0 0 0 6px rgb(var(--green-2));
}

.logs-card {
  margin-top: 16px;
}

.logs-retention {
  color: var(--color-text-3);
  font-size: 12px;
}

.points-delta {
  display: flex;
  flex-direction: column;
}

.points-delta strong {
  color: rgb(var(--green-6));
}

.points-delta.negative strong {
  color: rgb(var(--red-6));
}

.points-delta span {
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
}

.breakdown-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.event-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

@media (max-width: 980px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .balance-card :deep(.arco-card-body) {
    padding: 26px 22px;
  }

  .balance-mark {
    display: none;
  }

  .category-grid {
    grid-template-columns: 1fr;
  }

  .metric-pair {
    grid-template-columns: 1fr;
  }
}
</style>
