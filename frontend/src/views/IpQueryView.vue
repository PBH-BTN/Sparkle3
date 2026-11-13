<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import type { TableColumnData } from '@arco-design/web-vue'
import {
  IconCheckCircle,
  IconDelete,
  IconEdit,
  IconExclamationCircle,
  IconHistory,
  IconInfoCircle,
  IconMessage,
  IconRefresh,
  IconSearch,
  IconSend,
  IconUser,
} from '@arco-design/web-vue/es/icon'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import TablePager from '@/components/TablePager.vue'
import { uiApi } from '@/api'
import type {
  IpComment,
  IpQueryLookupResponse,
  ModerationStatus,
} from '@/api/types'
import {
  errorMessage,
  useFormatters,
} from '@/utils/format'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const {
  formatBytes,
  formatDate,
  formatDuration,
  formatNumber,
  formatProgress,
  formatRatio,
} = useFormatters()

const search = ref('')
const lookup = ref<IpQueryLookupResponse | null>(null)
const loading = ref(false)
const loadError = ref('')
const selectedScope = ref('')
const comments = ref<IpComment[]>([])
const commentsLoading = ref(false)
const commentsError = ref('')
const commentPage = ref(1)
const commentPageSize = ref(20)
const commentTotal = ref(0)
const draft = ref('')
const replyTo = ref<IpComment | null>(null)
const editingId = ref<number | null>(null)
const editDraft = ref('')
const submitting = ref(false)
const savingEdit = ref(false)
const historyVisible = ref(false)
const historyComment = ref<IpComment | null>(null)
let lastLookupQuery = ''
let lookupRequestId = 0
let commentsRequestId = 0

const banColumns = computed<TableColumnData[]>(() => [
  { title: t('ipQuery.time'), slotName: 'time', width: 170 },
  { title: t('ipQuery.torrent'), slotName: 'torrent', width: 130 },
  { title: t('ipQuery.port'), dataIndex: 'peer_port', width: 72 },
  { title: t('ipQuery.client'), dataIndex: 'peer_client_name', width: 150 },
  { title: t('ipQuery.progress'), slotName: 'progress', width: 95 },
  { title: t('ipQuery.trafficPair'), slotName: 'traffic', width: 185 },
  { title: t('ipQuery.module'), slotName: 'module', width: 190 },
  { title: t('ipQuery.description'), slotName: 'description', width: 260 },
])

const swarmColumns = computed<TableColumnData[]>(() => [
  { title: t('ipQuery.torrent'), slotName: 'torrent', width: 130 },
  { title: t('ipQuery.port'), dataIndex: 'peer_port', width: 72 },
  { title: t('ipQuery.peerId'), dataIndex: 'peer_id', width: 170 },
  { title: t('ipQuery.client'), dataIndex: 'peer_client_name', width: 150 },
  { title: t('ipQuery.peerProgress'), slotName: 'peerProgress', width: 110 },
  { title: t('ipQuery.trafficPair'), slotName: 'traffic', width: 185 },
  { title: t('ipQuery.flags'), dataIndex: 'flags', width: 100 },
  { title: t('ipQuery.lastSeen'), slotName: 'lastSeen', width: 170 },
])

const scopeOptions = computed(() => lookup.value?.commentScopes ?? [])
const commentAccess = computed(() => lookup.value?.commentAccess)
const activity = computed(() => lookup.value?.activity)
const banPreview = computed(() => activity.value?.bans?.records.slice(0, 20) ?? [])
const swarmPreview = computed(() => activity.value?.swarms?.records.slice(0, 20) ?? [])

function statusLabel(status: ModerationStatus | string | null | undefined) {
  return t(`ipQueryPage.moderation.${status ?? 'PENDING'}`)
}

function statusColor(status: ModerationStatus | string | null | undefined) {
  const colors: Record<string, string> = {
    APPROVED: 'green',
    PENDING: 'orange',
    REJECTED: 'red',
    FAILED: 'red',
  }
  return colors[status ?? 'PENDING'] ?? 'gray'
}

function flagLabel(flag: string) {
  const key = `ipQueryPage.flags.${flag}`
  const translated = t(key)
  return translated === key ? flag : translated
}

function formatAddressTotal(value: string) {
  try {
    return new Intl.NumberFormat(locale.value).format(BigInt(value))
  } catch {
    return value
  }
}

function collapse(value: string | null | undefined, limit = 72) {
  if (!value) return 'N/A'
  return value.length > limit ? `${value.slice(0, limit)}…` : value
}

function shortModuleName(name: string | null | undefined) {
  if (!name) return 'N/A'
  const prefix = 'com.ghostchu.peerbanhelper.module.impl.rule.'
  return name.startsWith(prefix) ? name.slice(prefix.length) : name
}

function resetCommentForm() {
  draft.value = ''
  replyTo.value = null
}

function startReply(comment: IpComment) {
  replyTo.value = comment
  draft.value = ''
  requestAnimationFrame(() => {
    document.getElementById('ip-comment-editor')?.scrollIntoView({
      behavior: 'smooth',
      block: 'center',
    })
  })
}

