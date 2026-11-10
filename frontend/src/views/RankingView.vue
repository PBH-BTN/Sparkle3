<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconTrophy,
  IconUser,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { uiApi } from '@/api'
import type { RankingEntry, RankingResponse } from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatBytes } = useFormatters()
const ranking = ref<RankingResponse | null>(null)
const loading = ref(true)

const columns = computed<TableColumnData[]>(() => [
  { title: t('ranking.rank'), dataIndex: 'rank', slotName: 'rank', width: 90, align: 'center' },
  { title: t('ranking.user'), slotName: 'user', width: 260 },
  { title: t('ranking.sentSelf'), slotName: 'sentSelf', width: 150 },
  { title: t('ranking.receivedSelf'), slotName: 'receivedSelf', width: 150 },
  { title: t('ranking.sentAck'), slotName: 'sentAck', width: 150 },
  { title: t('ranking.receivedAck'), slotName: 'receivedAck', width: 150 },
])

function rankColor(rank: number) {
  if (rank === 1) return 'gold'
  if (rank === 2) return 'gray'
  if (rank === 3) return 'orange'
  return 'arcoblue'
}

function rankLabel(entry: RankingEntry) {
  return entry.rank > 1000 ? t('ranking.over1000') : String(entry.rank)
}

onMounted(async () => {
  try {
    ranking.value = await uiApi.ranking()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('ranking.title')" :description="t('ranking.description')" />

    <a-spin :loading="loading" class="ranking-spin">
      <template v-if="ranking">
        <a-card v-if="ranking.self" class="surface-card self-card">
          <template #title>
            <span class="card-title">
              <IconUser />
              {{ t('ranking.myRanking') }}
            </span>
          </template>
          <div class="self-layout">
            <div class="self-rank">
              <span>{{ t('ranking.rank') }}</span>
              <strong>{{ rankLabel(ranking.self) }}</strong>
            </div>
            <div class="self-stats">
              <div>
                <span>{{ t('ranking.sentSelf') }}</span>
                <strong>{{ formatBytes(ranking.self.sentTrafficSelfReport) }}</strong>
              </div>
              <div>
                <span>{{ t('ranking.receivedSelf') }}</span>
                <strong>{{ formatBytes(ranking.self.receivedTrafficSelfReport) }}</strong>
              </div>
              <div>
                <span>{{ t('ranking.sentAck') }}</span>
                <strong>{{ formatBytes(ranking.self.sentTrafficOtherAck) }}</strong>
              </div>
              <div>
                <span>{{ t('ranking.receivedAck') }}</span>
                <strong>{{ formatBytes(ranking.self.receivedTrafficOtherAck) }}</strong>
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
            <a-tooltip :content="t('ranking.anonymousHint')">
              <span class="info-dot">i</span>
            </a-tooltip>
          </template>
          <a-table
            :columns="columns"
            :data="ranking.entries"
            :pagination="false"
            :loading="loading"
            :scroll="{ x: 1000 }"
            row-key="rank"
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
                <span :class="{ 'anonymous-name': record.anonymous }">
                  {{ record.anonymous ? t('common.anonymous') : record.displayName }}
                </span>
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
  </div>
</template>

<style scoped>
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
  border-radius: 50%;
  cursor: help;
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
  border-radius: 8px;
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

.self-stats div {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 12px 16px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.self-stats span {
  color: var(--color-text-3);
  font-size: 12px;
}

.self-stats strong {
  margin-top: 6px;
  color: var(--color-text-1);
  font-size: 18px;
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

.traffic-value {
  display: inline-flex;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
}

.traffic-value--up {
  color: rgb(var(--blue-6));
  background: rgb(var(--blue-1));
}

.traffic-value--down {
  color: rgb(var(--green-6));
  background: rgb(var(--green-1));
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
