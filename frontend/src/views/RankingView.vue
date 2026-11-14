<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconTrophy,
  IconUser,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { uiApi } from '@/api'
import type {
  PointsRankingEntry,
  PointsRankingResponse,
  RankingEntry,
  RankingResponse,
} from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatBytes, formatPointBytes } = useFormatters()
const activeTab = ref<'traffic' | 'points'>('traffic')
const trafficRanking = ref<RankingResponse | null>(null)
const pointsRanking = ref<PointsRankingResponse | null>(null)
const trafficLoading = ref(true)
const pointsLoading = ref(true)

const virtualListProps = {
  height: 620,
  fixedSize: true,
  isStaticItemHeight: true,
  estimatedSize: 62,
  buffer: 300,
}

const trafficColumns = computed<TableColumnData[]>(() => [
  { title: t('ranking.rank'), dataIndex: 'rank', slotName: 'rank', width: 90, align: 'center' },
  { title: t('ranking.user'), slotName: 'user', width: 260 },
  { title: t('ranking.sentSelf'), slotName: 'sentSelf', width: 150 },
  { title: t('ranking.receivedSelf'), slotName: 'receivedSelf', width: 150 },
  { title: t('ranking.sentAck'), slotName: 'sentAck', width: 150 },
  { title: t('ranking.receivedAck'), slotName: 'receivedAck', width: 150 },
])

const pointsColumns = computed<TableColumnData[]>(() => [
  { title: t('ranking.rank'), dataIndex: 'rank', slotName: 'rank', width: 90, align: 'center' },
  { title: t('ranking.user'), slotName: 'user', width: 360 },
  { title: t('ranking.pointsBalance'), slotName: 'points', width: 220 },
])

function rankColor(rank: number) {
  if (rank === 1) return 'gold'
  if (rank === 2) return 'gray'
  if (rank === 3) return 'orange'
  return 'arcoblue'
}

function rankLabel(entry: { rank: number }) {
  return entry.rank > 1000 ? t('ranking.over1000') : String(entry.rank)
}

async function loadTrafficRanking() {
  trafficLoading.value = true
  try {
    trafficRanking.value = await uiApi.ranking()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    trafficLoading.value = false
  }
}

async function loadPointsRanking() {
  pointsLoading.value = true
  try {
    pointsRanking.value = await uiApi.pointsRanking()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    pointsLoading.value = false
  }
}

function pointRawVisible(entry: PointsRankingEntry) {
  try {
    const points = BigInt(entry.scoreBytes)
    return (points < 0n ? -points : points) >= 1024n
  } catch {
    return false
  }
}