function startEdit(comment: IpComment) {
  editingId.value = comment.id
  editDraft.value = comment.content
}

function openHistory(comment: IpComment) {
  historyComment.value = comment
  historyVisible.value = true
}

async function copyText(value: string | null | undefined) {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    Message.success(t('common.copied'))
  } catch {
    Message.error(t('ipQueryPage.copyFailed'))
  }
}

async function submitSearch() {
  const value = search.value.trim()
  if (!value) {
    Message.warning(t('ipQueryPage.enterQuery'))
    return
  }
  if (route.query.q === value) {
    await loadLookup(value)
    return
  }
  await router.push({
    name: 'ip-query',
    query: { q: value },
  })
}

async function loadLookup(query: string) {
  if (!query || (loading.value && lastLookupQuery === query)) return
  const requestId = ++lookupRequestId
  lastLookupQuery = query
  loading.value = true
  loadError.value = ''
  comments.value = []
  commentsError.value = ''
  try {
    const result = await uiApi.ipQueryLookup(query)
    if (requestId !== lookupRequestId) return
    lookup.value = result
    search.value = query
    const requestedScope =
      typeof route.query.scope === 'string' ? route.query.scope : ''
    selectedScope.value =
      result.commentScopes.find((option) => option.scope === requestedScope)?.scope
      ?? result.defaultCommentScope
    commentPage.value = 1
    await loadComments()
  } catch (error) {
    if (requestId !== lookupRequestId) return
    lookup.value = null
    loadError.value = errorMessage(error, t('ipQueryPage.loadFailed'))
  } finally {
    if (requestId === lookupRequestId) {
      loading.value = false
    }
  }
}

async function loadComments() {
  const requestId = ++commentsRequestId
  if (!selectedScope.value || !lookup.value?.commentAccess.visible) {
    comments.value = []
    commentTotal.value = 0
    commentsLoading.value = false
    return
  }
  comments.value = []
  commentsLoading.value = true
  commentsError.value = ''
  try {
    const result = await uiApi.ipQueryComments(
      selectedScope.value,
      commentPage.value,
      commentPageSize.value,
    )
    if (requestId !== commentsRequestId) return
    comments.value = result.records
    commentTotal.value = result.total
    commentPage.value = result.current
    commentPageSize.value = result.size
    if (lookup.value) {
      lookup.value.commentAccess = result.access
    }
  } catch (error) {
    if (requestId !== commentsRequestId) return
    commentsError.value = errorMessage(error, t('ipQueryPage.comments.loadFailed'))
  } finally {
    if (requestId === commentsRequestId) {
      commentsLoading.value = false
    }
  }
}

async function selectScope(scope: string) {
  if (selectedScope.value === scope) return
  selectedScope.value = scope
  commentPage.value = 1
  resetCommentForm()
  editingId.value = null
  editDraft.value = ''
  await router.replace({
    query: {
      ...route.query,
      scope,
    },
  })
  await loadComments()
}

async function submitComment() {
  const content = draft.value.trim()
  if (!content) {
    Message.warning(t('ipQueryPage.comments.required'))
    return
  }
  submitting.value = true
  try {
    const created = await uiApi.createIpQueryComment(
      replyTo.value?.network ?? selectedScope.value,
      content,
      replyTo.value?.id ?? null,
    )
    resetCommentForm()
    Message.success(
      created.moderationStatus === 'APPROVED'
        ? t('ipQueryPage.comments.created')
        : t('ipQueryPage.comments.pending'),
    )
    commentPage.value = 1
    await loadComments()
  } catch (error) {
    Message.error(errorMessage(error, t('ipQueryPage.comments.createFailed')))
  } finally {
    submitting.value = false
  }
}

async function saveEdit(comment: IpComment) {
  const content = editDraft.value.trim()
  if (!content) {
    Message.warning(t('ipQueryPage.comments.required'))
    return
  }
  savingEdit.value = true
  try {
    const updated = await uiApi.updateIpQueryComment(
      comment.id,
      content,
      comment.revision,
    )
    editingId.value = null
    editDraft.value = ''
    Message.success(
      updated.moderationStatus === 'APPROVED'
        ? t('ipQueryPage.comments.updated')
        : t('ipQueryPage.comments.updatePending'),
    )
    await loadComments()
  } catch (error) {
    Message.error(errorMessage(error, t('ipQueryPage.comments.updateFailed')))
  } finally {
    savingEdit.value = false
  }
}

async function removeComment(comment: IpComment) {
  try {
    await uiApi.deleteIpQueryComment(comment.id)
    Message.success(t('ipQueryPage.comments.deleted'))
    await loadComments()
  } catch (error) {
    Message.error(errorMessage(error, t('ipQueryPage.comments.deleteFailed')))
  }
}

watch(
  () => route.query.q,
  (value) => {
    if (typeof value === 'string' && value.trim()) {
      void loadLookup(value.trim())
    }
  },
)

