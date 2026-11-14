<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TableColumnData } from '@arco-design/web-vue'
import {
  IconBug,
  IconCheckCircle,
  IconCloud,
  IconDownload,
  IconFire,
  IconList,
  IconBarChart,
  IconUpload,
} from '@arco-design/web-vue/es/icon'
import EmptyState from '@/components/EmptyState.vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { uiApi } from '@/api'
import type { IpQueryResult, IpQueryRecord, IpSwarmRecord } from '@/api/types'
import {
  errorMessage,
  useFormatters,
} from '@/utils/format'

const { t } = useI18n()
const {
  formatBytes,
  formatDate,
  formatDuration,
  formatNumber,
  formatProgress,
  formatRatio,
} = useFormatters()
const ip = ref('')
const result = ref<IpQueryResult | null>(null)
const loading = ref(false)
const error = ref('')

const banColumns = computed<TableColumnData[]>(() => [
  { title: t('ipQuery.time'), slotName: 'time', width: 180 },
  { title: t('ipQuery.torrent'), slotName: 'torrent', width: 130 },
  { title: t('ipQuery.port'), slotName: 'port', width: 80 },
  { title: t('ipQuery.client'), slotName: 'client', width: 150 },
  { title: t('ipQuery.progress'), slotName: 'progress', width: 100 },
  { title: t('ipQuery.trafficPair'), slotName: 'traffic', width: 200 },
  { title: t('ipQuery.module'), slotName: 'module', width: 180 },
  { title: t('ipQuery.rule'), slotName: 'rule', width: 160 },
  { title: t('ipQuery.description'), slotName: 'description', width: 240 },
])

const swarmColumns = computed<TableColumnData[]>(() => [
  { title: t('ipQuery.torrent'), slotName: 'torrent', width: 130 },
  { title: t('ipQuery.port'), slotName: 'port', width: 80 },
  { title: t('ipQuery.peerId'), slotName: 'peerId', width: 170 },
  { title: t('ipQuery.client'), slotName: 'client', width: 150 },
  { title: t('ipQuery.peerProgress'), slotName: 'peerProgress', width: 110 },
  { title: t('ipQuery.userProgress'), slotName: 'userProgress', width: 110 },
  { title: t('ipQuery.trafficPair'), slotName: 'traffic', width: 190 },
  { title: t('ipQuery.offsetPair'), slotName: 'offset', width: 190 },
  { title: t('swarmTracker.flags'), slotName: 'flags', width: 90 },
  { title: t('ipQuery.firstSeen'), slotName: 'firstSeen', width: 180 },
  { title: t('ipQuery.lastSeen'), slotName: 'lastSeen', width: 180 },
])

function displayLabel(label: string) {
  return label === 'BTN用户' ? t('ipQuery.btnUser') : label
}

function shortModuleName(name: string | null | undefined) {
  if (!name) return 'N/A'
  const prefix = 'com.ghostchu.peerbanhelper.module.impl.rule.'
  return name.startsWith(prefix) ? name.slice(prefix.length) : name
}

function banCollapsed(value: string, limit = 54) {
  return value.length > limit ? `${value.slice(0, limit)}…` : value
}

