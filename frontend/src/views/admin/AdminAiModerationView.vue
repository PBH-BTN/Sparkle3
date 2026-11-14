<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import type { TableColumnData } from '@arco-design/web-vue'
import {
  IconCheckCircle,
  IconExclamationCircle,
  IconRefresh,
  IconSave,
  IconSend,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  AiModerationAdmin,
  AiModerationJob,
  ModerationTestResult,
} from '@/api/adminTypes'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate } = useFormatters()
const loading = ref(true)
const savingProvider = ref(false)
const savingComments = ref(false)
const testing = ref(false)
const requeueing = ref(false)
const retrying = ref(false)
const data = ref<AiModerationAdmin | null>(null)
const testContent = ref('')
const testResult = ref<ModerationTestResult | null>(null)

const providerForm = ref({
  enabled: false,
  baseUrl: '',
  apiKey: '',
  clearApiKey: false,
  model: '',
  rubric: '',
  timeoutSeconds: 30,
  maxAttempts: 5,
  moderateAvatars: true,
})

const commentForm = ref({
  commentsEnabled: true,
  commentsVisible: true,
  maxLength: 5000,
  hourlyLimit: 30,
})

const jobColumns = computed<TableColumnData[]>(() => [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: t('admin.aiModeration.jobType'), slotName: 'type', width: 120 },
  { title: t('admin.aiModeration.jobTarget'), slotName: 'target', width: 150 },
  { title: t('admin.aiModeration.jobAttempts'), slotName: 'attempts', width: 100 },
  { title: t('admin.aiModeration.jobStatus'), slotName: 'status', width: 120 },
  { title: t('admin.aiModeration.jobResult'), slotName: 'result', width: 320 },
  { title: t('common.createdAt'), slotName: 'createdAt', width: 175 },
])

const metrics = computed(() => data.value?.metrics)

function applyView(view: AiModerationAdmin) {
  data.value = view
  Object.assign(providerForm.value, {
    enabled: view.settings.enabled,
    baseUrl: view.settings.baseUrl,
    apiKey: '',
    clearApiKey: false,
    model: view.settings.model,
    rubric: view.settings.rubric,
    timeoutSeconds: view.settings.timeoutSeconds,
    maxAttempts: view.settings.maxAttempts,
    moderateAvatars: view.settings.moderateAvatars,
  })
  Object.assign(commentForm.value, view.comments)
}

