<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
  if (!message.read) {
    try {
      await uiApi.markMessageRead(message.id)
      message.read = true
      message.readAt = new Date().toISOString()
      totalUnread.value = Math.max(0, totalUnread.value - 1)
      window.dispatchEvent(new CustomEvent('sparkle:messages-changed'))
    } catch (error) {
      Message.error(errorMessage(error, t('common.requestFailed')))
    }
  }
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
    detailVisible.value = false
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
        <a-radio-group v-model="unreadOnly" type="button" @change="load">
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

    <a-card class="surface-card inbox-card">
      <a-spin :loading="loading">
        <div v-if="records.length" class="message-list">
          <button
            v-for="message in records"
            :key="message.id"
            type="button"
            class="message-row"
            :class="{ 'message-row--unread': !message.read }"
            @click="open(message)"
          >
            <span class="message-icon">
              <IconNotification v-if="message.senderKind === 'SYSTEM'" />
              <IconEmail v-else />
            </span>
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
              <span class="message-preview">{{ message.content }}</span>
              <span class="message-sender">
                {{ message.senderDisplayName }} · {{ formatDate(message.createdAt) }}
              </span>
            </span>
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
    </a-card>

    <a-modal
      v-model:visible="detailVisible"
      :title="selected?.subject"
      :footer="false"
      width="760px"
    >
      <div v-if="selected" class="message-detail">
        <div class="detail-meta">
          <a-tag color="arcoblue">{{ categoryLabel(selected.category) }}</a-tag>
          <span>{{ selected.senderDisplayName }}</span>
          <span>{{ formatDate(selected.createdAt) }}</span>
          <a-tag v-if="selected.emailForced" color="orange">
            {{ t('inbox.forcedEmail') }}
          </a-tag>
        </div>
        <div class="detail-content">{{ selected.content }}</div>
        <div class="detail-actions">
          <a-popconfirm :content="t('inbox.deleteConfirm')" @ok="remove(selected)">
            <a-button status="danger">
              <template #icon><IconDelete /></template>
              {{ t('admin.common.delete') }}
            </a-button>
          </a-popconfirm>
          <a-button type="primary" @click="detailVisible = false">
            {{ t('common.close') }}
          </a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.inbox-page {
  width: min(1100px, 100%);
  margin: 0 auto;
}

.message-list {
  display: flex;
  flex-direction: column;
}

.message-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  width: 100%;
  padding: 17px 12px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-border-2);
  cursor: pointer;
}

.message-row:last-child {
  border-bottom: 0;
}

.message-row:hover,
.message-row--unread {
  background: var(--color-fill-1);
}

.message-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: rgb(var(--primary-6));
  font-size: 18px;
  background: rgb(var(--primary-1));
  border-radius: 10px;
}

.message-main {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  gap: 5px;
}

.message-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.message-heading strong {
  color: var(--color-text-1);
  font-size: 14px;
}

.message-preview {
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-sender {
  color: var(--color-text-3);
  font-size: 11px;
}

.unread-dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  margin-top: 8px;
  background: rgb(var(--primary-6));
  border-radius: 50%;
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
  min-height: 180px;
  padding: 18px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