onMounted(() => {
  loadTrafficRanking()
  loadPointsRanking()
})
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('ranking.title')" :description="t('ranking.description')" />

    <a-tabs v-model:active-key="activeTab" type="rounded" class="ranking-tabs">
      <a-tab-pane key="traffic" :title="t('ranking.tabs.traffic')">
        <a-spin :loading="trafficLoading" class="ranking-spin">
          <template v-if="trafficRanking">
            <a-card v-if="trafficRanking.self" class="surface-card self-card">
              <template #title>
                <span class="card-title">
                  <IconUser />
                  {{ t('ranking.myRanking') }}
                </span>
              </template>
              <div class="self-layout">
                <div class="self-rank">
                  <span>{{ t('ranking.rank') }}</span>
                  <strong>{{ rankLabel(trafficRanking.self) }}</strong>
                </div>
                <div class="self-stats">
                  <div>
                    <span>{{ t('ranking.sentSelf') }}</span>
                    <strong>{{ formatBytes(trafficRanking.self.sentTrafficSelfReport) }}</strong>
                  </div>
                  <div>
                    <span>{{ t('ranking.receivedSelf') }}</span>
                    <strong>{{ formatBytes(trafficRanking.self.receivedTrafficSelfReport) }}</strong>
                  </div>
                  <div>
                    <span>{{ t('ranking.sentAck') }}</span>
                    <strong>{{ formatBytes(trafficRanking.self.sentTrafficOtherAck) }}</strong>
                  </div>
                  <div>
                    <span>{{ t('ranking.receivedAck') }}</span>
                    <strong>{{ formatBytes(trafficRanking.self.receivedTrafficOtherAck) }}</strong>
                  </div>
                </div>
              </div>
            </a-card>
            <a-alert v-else type="info" show-icon>{{ t('ranking.noSelf') }}</a-alert>

            <a-card class="surface-card table-card">
              <template #title>
                <span class="card-title">
                  <IconTrophy />
                  {{ t('ranking.top500') }}
                </span>
              </template>
              <template #extra>
                <a-tooltip :content="t('ranking.anonymousHint')">
                  <span class="info-dot">i</span>
                </a-tooltip>
              </template>
              <a-table
                :columns="trafficColumns"
                :data="trafficRanking.entries"
                :pagination="false"
                :virtual-list-props="virtualListProps"
                :scroll="{ x: 1000, y: 620 }"
                row-key="rank"
                class="virtual-ranking-table"
              >
                <template #empty>
                  <EmptyState :title="t('ranking.noData')" />
                </template>
                <template #rank="{ record }">
                  <a-tag :color="rankColor(record.rank)" class="rank-tag">
                    {{ record.rank }}
                  </a-tag>
                </template>
                <template #user="{ record }">
                  <div class="ranking-user">
                    <a-avatar :size="36" :image-url="record.avatarUrl">
                      <IconUserGroup v-if="record.anonymous" />
                      <IconUser v-else />
                    </a-avatar>
                    <RouterLink
                      :to="`/user/profile/${record.userId}`"
                      class="user-profile-link"
                      :class="{ 'anonymous-name': record.anonymous }"
                    >
                      {{ record.anonymous ? t('common.anonymous') : record.displayName }}
                    </RouterLink>
                    <a-tag v-if="record.banned" color="red" size="small">
                      {{ t('ranking.banned') }}
                    </a-tag>
                  </div>
                </template>
                <template #sentSelf="{ record }">
                  <span class="traffic-value traffic-value--up">
                    {{ formatBytes(record.sentTrafficSelfReport) }}
                  </span>
                </template>
                <template #receivedSelf="{ record }">
                  <span class="traffic-value traffic-value--down">
                    {{ formatBytes(record.receivedTrafficSelfReport) }}
                  </span>
                </template>
                <template #sentAck="{ record }">
                  <span class="traffic-value traffic-value--up">
                    {{ formatBytes(record.sentTrafficOtherAck) }}
                  </span>
                </template>
                <template #receivedAck="{ record }">
                  <span class="traffic-value traffic-value--down">
                    {{ formatBytes(record.receivedTrafficOtherAck) }}
                  </span>
                </template>
              </a-table>
            </a-card>
          </template>
        </a-spin>
      </a-tab-pane>

      <a-tab-pane key="points" :title="t('ranking.tabs.points')">
        <a-spin :loading="pointsLoading" class="ranking-spin">
          <template v-if="pointsRanking">
            <a-card v-if="pointsRanking.self" class="surface-card self-card points-self-card">
              <template #title>
                <span class="card-title">
                  <IconUser />
                  {{ t('ranking.myPointsRanking') }}
                </span>
              </template>
              <div class="self-layout points-self-layout">
                <div class="self-rank">
                  <span>{{ t('ranking.rank') }}</span>
                  <strong>{{ rankLabel(pointsRanking.self) }}</strong>
                </div>
                <div class="points-self-score">
                  <span>{{ t('ranking.pointsBalance') }}</span>
                  <strong>{{ formatPointBytes(pointsRanking.self.scoreBytes) }}</strong>
                  <small v-if="pointRawVisible(pointsRanking.self)">
                    {{ pointsRanking.self.scoreBytes }} Bytes
                  </small>
                </div>
              </div>
            </a-card>
            <a-alert v-else type="info" show-icon>{{ t('ranking.noSelf') }}</a-alert>

            <a-card class="surface-card table-card">
              <template #title>
                <span class="card-title">
                  <IconTrophy />
                  {{ t('ranking.pointsTop500') }}
                </span>
              </template>
              <template #extra>
                <a-tooltip :content="t('ranking.anonymousHint')">
                  <span class="info-dot">i</span>
                </a-tooltip>
              </template>
              <a-table
                :columns="pointsColumns"
                :data="pointsRanking.entries"
                :pagination="false"
                :virtual-list-props="virtualListProps"
                :scroll="{ x: 760, y: 620 }"
                row-key="rank"
                class="virtual-ranking-table"
              >
                <template #empty>
                  <EmptyState :title="t('ranking.noData')" />
                </template>
                <template #rank="{ record }">
                  <a-tag :color="rankColor(record.rank)" class="rank-tag">
                    {{ record.rank }}
                  </a-tag>
                </template>
                <template #user="{ record }">
                  <div class="ranking-user">
                    <a-avatar :size="36" :image-url="record.avatarUrl">
                      <IconUserGroup v-if="record.anonymous" />
                      <IconUser v-else />
                    </a-avatar>
                    <RouterLink
                      :to="`/user/profile/${record.userId}`"
                      class="user-profile-link"
                      :class="{ 'anonymous-name': record.anonymous }"
                    >
                      {{ record.anonymous ? t('common.anonymous') : record.displayName }}
                    </RouterLink>
                    <a-tag v-if="record.banned" color="red" size="small">
                      {{ t('ranking.banned') }}
                    </a-tag>
                  </div>
                </template>
                <template #points="{ record }">
                  <div class="points-ranking-value">
                    <strong>{{ formatPointBytes(record.scoreBytes) }}</strong>
                    <span v-if="pointRawVisible(record)">{{ record.scoreBytes }} Bytes</span>
                  </div>
                </template>
              </a-table>
            </a-card>
          </template>
        </a-spin>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<style scoped>