async function load() {
  loading.value = true
  try {
    applyView(await adminApi.aiModeration())
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function saveProvider() {
  if (
    providerForm.value.enabled
    && (
      !providerForm.value.baseUrl.trim()
      || !providerForm.value.model.trim()
      || !providerForm.value.rubric.trim()
    )
  ) {
    Message.warning(t('admin.aiModeration.required'))
    return
  }
  savingProvider.value = true
  try {
    const view = await adminApi.updateAiModeration({
      enabled: providerForm.value.enabled,
      baseUrl: providerForm.value.baseUrl.trim(),
      apiKey: providerForm.value.apiKey.trim() || undefined,
      clearApiKey: providerForm.value.clearApiKey,
      model: providerForm.value.model.trim(),
      rubric: providerForm.value.rubric.trim(),
      timeoutSeconds: providerForm.value.timeoutSeconds,
      maxAttempts: providerForm.value.maxAttempts,
      moderateAvatars: providerForm.value.moderateAvatars,
    })
    applyView(view)
    Message.success(t('admin.aiModeration.providerSaved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingProvider.value = false
  }
}

async function saveComments() {
  savingComments.value = true
  try {
    const view = await adminApi.updateIpQueryComments({
      commentsEnabled: commentForm.value.commentsEnabled,
      commentsVisible: commentForm.value.commentsVisible,
      maxLength: commentForm.value.maxLength,
      hourlyLimit: commentForm.value.hourlyLimit,
    })
    applyView(view)
    Message.success(t('admin.aiModeration.commentsSaved'))
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    savingComments.value = false
  }
}

async function testModel() {
  if (!testContent.value.trim()) {
    Message.warning(t('admin.aiModeration.required'))
    return
  }
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await adminApi.testAiModeration(testContent.value.trim())
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    testing.value = false
  }
}

async function requeueAll() {
  requeueing.value = true
  try {
    const result = await adminApi.requeueAllIpQueryComments()
    Message.success(
      t('admin.aiModeration.requeued', { count: result.jobsCreated }),
    )
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    requeueing.value = false
  }
}

async function retryFailed() {
  retrying.value = true
  try {
    const result = await adminApi.retryAiModerationJobs()
    Message.success(
      t('admin.aiModeration.retried', { count: result.jobsCreated }),
    )
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    retrying.value = false
  }
}

function statusColor(status: string) {
  const colors: Record<string, string> = {
    APPROVED: 'green',
    PENDING: 'orange',
    RUNNING: 'arcoblue',
    REJECTED: 'red',
    FAILED: 'red',
  }
  return colors[status] ?? 'gray'
}

function jobResult(job: AiModerationJob) {
  return job.rejectionReason || job.lastError || '—'
}

onMounted(load)
</script>

<template>
  <div class="page-stack ai-moderation-page">
    <PageHeader
      :title="t('admin.aiModeration.title')"
      :description="t('admin.aiModeration.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.common.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :loading="loading">
      <div class="metric-grid">
        <div class="metric-card metric-card--orange">
          <span>{{ t('admin.aiModeration.metrics.pending') }}</span>
          <strong>{{ metrics?.pending ?? 0 }}</strong>
        </div>
        <div class="metric-card metric-card--blue">
          <span>{{ t('admin.aiModeration.metrics.running') }}</span>
          <strong>{{ metrics?.running ?? 0 }}</strong>
        </div>
        <div class="metric-card metric-card--green">
          <span>{{ t('admin.aiModeration.metrics.approved') }}</span>
          <strong>{{ metrics?.approved ?? 0 }}</strong>
        </div>
        <div class="metric-card metric-card--red">
          <span>{{ t('admin.aiModeration.metrics.rejected') }}</span>
          <strong>{{ metrics?.rejected ?? 0 }}</strong>
        </div>
        <div class="metric-card metric-card--gray">
          <span>{{ t('admin.aiModeration.metrics.failed') }}</span>
          <strong>{{ metrics?.failed ?? 0 }}</strong>
        </div>
      </div>

      <a-row :gutter="16">
        <a-col :xs="24" :xl="15">
          <a-card class="surface-card">
            <template #title>{{ t('admin.aiModeration.providerTitle') }}</template>
            <a-alert type="info" show-icon>
              {{ t('admin.aiModeration.providerHint') }}
            </a-alert>
            <a-form :model="providerForm" layout="vertical">
              <a-form-item :label="t('admin.aiModeration.enabled')">
                <a-switch v-model="providerForm.enabled" />
                <template #extra>
                  {{ t('admin.aiModeration.enabledHint') }}
                </template>
              </a-form-item>

              <a-row :gutter="14">
                <a-col :xs="24" :md="16">
                  <a-form-item :label="t('admin.aiModeration.baseUrl')">
                    <a-input
                      v-model="providerForm.baseUrl"
                      placeholder="https://api.openai.com/v1"
                    />
                    <template #extra>
                      {{ t('admin.aiModeration.baseUrlHint') }}
                    </template>
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="8">
                  <a-form-item :label="t('admin.aiModeration.model')">
                    <a-input v-model="providerForm.model" />
                    <template #extra>
                      {{ t('admin.aiModeration.modelHint') }}
                    </template>
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item :label="t('admin.aiModeration.apiKey')">
                <a-input-password
                  v-model="providerForm.apiKey"
                  :placeholder="
                    data?.settings.apiKeyConfigured
                      ? t('admin.aiModeration.apiKeyKeep')
                      : t('admin.aiModeration.apiKeyPlaceholder')
                  "
                  :disabled="providerForm.clearApiKey"
                />
                <template #extra>
                  <a-space wrap>
                    <a-tag :color="data?.settings.apiKeyConfigured ? 'green' : 'gray'">
                      {{
                        data?.settings.apiKeyConfigured
                          ? t('admin.aiModeration.apiKeyConfigured')
                          : t('admin.aiModeration.apiKeyMissing')
                      }}
                    </a-tag>
                    <a-checkbox
                      v-if="data?.settings.apiKeyConfigured"
                      v-model="providerForm.clearApiKey"
                    >
                      {{ t('admin.aiModeration.clearApiKey') }}
                    </a-checkbox>
                  </a-space>
                </template>
              </a-form-item>

              <a-row :gutter="14">
                <a-col :xs="12" :md="8">
                  <a-form-item :label="t('admin.aiModeration.timeout')">
                    <a-input-number
                      v-model="providerForm.timeoutSeconds"
                      :min="5"
                      :max="120"
                      style="width: 100%"
                    />
                  </a-form-item>
                </a-col>
                <a-col :xs="12" :md="8">
                  <a-form-item :label="t('admin.aiModeration.attempts')">
                    <a-input-number
                      v-model="providerForm.maxAttempts"
                      :min="1"
                      :max="10"
                      style="width: 100%"
                    />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="8">
                  <a-form-item :label="t('admin.aiModeration.moderateAvatars')">
                    <a-switch v-model="providerForm.moderateAvatars" />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item :label="t('admin.aiModeration.rubric')">
                <a-textarea
                  v-model="providerForm.rubric"
                  :max-length="30000"
                  show-word-limit
                  :auto-size="{ minRows: 10, maxRows: 22 }"
                />
                <template #extra>
                  {{ t('admin.aiModeration.rubricHint') }}
                </template>
              </a-form-item>

              <a-button
                type="primary"
                :loading="savingProvider"
                @click="saveProvider"
              >
                <template #icon><IconSave /></template>
                {{ t('admin.aiModeration.saveProvider') }}
              </a-button>
            </a-form>
          </a-card>

          <a-card class="surface-card test-card">
            <template #title>{{ t('admin.aiModeration.testTitle') }}</template>
            <a-textarea
              v-model="testContent"
              :placeholder="t('admin.aiModeration.testPlaceholder')"
              :auto-size="{ minRows: 4, maxRows: 10 }"
            />
            <div class="test-actions">
              <a-button :loading="testing" @click="testModel">
                <template #icon><IconSend /></template>
                {{ t('admin.aiModeration.test') }}
              </a-button>
            </div>
            <a-alert
              v-if="testResult"
              :type="testResult.approved ? 'success' : 'error'"
              show-icon
              :title="
                testResult.approved
                  ? t('admin.aiModeration.testPassed')
                  : t('admin.aiModeration.testRejected')
              "
            >
              <div class="test-result">
                <span>{{ testResult.reason || 'N/A' }}</span>
                <a-space wrap>
                  <a-tag
                    v-for="category in testResult.categories"
                    :key="category"
                    color="red"
                  >
                    {{ category }}
                  </a-tag>
                  <a-tag color="gray">
                    confidence {{ testResult.confidence.toFixed(2) }}
                  </a-tag>
                </a-space>
              </div>
            </a-alert>
          </a-card>
        </a-col>

        <a-col :xs="24" :xl="9">
          <a-card class="surface-card">
            <template #title>{{ t('admin.aiModeration.commentsTitle') }}</template>
            <p class="section-copy">
              {{ t('admin.aiModeration.commentsDescription') }}
            </p>
            <a-form :model="commentForm" layout="vertical">
              <a-form-item :label="t('admin.aiModeration.commentsEnabled')">
                <a-switch v-model="commentForm.commentsEnabled" />
              </a-form-item>
              <a-form-item :label="t('admin.aiModeration.commentsVisible')">
                <a-switch v-model="commentForm.commentsVisible" />
              </a-form-item>
              <a-row :gutter="12">
                <a-col :span="12">
                  <a-form-item :label="t('admin.aiModeration.maxLength')">
                    <a-input-number
                      v-model="commentForm.maxLength"
                      :min="100"
                      :max="20000"
                      style="width: 100%"
                    />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item :label="t('admin.aiModeration.hourlyLimit')">
                    <a-input-number
                      v-model="commentForm.hourlyLimit"
                      :min="1"
                      :max="500"
                      style="width: 100%"
                    />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-button
                type="primary"
                :loading="savingComments"
                @click="saveComments"
              >
                <template #icon><IconSave /></template>
                {{ t('admin.aiModeration.saveComments') }}
              </a-button>
            </a-form>

            <a-divider />
            <div class="review-actions">
              <a-popconfirm
                :content="t('admin.aiModeration.requeueConfirm')"
                @ok="requeueAll"
              >
                <a-button
                  status="warning"
                  :loading="requeueing"
                  :disabled="!data?.settings.enabled"
                >
                  <template #icon><IconRefresh /></template>
                  {{ t('admin.aiModeration.requeueAll') }}
                </a-button>
              </a-popconfirm>
              <a-button
                :loading="retrying"
                :disabled="!metrics?.failed && !metrics?.running"
                @click="retryFailed"
              >
                <template #icon><IconExclamationCircle /></template>
                {{ t('admin.aiModeration.retryFailed') }}
              </a-button>
            </div>
          </a-card>
        </a-col>
      </a-row>

      <a-card class="surface-card jobs-card">
        <template #title>
          <span class="jobs-title">
            <IconCheckCircle />
            {{ t('admin.aiModeration.jobsTitle') }}
          </span>
        </template>
        <a-table
          v-if="data?.recentJobs.length"
          :columns="jobColumns"
          :data="data.recentJobs"
          :pagination="false"
          :scroll="{ x: 1080 }"
          row-key="id"
          size="small"
          stripe
        >
          <template #type="{ record }">
            <a-tag color="arcoblue">{{ record.contentType }}</a-tag>
          </template>
          <template #target="{ record }">
            <code>#{{ record.contentRefId }} @ v{{ record.contentVersion }}</code>
          </template>
          <template #attempts="{ record }">
            {{ record.attemptCount }} / {{ record.maxAttempts }}
          </template>
          <template #status="{ record }">
            <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
          </template>
          <template #result="{ record }">
            <span class="job-result" :title="jobResult(record)">
              {{ jobResult(record) }}
            </span>
          </template>
          <template #createdAt="{ record }">
            {{ formatDate(record.createdAt) }}
          </template>
        </a-table>
        <a-empty v-else :description="t('admin.aiModeration.jobsEmpty')" />
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.ai-moderation-page {
  width: min(1180px, 100%);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 16px 18px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--app-radius);
}

.metric-card span {
  color: var(--color-text-3);
  font-size: 11px;
}

.metric-card strong {
  font-size: 27px;
  letter-spacing: -0.03em;
}

.metric-card--orange { border-top: 3px solid rgb(var(--orange-6)); }
.metric-card--blue { border-top: 3px solid rgb(var(--blue-6)); }
.metric-card--green { border-top: 3px solid rgb(var(--green-6)); }
.metric-card--red { border-top: 3px solid rgb(var(--red-6)); }
.metric-card--gray { border-top: 3px solid var(--color-text-3); }

.test-card,
.jobs-card {
  margin-top: 16px;
}

.test-actions {
  display: flex;
  justify-content: flex-end;
  margin: 12px 0;
}

.test-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.section-copy {
  margin: 0 0 18px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.7;
}

.review-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.review-actions :deep(.arco-btn) {
  width: 100%;
}

.jobs-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.job-result {
  display: block;
  overflow: hidden;
  color: var(--color-text-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.test-card :deep(.arco-alert) {
  margin-top: 4px;
}

@media (max-width: 900px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