async function load() {
  const params = new URLSearchParams(window.location.search)
  ip.value = params.get('ip') ?? ''
  if (!ip.value) {
    error.value = t('ipQuery.missingIp')
    return
  }
  loading.value = true
  error.value = ''
  try {
    result.value = await uiApi.ipQueryWidget(window.location.search)
  } catch (requestError) {
    error.value = errorMessage(requestError, t('ipQuery.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="query-page">
    <div class="query-language"><LanguageSwitcher /></div>
    <div class="query-container">
      <a-spin :loading="loading" class="query-spin">
        <a-alert v-if="error" type="error" show-icon :title="t('ipQuery.loadFailed')">
          {{ error }}
        </a-alert>

        <template v-else-if="result">
          <a-card class="surface-card result-header">
            <div class="ip-heading">
              <span
                class="color-indicator"
                :style="{ backgroundColor: result.color }"
                aria-hidden="true"
              />
              <div>
                <span class="ip-label">{{ t('ipQuery.title') }}</span>
                <h1>{{ ip }}</h1>
              </div>
            </div>
            <div v-if="result.labels?.length" class="ip-labels">
              <a-tag v-for="label in result.labels" :key="label" color="arcoblue">
                {{ displayLabel(label) }}
              </a-tag>
            </div>
          </a-card>

          <a-card v-if="result.bans" class="surface-card query-section">
            <template #title>
              <span class="section-title">
                <span class="section-icon section-icon--danger"><IconBug /></span>
                <span>
                  <strong>{{ t('ipQuery.banHistory') }}</strong>
                  <small>
                    {{
                      t('ipQuery.recent', {
                        duration: formatDuration(result.bans.duration),
                      })
                    }}
                  </small>
                </span>
              </span>
            </template>
            <div class="metric-grid metric-grid--one">
              <div class="metric-box">
                <span>{{ t('ipQuery.totalBans') }}</span>
                <strong class="metric-danger">{{ formatNumber(result.bans.total) }}</strong>
              </div>
            </div>
            <a-table
              v-if="result.bans.records.length"
              :columns="banColumns"
              :data="result.bans.records"
              :pagination="false"
              :scroll="{ x: 1500 }"
              row-key="id"
              size="small"
              stripe
            >
              <template #time="{ record }">
                {{ formatDate(record.populate_time) }}
              </template>
              <template #torrent="{ record }">
                <span class="text-truncate" :title="record.torrent">
                  {{ record.torrent }}
                </span>
              </template>
              <template #port="{ record }">{{ record.peer_port }}</template>
              <template #client="{ record }">{{ record.peer_client_name || 'N/A' }}</template>
              <template #progress="{ record }">
                {{ formatProgress(record.peer_progress) }}
              </template>
              <template #traffic="{ record }">
                {{ formatBytes(record.to_peer_traffic) }} /
                {{ formatBytes(record.from_peer_traffic) }}
              </template>
              <template #module="{ record }">
                <span :title="record.module_name">
                  {{ shortModuleName(record.module_name) }}
                </span>
              </template>
              <template #rule="{ record }">{{ record.rule || 'N/A' }}</template>
              <template #description="{ record }">
                <span :title="record.description">
                  {{ banCollapsed(record.description || 'N/A') }}
                </span>
              </template>
            </a-table>
            <EmptyState v-else :title="t('ipQuery.noBans')" />
          </a-card>

          <a-card v-if="result.swarms" class="surface-card query-section">
            <template #title>
              <span class="section-title">
                <span class="section-icon section-icon--info"><IconCloud /></span>
                <span>
                  <strong>{{ t('ipQuery.swarmActivity') }}</strong>
                  <small>
                    {{
                      t('ipQuery.recent', {
                        duration: formatDuration(result.swarms.duration),
                      })
                    }}
                  </small>
                </span>
              </span>
            </template>
            <div class="metric-grid">
              <div class="metric-box">
                <span>{{ t('ipQuery.totalRecords') }}</span>
                <strong>{{ formatNumber(result.swarms.total) }}</strong>
              </div>
              <div class="metric-box">
                <span>{{ t('ipQuery.concurrentDownloads') }}</span>
                <strong>{{ formatNumber(result.swarms.concurrent_download_torrents_count) }}</strong>
              </div>
              <div class="metric-box">
                <span>{{ t('ipQuery.concurrentSeeds') }}</span>
                <strong>{{ formatNumber(result.swarms.concurrent_seeding_torrents_count) }}</strong>
              </div>
            </div>
            <a-table
              v-if="result.swarms.records.length"
              :columns="swarmColumns"
              :data="result.swarms.records"
              :pagination="false"
              :scroll="{ x: 1650 }"
              row-key="id"
              size="small"
              stripe
            >
              <template #torrent="{ record }">
                <span class="text-truncate" :title="record.torrent">
                  {{ record.torrent }}
                </span>
              </template>
              <template #port="{ record }">{{ record.peer_port }}</template>
              <template #peerId="{ record }">
                <code class="mono">{{ record.peer_id || 'N/A' }}</code>
              </template>
              <template #client="{ record }">{{ record.peer_client_name || 'N/A' }}</template>
              <template #peerProgress="{ record }">
                {{ formatProgress(record.peer_progress) }}
              </template>
              <template #userProgress="{ record }">
                {{ formatProgress(record.user_progress) }}
              </template>
              <template #traffic="{ record }">
                {{ formatBytes(record.to_peer_traffic) }} /
                {{ formatBytes(record.from_peer_traffic) }}
              </template>
              <template #offset="{ record }">
                {{ formatBytes(record.to_peer_traffic_offset) }} /
                {{ formatBytes(record.from_peer_traffic_offset) }}
              </template>
              <template #flags="{ record }">
                <code class="mono">{{ record.flags || 'N/A' }}</code>
              </template>
              <template #firstSeen="{ record }">
                {{ formatDate(record.first_time_seen) }}
              </template>
              <template #lastSeen="{ record }">
                {{ formatDate(record.last_time_seen) }}
              </template>
            </a-table>
            <EmptyState v-else :title="t('ipQuery.noSwarms')" />
          </a-card>

          <div class="detail-grid">
            <a-card v-if="result.traffic" class="surface-card">
              <template #title>
                <span class="section-title">
                  <span class="section-icon section-icon--success"><IconCheckCircle /></span>
                  <span>
                    <strong>{{ t('ipQuery.traffic') }}</strong>
                    <small>
                      {{
                        t('ipQuery.recent', {
                          duration: formatDuration(result.traffic.duration),
                        })
                      }}
                    </small>
                  </span>
                </span>
              </template>
              <div class="metric-grid">
                <div class="metric-box">
                  <span><IconUpload /> {{ t('ipQuery.upload') }}</span>
                  <strong>{{ formatBytes(result.traffic.to_peer_traffic) }}</strong>
                </div>
                <div class="metric-box">
                  <span><IconDownload /> {{ t('ipQuery.download') }}</span>
                  <strong>{{ formatBytes(result.traffic.from_peer_traffic) }}</strong>
                </div>
                <div class="metric-box">
                  <span><IconBarChart /> {{ t('ipQuery.shareRatio') }}</span>
                  <strong>{{ formatRatio(result.traffic.share_ratio) }}</strong>
                </div>
              </div>
            </a-card>

            <a-card v-if="result.torrents" class="surface-card">
              <template #title>
                <span class="section-title">
                  <span class="section-icon section-icon--primary"><IconList /></span>
                  <span>
                    <strong>{{ t('ipQuery.torrents') }}</strong>
                    <small>
                      {{
                        t('ipQuery.recent', {
                          duration: formatDuration(result.torrents.duration),
                        })
                      }}
                    </small>
                  </span>
                </span>
              </template>
              <div class="metric-grid metric-grid--one">
                <div class="metric-box">
                  <span>{{ t('ipQuery.uniqueTorrents') }}</span>
                  <strong>{{ formatNumber(result.torrents.count) }}</strong>
                </div>
              </div>
            </a-card>
          </div>
        </template>

        <a-skeleton v-else-if="loading" :animation="true">
          <a-skeleton-line :rows="8" />
        </a-skeleton>
      </a-spin>

      <footer class="query-footer">
        <IconFire />
        {{ t('ipQuery.poweredBy') }}
      </footer>
    </div>
  </main>
</template>

<style scoped>
.query-page {
  min-height: 100vh;
  padding: 56px 20px 28px;
  background: var(--color-fill-2);
}

.query-language {
  position: absolute;
  top: 20px;
  right: 24px;
  z-index: 2;
}

.query-container {
  width: min(1440px, 100%);
  margin: 0 auto;
}

.query-spin {
  display: block;
  width: 100%;
}

.result-header :deep(.arco-card-body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px;
}

.ip-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.color-indicator {
  display: inline-block;
  width: 12px;
  height: 46px;
  border-radius: var(--app-radius);
}

.ip-label {
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.ip-heading h1 {
  margin: 2px 0 0;
  overflow-wrap: anywhere;
  font-size: 28px;
}

.ip-labels {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.query-section,
.detail-grid {
  margin-top: 16px;
}

.section-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.section-title > span:last-child {
  display: flex;
  flex-direction: column;
}

.section-title strong {
  font-size: 15px;
}

.section-title small {
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 400;
}

.section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
  border-radius: var(--app-radius);
}

.section-icon--danger {
  color: rgb(var(--red-6));
  background: var(--color-danger-light-1);
}

.section-icon--info {
  color: rgb(var(--blue-6));
  background: var(--color-primary-light-1);
}

.section-icon--success {
  color: rgb(var(--green-6));
  background: var(--color-success-light-1);
}

.section-icon--primary {
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.metric-grid--one {
  grid-template-columns: minmax(0, 1fr);
}

.metric-box {
  display: flex;
  flex-direction: column;
  padding: 14px 16px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}

.metric-box > span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-3);
  font-size: 12px;
}

.metric-box strong {
  margin-top: 6px;
  color: var(--color-text-1);
  font-size: 24px;
}

.metric-danger {
  color: rgb(var(--red-6)) !important;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: 16px;
}

.detail-grid .arco-card {
  margin-top: 0;
}

.query-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 28px 0 4px;
  color: var(--color-text-3);
  font-size: 12px;
}

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .query-page {
    padding: 52px 12px 20px;
  }

  .query-language {
    top: 12px;
    right: 12px;
  }

  .result-header {
    margin-top: 18px;
  }

  .result-header :deep(.arco-card-body) {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