.ranking-tabs :deep(.arco-tabs-content) {
  padding-top: 16px;
}

.ranking-spin {
  display: block;
  width: 100%;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.info-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 17px;
  height: 17px;
  margin-left: 8px;
  color: var(--color-text-3);
  font-size: 11px;
  border: 1px solid var(--color-border-3);
  border-radius: var(--app-radius);
  cursor: help;
}

.self-card {
  margin-bottom: 16px;
}

.self-layout {
  display: flex;
  align-items: stretch;
  gap: 24px;
}

.self-rank {
  display: flex;
  flex: 0 0 170px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: var(--color-white);
  background: rgb(var(--primary-6));
  border-radius: var(--app-radius);
}

.self-rank span {
  margin-bottom: 6px;
  font-size: 12px;
  opacity: 0.85;
}

.self-rank strong {
  font-size: 34px;
  line-height: 1;
}

.self-stats {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.self-stats div,
.points-self-score {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 12px 16px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}

.self-stats span,
.points-self-score span {
  color: var(--color-text-3);
  font-size: 12px;
}

.self-stats strong,
.points-self-score strong {
  margin-top: 6px;
  color: var(--color-text-1);
  font-size: 18px;
}

.points-self-layout {
  align-items: stretch;
}

.points-self-score {
  flex: 1;
}

.points-self-score strong {
  color: rgb(var(--primary-6));
  font-size: 32px;
}

.points-self-score small {
  margin-top: 4px;
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
}

.rank-tag {
  min-width: 34px;
  justify-content: center;
}

.ranking-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.anonymous-name {
  color: var(--color-text-3);
  font-style: italic;
}

.user-profile-link {
  color: rgb(var(--primary-6));
  text-decoration: none;
}

.user-profile-link:hover {
  text-decoration: underline;
}

.traffic-value {
  display: inline-flex;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 500;
  border-radius: var(--app-radius);
}

.traffic-value--up {
  color: rgb(var(--blue-6));
  background: var(--color-primary-light-1);
}

.traffic-value--down {
  color: rgb(var(--green-6));
  background: var(--color-success-light-1);
}

.points-ranking-value {
  display: flex;
  flex-direction: column;
}

.points-ranking-value strong {
  color: rgb(var(--primary-6));
}

.points-ranking-value span {
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
}

.virtual-ranking-table :deep(.arco-table-tr) {
  height: 62px;
}

@media (max-width: 900px) {
  .self-layout {
    flex-direction: column;
  }

  .self-rank {
    flex-basis: auto;
  }

  .self-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