onMounted(() => {
  const query = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  if (query) {
    void loadLookup(query)
  }
})
</script>

<template>
  <div class="ip-query-page">
    <PageHeader
      :title="t('ipQueryPage.title')"
      :description="t('ipQueryPage.description')"
    >
      <template #actions>
        <a-button
          v-if="lookup"
          :loading="loading"
          @click="loadLookup(lastLookupQuery)"
        >
          <template #icon><IconRefresh /></template>
          {{ t('common.retry') }}
        </a-button>
      </template>
    </PageHeader>

    <a-card class="surface-card search-card">
      <div class="query-search">
        <a-input
          v-model="search"
          size="large"
          allow-clear
          :placeholder="t('ipQueryPage.placeholder')"
          @press-enter="submitSearch"
        >
          <template #prefix><IconSearch /></template>
        </a-input>
        <a-button
          type="primary"
          size="large"
          :loading="loading"
          @click="submitSearch"
        >
          {{ t('ipQueryPage.search') }}
        </a-button>
      </div>
      <div class="query-hints">
        <span>{{ t('ipQueryPage.hint.ipv4') }}</span>
        <span>{{ t('ipQueryPage.hint.ipv6') }}</span>
        <span>{{ t('ipQueryPage.hint.comments') }}</span>
      </div>
    </a-card>

    <a-alert v-if="loadError" type="error" show-icon :title="loadError" />

    <a-spin :loading="loading" class="query-spin">
      <template v-if="lookup">
        <a-card class="surface-card identity-card">
          <div class="identity-main">
            <span
              class="family-mark"
              :class="`family-mark--v${lookup.ipVersion}`"
            >
              v{{ lookup.ipVersion }}
            </span>
            <div class="identity-copy">
              <span class="eyebrow">{{ t('ipQueryPage.canonicalScope') }}</span>
              <h1>{{ lookup.canonicalCidr }}</h1>
              <div class="identity-flags">
                <a-tag
                  :color="lookup.globallyRoutable ? 'green' : 'orange'"
                >
                  {{
                    lookup.globallyRoutable
                      ? t('ipQueryPage.publicAddress')
                      : t('ipQueryPage.specialAddress')
                  }}
                </a-tag>
                <a-tag
                  v-for="flag in lookup.flags"
                  :key="flag"
                  color="arcoblue"
                >
                  {{ flagLabel(flag) }}
                </a-tag>
              </div>
            </div>
          </div>
          <div class="identity-side">
            <span>{{ t('ipQueryPage.addressCount') }}</span>
            <strong>{{ formatAddressTotal(lookup.totalAddresses) }}</strong>
            <small>{{ t('ipQueryPage.generatedAt', { time: formatDate(lookup.generatedAt) }) }}</small>
          </div>
        </a-card>

        <div class="metric-grid">
          <div
            class="metric-card metric-card--danger"
          >
            <span>{{ t('ipQueryPage.metrics.bans') }}</span>
            <strong>{{ formatNumber(activity?.bans?.total ?? 0) }}</strong>
            <small>
              {{
                t('ipQueryPage.recent', {
                  duration: formatDuration(activity?.bans?.duration ?? 0),
                })
              }}
            </small>
          </div>
          <div class="metric-card metric-card--blue">
            <span>{{ t('ipQueryPage.metrics.swarms') }}</span>
            <strong>{{ formatNumber(activity?.swarms?.total ?? 0) }}</strong>
            <small>
              {{
                t('ipQueryPage.recent', {
                  duration: formatDuration(activity?.swarms?.duration ?? 0),
                })
              }}
            </small>
          </div>
          <div class="metric-card metric-card--green">
            <span>{{ t('ipQueryPage.metrics.upload') }}</span>
            <strong>{{ formatBytes(activity?.traffic?.to_peer_traffic ?? 0) }}</strong>
            <small>{{ t('ipQueryPage.metrics.toPeer') }}</small>
          </div>
          <div class="metric-card metric-card--cyan">
            <span>{{ t('ipQueryPage.metrics.download') }}</span>
            <strong>{{ formatBytes(activity?.traffic?.from_peer_traffic ?? 0) }}</strong>
            <small>{{ t('ipQueryPage.metrics.fromPeer') }}</small>
          </div>
          <div class="metric-card metric-card--purple">
            <span>{{ t('ipQueryPage.metrics.ratio') }}</span>
            <strong>{{ formatRatio(activity?.traffic?.share_ratio ?? 0) }}</strong>
            <small>{{ t('ipQueryPage.metrics.shareRatio') }}</small>
          </div>
          <div class="metric-card metric-card--orange">
            <span>{{ t('ipQueryPage.metrics.torrents') }}</span>
            <strong>{{ formatNumber(activity?.torrents?.count ?? 0) }}</strong>
            <small>{{ t('ipQueryPage.metrics.uniqueTorrents') }}</small>
          </div>
        </div>

        <div class="two-column">
          <a-card class="surface-card geo-card">
            <template #title>
              <span class="card-heading">
                <IconInfoCircle />
                {{ t('ipQueryPage.geo.title') }}
              </span>
            </template>
            <div class="geo-grid">
              <div>
                <span class="geo-label">{{ t('ipQueryPage.geo.start') }}</span>
                <strong>{{ lookup.geoStart?.city || lookup.geoStart?.country || 'N/A' }}</strong>
                <small>
                  {{ lookup.geoStart?.asn }}
                  {{ lookup.geoStart?.asnOrganization || '' }}
                </small>
                <code v-if="lookup.geoStart?.asnNetwork">
                  {{ lookup.geoStart.asnNetwork }}
                </code>
              </div>
              <div>
                <span class="geo-label">{{ t('ipQueryPage.geo.end') }}</span>
                <strong>{{ lookup.geoEnd?.city || lookup.geoEnd?.country || 'N/A' }}</strong>
                <small>
                  {{ lookup.geoEnd?.asn }}
                  {{ lookup.geoEnd?.asnOrganization || '' }}
                </small>
                <code v-if="lookup.geoEnd?.asnNetwork">
                  {{ lookup.geoEnd.asnNetwork }}
                </code>
              </div>
            </div>
            <div class="geo-details">
              <div>
                <span>{{ t('ipQueryPage.geo.region') }}</span>
                <strong>
                  {{
                    [
                      lookup.geoStart?.country,
                      lookup.geoStart?.province,
                      lookup.geoStart?.district,
                    ]
                      .filter(Boolean)
                      .join(' / ') || 'N/A'
                  }}
                </strong>
              </div>
              <div>
                <span>{{ t('ipQueryPage.geo.isp') }}</span>
                <strong>{{ lookup.geoStart?.isp || 'N/A' }}</strong>
              </div>
              <div>
                <span>{{ t('ipQueryPage.geo.networkType') }}</span>
                <strong>{{ lookup.geoStart?.networkType || 'N/A' }}</strong>
              </div>
            </div>
          </a-card>

          <a-card class="surface-card technical-card">
            <template #title>
              <span class="card-heading">
                <IconCheckCircle />
                {{ t('ipQueryPage.technical.title') }}
              </span>
            </template>
            <a-collapse :bordered="false">
              <a-collapse-item
                :header="t('ipQueryPage.technical.summary')"
                key="summary"
              >
                <a-descriptions :column="1" size="small" bordered>
                  <a-descriptions-item :label="t('ipQueryPage.technical.address')">
                    <button type="button" class="copy-value" @click="copyText(lookup.normalizedAddress)">
                      {{ lookup.normalizedAddress }}
                    </button>
                  </a-descriptions-item>
                  <a-descriptions-item :label="t('ipQueryPage.technical.range')">
                    {{ lookup.rangeStart }} – {{ lookup.rangeEnd }}
                  </a-descriptions-item>
                  <a-descriptions-item :label="t('ipQueryPage.technical.reverseDns')">
                    {{ lookup.reverseDnsName }}
                  </a-descriptions-item>
                  <a-descriptions-item
                    v-if="lookup.decimalAddress"
                    :label="t('ipQueryPage.technical.decimal')"
                  >
                    {{ lookup.decimalAddress }}
                  </a-descriptions-item>
                </a-descriptions>
              </a-collapse-item>
              <a-collapse-item
                :header="t('ipQueryPage.technical.extended')"
                key="extended"
              >
                <div class="technical-values">
                  <div>
                    <span>Expanded</span>
                    <code>{{ lookup.expandedAddress }}</code>
                  </div>
                  <div>
                    <span>Binary</span>
                    <code>{{ lookup.binaryAddress }}</code>
                  </div>
                </div>
              </a-collapse-item>
            </a-collapse>
          </a-card>
        </div>

        <a-card class="surface-card activity-card">
          <template #title>
            <span class="card-heading">
              <IconExclamationCircle />
              {{ t('ipQueryPage.activity.title') }}
            </span>
          </template>
          <a-collapse :bordered="false">
            <a-collapse-item
              v-if="banPreview.length"
              :header="t('ipQueryPage.activity.banRecords', { count: activity?.bans?.total ?? 0 })"
              key="bans"
            >
              <a-table
                :columns="banColumns"
                :data="banPreview"
                :pagination="false"
                :scroll="{ x: 1320 }"
                row-key="id"
                size="small"
                stripe
              >
                <template #time="{ record }">{{ formatDate(record.populate_time) }}</template>
                <template #torrent="{ record }">
                  <span class="truncate" :title="record.torrent">{{ record.torrent }}</span>
                </template>
                <template #progress="{ record }">{{ formatProgress(record.peer_progress) }}</template>
                <template #traffic="{ record }">
                  {{ formatBytes(record.to_peer_traffic) }} / {{ formatBytes(record.from_peer_traffic) }}
                </template>
                <template #module="{ record }">
                  <span :title="record.module_name">{{ shortModuleName(record.module_name) }}</span>
                </template>
                <template #description="{ record }">
                  <span :title="record.description">{{ collapse(record.description) }}</span>
                </template>
              </a-table>
            </a-collapse-item>
            <a-collapse-item
              v-if="swarmPreview.length"
              :header="t('ipQueryPage.activity.swarmRecords', { count: activity?.swarms?.total ?? 0 })"
              key="swarms"
            >
              <a-table
                :columns="swarmColumns"
                :data="swarmPreview"
                :pagination="false"
                :scroll="{ x: 1220 }"
                row-key="id"
                size="small"
                stripe
              >
                <template #torrent="{ record }">
                  <span class="truncate" :title="record.torrent">{{ record.torrent }}</span>
                </template>
                <template #peerProgress="{ record }">{{ formatProgress(record.peer_progress) }}</template>
                <template #traffic="{ record }">
                  {{ formatBytes(record.to_peer_traffic) }} / {{ formatBytes(record.from_peer_traffic) }}
                </template>
                <template #lastSeen="{ record }">{{ formatDate(record.last_time_seen) }}</template>
              </a-table>
            </a-collapse-item>
          </a-collapse>
          <EmptyState
            v-if="!banPreview.length && !swarmPreview.length"
            :title="t('ipQueryPage.activity.empty')"
          />
        </a-card>

        <section id="comments" class="comments-section">
          <a-card class="surface-card comments-card">
            <template #title>
              <div class="comments-title">
                <span class="card-heading">
                  <IconMessage />
                  {{ t('ipQueryPage.comments.title') }}
                </span>
                <span v-if="commentAccess?.visible" class="comment-count">
                  {{ t('ipQueryPage.comments.total', { count: commentTotal }) }}
                </span>
              </div>
            </template>

            <a-alert
              v-if="!commentAccess?.visible"
              type="warning"
              show-icon
              :title="t('ipQueryPage.comments.hidden')"
            />
            <template v-else>
              <div class="scope-switcher">
                <div>
                  <strong>{{ t('ipQueryPage.comments.scope') }}</strong>
                  <span>{{ selectedScope }}</span>
                </div>
                <a-radio-group
                  :model-value="selectedScope"
                  type="button"
                  size="small"
                  @change="(value) => selectScope(String(value))"
                >
                  <a-radio
                    v-for="option in scopeOptions"
                    :key="option.scope"
                    :value="option.scope"
                  >
                    /{{ option.prefix }}
                  </a-radio>
                </a-radio-group>
              </div>
              <div class="scope-hint">
                {{ t('ipQueryPage.comments.scopeHint') }}
              </div>

              <a-alert
                v-if="commentAccess?.moderationEnabled"
                type="info"
                show-icon
              >
                {{ t('ipQueryPage.comments.moderationHint') }}
              </a-alert>
              <a-alert
                v-else
                type="warning"
                show-icon
              >
                {{ t('ipQueryPage.comments.moderationOffline') }}
              </a-alert>

              <div v-if="commentAccess?.publishingEnabled" id="ip-comment-editor" class="comment-editor">
                <div v-if="replyTo" class="reply-context">
                  <span>
                    {{ t('ipQueryPage.comments.replyingTo', { id: replyTo.id }) }}
                  </span>
                  <a-button type="text" size="mini" @click="replyTo = null">
                    {{ t('ipQueryPage.comments.cancelReply') }}
                  </a-button>
                </div>
                <a-textarea
                  v-model="draft"
                  :max-length="commentAccess.maxLength"
                  show-word-limit
                  :auto-size="{ minRows: 4, maxRows: 12 }"
                  :placeholder="t('ipQueryPage.comments.placeholder')"
                />
                <div class="editor-footer">
                  <span>
                    {{
                      t('ipQueryPage.comments.rateHint', {
                        limit: commentAccess.hourlyLimit,
                      })
                    }}
                  </span>
                  <a-button
                    type="primary"
                    :loading="submitting"
                    :disabled="!draft.trim()"
                    @click="submitComment"
                  >
                    <template #icon><IconSend /></template>
                    {{ t('ipQueryPage.comments.publish') }}
                  </a-button>
                </div>
              </div>
              <a-alert
                v-else
                type="warning"
                show-icon
                :title="t('ipQueryPage.comments.publishingClosed')"
              />

              <a-alert
                v-if="commentsError"
                type="error"
                show-icon
                :title="commentsError"
              />

              <a-spin :loading="commentsLoading">
                <div v-if="comments.length" class="comment-list">
                  <article
                    v-for="comment in comments"
                    :key="comment.id"
                    class="comment-item"
                    :class="{ 'comment-item--self': comment.self }"
                  >
                    <div class="comment-line" />
                    <div class="comment-avatar">
                      <a-avatar :size="42" :image-url="comment.authorAvatarUrl || undefined">
                        <IconUser />
                      </a-avatar>
                    </div>
                    <div class="comment-body">
                      <header class="comment-header">
                        <div class="author-line">
                          <router-link
                            v-if="comment.authorId && !comment.authorAnonymous"
                            :to="`/user/profile/${comment.authorId}`"
                            class="author-name"
                          >
                            {{ comment.authorNickname }}
                          </router-link>
                          <strong v-else class="author-name author-name--anonymous">
                            {{
                              comment.authorModerationHidden
                                ? t('ipQueryPage.comments.authorHidden')
                                : t('ipQueryPage.comments.anonymous')
                            }}
                          </strong>
                          <a-tag v-if="comment.self" size="small" color="arcoblue">
                            {{ t('ipQueryPage.comments.you') }}
                          </a-tag>
                          <a-tag v-if="comment.parentId" size="small" color="gray">
                            {{
                              t('ipQueryPage.comments.replyTo', {
                                id: comment.parentId,
                              })
                            }}
                          </a-tag>
                          <a-tag
                            v-if="comment.self"
                            size="small"
                            :color="statusColor(comment.moderationStatus)"
                          >
                            {{ statusLabel(comment.moderationStatus) }}
                          </a-tag>
                          <a-tag v-if="comment.edited" size="small" color="orange">
                            {{ t('ipQueryPage.comments.edited') }}
                          </a-tag>
                        </div>
                        <time>{{ formatDate(comment.createdAt) }}</time>
                      </header>

                      <div class="comment-meta">
                        <button type="button" @click="copyText(comment.network)">
                          <strong>{{ t('ipQueryPage.comments.network') }}</strong>
                          {{ comment.network }}
                        </button>
                        <span v-if="comment.authorIp">
                          <strong>{{ t('ipQueryPage.comments.authorIp') }}</strong>
                          {{ comment.authorIp }}
                        </span>
                        <span
                          v-if="comment.authorUserAgent"
                          class="ua-value"
                          :title="comment.authorUserAgent"
                        >
                          <strong>UA</strong>
                          {{ collapse(comment.authorUserAgent, 56) }}
                        </span>
                      </div>

                      <template v-if="editingId === comment.id">
                        <a-textarea
                          v-model="editDraft"
                          :max-length="commentAccess?.maxLength"
                          show-word-limit
                          :auto-size="{ minRows: 4, maxRows: 12 }"
                        />
                        <div class="inline-actions">
                          <a-button size="small" @click="editingId = null">
                            {{ t('common.cancel') }}
                          </a-button>
                          <a-button
                            type="primary"
                            size="small"
                            :loading="savingEdit"
                            @click="saveEdit(comment)"
                          >
                            {{ t('common.save') }}
                          </a-button>
                        </div>
                      </template>
                      <p v-else class="comment-content">{{ comment.content }}</p>

                      <a-alert
                        v-if="
                          comment.self
                            && ['REJECTED', 'FAILED'].includes(comment.moderationStatus)
                        "
                        type="error"
                        show-icon
                        class="review-alert"
                        :title="
                          t('ipQueryPage.comments.reviewFailed', {
                            reason: comment.moderationReason || 'N/A',
                          })
                        "
                      />

                      <footer class="comment-actions">
                        <a-button
                          v-if="commentAccess?.publishingEnabled
                            && comment.moderationStatus === 'APPROVED'"
                          type="text"
                          size="small"
                          @click="startReply(comment)"
                        >
                          <template #icon><IconMessage /></template>
                          {{ t('ipQueryPage.comments.reply') }}
                        </a-button>
                        <a-button
                          v-if="comment.canEdit && editingId !== comment.id"
                          type="text"
                          size="small"
                          @click="startEdit(comment)"
                        >
                          <template #icon><IconEdit /></template>
                          {{ t('common.edit') }}
                        </a-button>
                        <a-button
                          v-if="comment.history.length > 1"
                          type="text"
                          size="small"
                          @click="openHistory(comment)"
                        >
                          <template #icon><IconHistory /></template>
                          {{
                            t('ipQueryPage.comments.history', {
                              count: comment.history.length,
                            })
                          }}
                        </a-button>
                        <a-popconfirm
                          v-if="comment.canDelete"
                          :content="t('ipQueryPage.comments.deleteConfirm')"
                          @ok="removeComment(comment)"
                        >
                          <a-button type="text" size="small" status="danger">
                            <template #icon><IconDelete /></template>
                            {{ t('common.delete') }}
                          </a-button>
                        </a-popconfirm>
                      </footer>
                    </div>
                  </article>
                </div>
                <p v-else-if="!commentsLoading" class="comments-empty">
                  {{ t('ipQueryPage.comments.empty') }}
                </p>
              </a-spin>
              <TablePager
                v-if="commentTotal > 0"
                :current="commentPage"
                :page-size="commentPageSize"
                :total="commentTotal"
                :records-length="comments.length"
                @change="(value) => { commentPage = value; loadComments() }"
                @page-size-change="(value) => { commentPageSize = value; commentPage = 1; loadComments() }"
              />
            </template>
          </a-card>
        </section>
      </template>

      <a-card v-else-if="!loading" class="surface-card empty-query">
        <span class="empty-icon"><IconSearch /></span>
        <h2>{{ t('ipQueryPage.empty.title') }}</h2>
        <p>{{ t('ipQueryPage.empty.description') }}</p>
        <div class="example-list">
          <button type="button" @click="search = '1.1.1.1'; submitSearch()">1.1.1.1</button>
          <button type="button" @click="search = '2001:db8::/48'; submitSearch()">2001:db8::/48</button>
          <button type="button" @click="search = '192.168.1.0/24'; submitSearch()">192.168.1.0/24</button>
        </div>
      </a-card>
    </a-spin>

    <a-modal
      v-model:visible="historyVisible"
      :title="t('ipQueryPage.comments.historyTitle', { id: historyComment?.id })"
      :footer="false"
      width="760px"
    >
      <div v-if="historyComment" class="history-list">
        <div
          v-for="revision in [...historyComment.history].reverse()"
          :key="revision.revision"
          class="history-item"
        >
          <div class="history-heading">
            <strong>#{{ revision.revision }}</strong>
            <a-tag
              size="small"
              :color="statusColor(revision.moderationStatus)"
            >
              {{ statusLabel(revision.moderationStatus) }}
            </a-tag>
            <span>{{ formatDate(revision.createdAt) }}</span>
          </div>
          <p>{{ revision.content || t('ipQueryPage.comments.historyHidden') }}</p>
          <div
            v-if="revision.editorIp || revision.editorUserAgent"
            class="history-meta"
          >
            <span v-if="revision.editorIp">IP：{{ revision.editorIp }}</span>
            <span v-if="revision.editorUserAgent">
              UA：{{ revision.editorUserAgent }}
            </span>
          </div>
          <a-alert
            v-if="revision.moderationReason"
            type="warning"
            :title="revision.moderationReason"
          />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.ip-query-page {
  width: min(1280px, 100%);
  margin: 0 auto;
}

