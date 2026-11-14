<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import {
  IconDelete,
  IconEmail,
  IconNotification,
  IconRefresh,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import TablePager from '@/components/TablePager.vue'
import { uiApi } from '@/api'
import type { InboxMessage } from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(false)
const records = ref<InboxMessage[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const unreadOnly = ref(false)
const selected = ref<InboxMessage | null>(null)
const detailVisible = ref(false)
const conversation = ref<InboxMessage[]>([])
const conversationViewport = ref<HTMLDivElement>()
const conversationPage = ref(1)
const conversationPages = ref(0)
const conversationLoading = ref(false)
const reply = ref('')
const sending = ref(false)
let conversationRequest = 0

async function loadConversation(older = false) {
  if (!selected.value) return
  const requestId = ++conversationRequest
  const id = selected.value.id
  const nextPage = older ? conversationPage.value + 1 : 1
  conversationLoading.value = true
  try {
    const result = await uiApi.messageConversation(id, nextPage)
    if (requestId !== conversationRequest) return
    const viewport = conversationViewport.value
    const previousHeight = viewport?.scrollHeight ?? 0
    const previousTop = viewport?.scrollTop ?? 0
    const messages = [...result.records].reverse()
    conversation.value = older ? [...messages, ...conversation.value] : messages
    conversationPage.value = result.current
    conversationPages.value = result.pages
    await nextTick()
    if (requestId !== conversationRequest) return
    if (conversationViewport.value) {
      conversationViewport.value.scrollTop = older
        ? previousTop + conversationViewport.value.scrollHeight - previousHeight
        : conversationViewport.value.scrollHeight
    }
    const unread = result.records.filter((item) => !item.outgoing && !item.read)
    await Promise.all(unread.map((item) => uiApi.markMessageRead(item.id)))
    if (requestId === conversationRequest) await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    if (requestId === conversationRequest) conversationLoading.value = false
  }
}

async function sendReply() {
  const message = selected.value
  if (!message?.peerUserId || !reply.value.trim() || sending.value) return
  sending.value = true
  try {
    await uiApi.sendMessage(message.peerUserId, message.subject, reply.value.trim())
    reply.value = ''
    await loadConversation()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    sending.value = false
  }
}

const totalUnread = ref(0)

async function load() {
  loading.value = true
  try {
    const [result, unread] = await Promise.all([
      uiApi.inbox(page.value, pageSize.value, unreadOnly.value),
      uiApi.unreadMessageCount(),
    ])
    records.value = result.records
    total.value = result.total
    page.value = result.current
    pageSize.value = result.size
    totalUnread.value = unread.count
    window.dispatchEvent(new CustomEvent('sparkle:messages-changed'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function open(message: InboxMessage) {
  selected.value = message
  detailVisible.value = true
  conversation.value = []
  conversationPage.value = 1
  conversationPages.value = 0
  reply.value = ''
  await loadConversation()
}

async function markAllRead() {
  try {
    await uiApi.markAllMessagesRead()
    totalUnread.value = 0
    Message.success(t('inbox.allRead'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

async function remove(message: InboxMessage) {
  try {
    await uiApi.deleteMessage(message.id)
    conversation.value = conversation.value.filter((item) => item.id !== message.id)
    if (selected.value?.id === message.id) {
      selected.value = conversation.value[conversation.value.length - 1] ?? null
      if (!selected.value) detailVisible.value = false
    }
    Message.success(t('inbox.deleted'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

function categoryLabel(category: string) {
  return t(`notifications.categories.${category}.name`)
}

onMounted(load)
</script>

<template>
  <div class="inbox-page">
    <PageHeader :title="t('inbox.title')" :description="t('inbox.description')">
      <template #actions>
        <a-radio-group v-model="unreadOnly" type="button" @change="() => { page = 1; load() }">
          <a-radio :value="false">{{ t('inbox.all') }}</a-radio>
          <a-radio :value="true">{{ t('inbox.unreadOnly') }}</a-radio>
        </a-radio-group>
        <a-button :disabled="totalUnread === 0" @click="markAllRead">
          {{ t('inbox.markAllRead') }}
        </a-button>
        <a-button @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('common.retry') }}
        </a-button>
      </template>
    </PageHeader>

    <section class="inbox-list">
      <a-spin :loading="loading" class="inbox-spin">
        <div v-if="records.length" class="message-list">
          <button
            v-for="message in records"
            :key="message.id"
            type="button"
            :disabled="sending"
            class="message-row"
            :class="{ 'message-row--unread': !message.read }"
            @click="open(message)"
          >
            <span class="message-icon">
              <IconNotification v-if="message.senderKind === 'SYSTEM'" />
              <IconEmail v-else />
            </span>
            <span class="message-sender">{{ message.peerDisplayName }}</span>
            <span class="message-main">
              <span class="message-heading">
                <strong>{{ message.subject }}</strong>
                <a-tag size="small" color="arcoblue">
                  {{ categoryLabel(message.category) }}
                </a-tag>
                <a-tag v-if="message.senderKind === 'SYSTEM'" size="small" color="purple">
                  {{ t('inbox.system') }}
                </a-tag>
              </span>
              <span class="message-preview"><template v-if="message.outgoing">{{ t('inbox.you') }}: </template>{{ message.content }}</span>
            </span>
            <time class="message-time" :datetime="message.createdAt">{{ formatDate(message.createdAt) }}</time>
            <span v-if="!message.read" class="unread-dot" />
          </button>
        </div>
        <a-empty v-else :description="t('inbox.empty')" />
      </a-spin>
      <TablePager
        v-if="total > 0"
        :current="page"
        :page-size="pageSize"
        :total="total"
        :records-length="records.length"
        @change="(value) => { page = value; load() }"
        @page-size-change="(value) => { pageSize = value; page = 1; load() }"
      />
    </section>

    <a-modal
      v-model:visible="detailVisible"
      :title="selected?.peerDisplayName"
      :footer="false"
      :closable="!sending"
      :mask-closable="!sending"
      :esc-to-close="!sending"
      width="min(760px, calc(100vw - 32px))"
    >
      <div v-if="selected" class="message-detail">
        <a-spin :loading="conversationLoading" class="conversation-spin">
          <a-button v-if="conversationPage < conversationPages" :disabled="conversationLoading" @click="loadConversation(true)">
            {{ t('inbox.older') }}
          </a-button>
          <div ref="conversationViewport" class="conversation-messages">
            <div v-for="message in conversation" :key="message.id" class="conversation-message" :class="{ 'conversation-message--outgoing': message.outgoing }">
              <div class="detail-meta">
                <strong>{{ message.outgoing ? t('inbox.you') : message.senderDisplayName }}</strong>
                <a-tag v-if="message.emailForced" size="small" color="orange">{{ t('inbox.forcedEmail') }}</a-tag>
              </div>
              <strong class="conversation-subject">{{ message.subject }}</strong>
              <div class="detail-content">{{ message.content }}</div>
              <div class="bubble-footer">
                <time :datetime="message.createdAt">{{ formatDate(message.createdAt) }}</time>
                <a-popconfirm v-if="!message.outgoing" :content="t('inbox.deleteConfirm')" @ok="remove(message)">
                  <a-button size="mini" status="danger" :aria-label="t('admin.common.delete')">
                    <template #icon><IconDelete /></template>
                  </a-button>
                </a-popconfirm>
              </div>
            </div>
          </div>
        </a-spin>
        <div v-if="selected.peerUserId" class="reply-form">
          <a-textarea v-model="reply" :placeholder="t('inbox.replyPlaceholder')" :max-length="20000" :auto-size="{ minRows: 3, maxRows: 8 }" :disabled="sending" />
        </div>
        <div class="detail-actions">
          <a-button v-if="selected.peerUserId" type="primary" :loading="sending" :disabled="!reply.trim() || conversationLoading" @click="sendReply">{{ t('inbox.reply') }}</a-button>
          <a-button :disabled="sending" @click="detailVisible = false">{{ t('common.close') }}</a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.inbox-page {
  width: 100%;
  min-width: 0;
  margin: 0 auto;
}
.inbox-list {
  width: 100%;
  min-width: 0;
  border-top: 1px solid var(--color-border-2);
}
.inbox-spin, .conversation-spin {
  display: block;
  width: 100%;
  min-height: 80px;
}
.message-list {
  width: 100%;
  min-width: 0;
  background: var(--color-bg-2);
}
.message-row {
  display: grid;
  grid-template-columns: 20px minmax(80px, 140px) minmax(0, 1fr) auto 8px;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 12px 16px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-border-2);
  cursor: pointer;
}
.message-row:hover, .message-row--unread {
  background: var(--color-fill-1);
}
.message-row:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}
.message-icon {
  display: inline-flex;
  color: rgb(var(--primary-6));
  font-size: 18px;
}
.message-main {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}
.message-heading {
  display: flex;
  min-width: 0;
  max-width: 65%;
  align-items: center;
  gap: 8px;
}
.message-heading :deep(.arco-tag) {
  flex-shrink: 0;
}
.message-heading strong {
  overflow: hidden;
  color: var(--color-text-1);
  font-size: 14px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.message-row--unread strong, .message-row--unread .message-sender {
  font-weight: 600;
}
.message-preview {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.message-sender {
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.message-time {
  color: var(--color-text-3);
  font-size: 12px;
  white-space: nowrap;
}
.unread-dot {
  grid-column: 5;
  width: 6px;
  height: 6px;
  background: rgb(var(--primary-6));
  border-radius: 50%;
}
.inbox-list :deep(.table-pagination) {
  padding: 16px 0;
}
.message-detail {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  color: var(--color-text-3);
  font-size: 12px;
}
.detail-content {
  padding: 12px 0;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.conversation-messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 50vh;
  overflow-y: auto;
  overscroll-behavior: contain;
  margin-top: 12px;
  padding: 16px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}
.conversation-message {
  align-self: flex-start;
  flex-shrink: 0;
  min-width: 0;
  max-width: 85%;
  padding: 12px 14px 8px;
  color: var(--color-text-1);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-1);
  border-radius: 14px 14px 14px 4px;
}
.conversation-message--outgoing {
  align-self: flex-end;
  background: var(--color-primary-light-1);
  border-color: var(--color-primary-light-2);
  border-radius: 14px 14px 4px 14px;
}
.conversation-subject {
  display: block;
  margin-top: 6px;
  overflow-wrap: anywhere;
}
.detail-meta strong {
  min-width: 0;
  color: rgb(var(--primary-6));
  overflow-wrap: anywhere;
}
.bubble-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  color: var(--color-text-3);
  font-size: 11px;
}
.bubble-footer time {
  overflow-wrap: anywhere;
}
.reply-form {
  min-width: 0;
}
@media (max-width: 720px) {
  .conversation-messages { padding: 10px; }
  .conversation-message { max-width: 94%; }
}
@media (max-width: 980px) {
  .message-main {
    flex-direction: column;
    align-items: stretch;
    gap: 4px;
  }
  .message-heading {
    max-width: 100%;
  }
}
@media (max-width: 720px) {
  .message-row {
    grid-template-columns: 18px minmax(0, 1fr) auto 8px;
    gap: 8px;
    padding: 12px;
  }
  .message-icon {
    grid-column: 1;
    grid-row: 1;
  }
  .message-sender {
    grid-column: 2;
  }
  .message-time {
    grid-column: 3;
    font-size: 11px;
  }
  .message-main {
    grid-column: 2 / -1;
    grid-row: 2;
  }
  .unread-dot {
    grid-column: 4;
    grid-row: 1;
  }
  .message-heading {
    flex-wrap: wrap;
  }
  .message-heading strong {
    flex: 1 1 100%;
  }
}
</style>