.search-card :deep(.arco-card-body) {
  padding: 20px 22px;
}

.query-search {
  display: flex;
  gap: 10px;
}

.query-search :deep(.arco-input-wrapper) {
  font-family: "SFMono-Regular", Consolas, monospace;
}

.query-search :deep(.arco-btn) {
  min-width: 104px;
}

.query-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 26px;
  margin-top: 12px;
  color: var(--color-text-3);
  font-size: 11px;
}

.query-spin {
  display: block;
  width: 100%;
}

.identity-card {
  margin-top: 16px;
  overflow: hidden;
}

.identity-card :deep(.arco-card-body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 24px 28px;
  background:
    radial-gradient(circle at 95% 10%, rgb(var(--primary-1)) 0, transparent 34%),
    linear-gradient(135deg, var(--color-bg-2), var(--color-fill-1));
}

.identity-main {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 18px;
}

.family-mark {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  color: white;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 16px;
  font-weight: 700;
  background: rgb(var(--primary-6));
  border-radius: 14px;
  box-shadow: 0 10px 22px rgb(var(--primary-6) / 24%);
}

.family-mark--v6 {
  background: linear-gradient(135deg, #7c3aed, #2563eb);
}

.identity-copy {
  min-width: 0;
}

.eyebrow,
.geo-label {
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.identity-copy h1 {
  margin: 2px 0 8px;
  overflow-wrap: anywhere;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: clamp(22px, 4vw, 34px);
  letter-spacing: -0.03em;
}

.identity-flags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.identity-side {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  padding-left: 24px;
  border-left: 1px solid var(--color-border-2);
}

.identity-side span,
.identity-side small {
  color: var(--color-text-3);
  font-size: 11px;
}

.identity-side strong {
  max-width: 340px;
  margin: 4px 0;
  overflow-wrap: anywhere;
  font-size: 22px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.metric-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 136px;
  padding: 17px;
  overflow: hidden;
  color: var(--color-text-1);
  text-align: left;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

button.metric-card {
  cursor: pointer;
}

.metric-card::after {
  position: absolute;
  right: -22px;
  bottom: -34px;
  width: 86px;
  height: 86px;
  background: currentColor;
  border-radius: 50%;
  opacity: 0.08;
  content: "";
}

.metric-card > span {
  color: var(--color-text-3);
  font-size: 11px;
}

.metric-card strong {
  margin: 8px 0 4px;
  overflow-wrap: anywhere;
  font-size: 21px;
  letter-spacing: -0.03em;
}

.metric-card small {
  margin-top: auto;
  color: var(--color-text-3);
  font-size: 10px;
}

.metric-card--danger { color: rgb(var(--red-6)); }
.metric-card--blue { color: rgb(var(--blue-6)); }
.metric-card--green { color: rgb(var(--green-6)); }
.metric-card--cyan { color: #0891b2; }
.metric-card--purple { color: #7c3aed; }
.metric-card--orange { color: rgb(var(--orange-6)); }

.two-column {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.card-heading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.geo-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.geo-grid > div {
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: var(--color-fill-1);
  border-radius: 9px;
}

.geo-grid strong {
  margin: 5px 0;
  font-size: 17px;
}

.geo-grid small {
  color: var(--color-text-2);
}

.geo-grid code {
  margin-top: 7px;
  color: var(--color-text-3);
  font-size: 11px;
}

.geo-details {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.geo-details > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.geo-details span {
  color: var(--color-text-3);
  font-size: 10px;
}

.geo-details strong {
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
}

.technical-values {
  display: grid;
  gap: 12px;
}

.technical-values > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.technical-values span {
  color: var(--color-text-3);
  font-size: 10px;
}

.technical-values code {
  overflow-wrap: anywhere;
  font-size: 11px;
}

.copy-value {
  padding: 0;
  color: inherit;
  font: inherit;
  text-align: left;
  background: none;
  border: 0;
  cursor: pointer;
}

.activity-card,
.comments-card {
  margin-top: 16px;
}

.truncate {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}

.comments-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.comment-count {
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 400;
}

.scope-switcher {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 16px;
  background: var(--color-fill-1);
  border-radius: 9px;
}

.scope-switcher > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 3px;
}

.scope-switcher strong {
  font-size: 12px;
}

.scope-switcher span {
  overflow-wrap: anywhere;
  color: var(--color-text-3);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.scope-hint {
  margin: 8px 2px 14px;
  color: var(--color-text-3);
  font-size: 11px;
}

.comments-card :deep(.arco-alert) {
  margin-bottom: 14px;
}

.comment-editor {
  margin: 16px 0 20px;
  padding: 16px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.reply-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  color: var(--color-text-2);
  font-size: 12px;
}

.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.editor-footer > span {
  color: var(--color-text-3);
  font-size: 11px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.comment-item {
  position: relative;
  display: flex;
  gap: 13px;
  padding: 17px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.comment-item--self {
  background: linear-gradient(135deg, var(--color-primary-light-1), var(--color-bg-2));
  border-color: rgb(var(--primary-3));
}

.comment-line {
  position: absolute;
  top: 17px;
  bottom: 17px;
  left: -1px;
  width: 3px;
  background: rgb(var(--primary-6));
  border-radius: 3px;
  opacity: 0;
}

.comment-item--self .comment-line {
  opacity: 1;
}

.comment-avatar {
  flex: 0 0 auto;
}

.comment-body {
  min-width: 0;
  flex: 1;
}

.comment-header,
.author-line,
.comment-meta,
.comment-actions,
.inline-actions,
.history-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.comment-header {
  justify-content: space-between;
}

.comment-header time {
  color: var(--color-text-3);
  font-size: 11px;
}

.author-name {
  color: var(--color-text-1);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.author-name--anonymous {
  color: var(--color-text-3);
}

.comment-meta {
  margin-top: 7px;
  gap: 6px 16px;
  color: var(--color-text-3);
  font-size: 10px;
}

.comment-meta button {
  display: inline-flex;
  gap: 6px;
  padding: 0;
  color: inherit;
  font: inherit;
  background: none;
  border: 0;
  cursor: pointer;
}

.comment-meta strong {
  color: var(--color-text-2);
  font-weight: 500;
}

.ua-value {
  max-width: min(520px, 100%);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.comment-content {
  margin: 12px 0;
  color: var(--color-text-1);
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.review-alert {
  margin: 10px 0;
}

.inline-actions {
  justify-content: flex-end;
  margin: 10px 0;
}

.comment-actions {
  gap: 2px;
  margin: -4px 0 -6px -8px;
}

.comments-empty {
  margin: 0;
  padding: 24px 12px;
  color: var(--color-text-3);
  text-align: center;
}

.empty-query {
  margin-top: 16px;
  padding: 46px 20px;
  text-align: center;
}

.empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  color: rgb(var(--primary-6));
  font-size: 25px;
  background: rgb(var(--primary-1));
  border-radius: 16px;
}

.empty-query h2 {
  margin: 16px 0 6px;
}

.empty-query p {
  margin: 0;
  color: var(--color-text-3);
}

.example-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
}

.example-list button {
  padding: 7px 12px;
  color: var(--color-text-2);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  cursor: pointer;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 68vh;
  overflow-y: auto;
}

.history-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 15px;
  background: var(--color-fill-1);
  border-radius: 9px;
}

.history-heading span {
  color: var(--color-text-3);
  font-size: 11px;
}

.history-item p {
  margin: 0;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.history-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  color: var(--color-text-3);
  font-size: 10px;
  overflow-wrap: anywhere;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .two-column {
    grid-template-columns: 1fr;
  }

  .identity-card :deep(.arco-card-body) {
    align-items: flex-start;
    flex-direction: column;
  }

  .identity-side {
    align-items: flex-start;
    width: 100%;
    padding-top: 16px;
    padding-left: 0;
    border-top: 1px solid var(--color-border-2);
    border-left: 0;
  }
}

@media (max-width: 620px) {
  .query-search,
  .scope-switcher,
  .editor-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .query-search :deep(.arco-btn) {
    width: 100%;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .geo-grid,
  .geo-details {
    grid-template-columns: 1fr;
  }

  .comment-item {
    padding: 14px 12px;
  }

  .comment-avatar {
    display: none;
  }
}
</style>
