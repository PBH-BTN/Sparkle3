<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import {
  IconDelete,
  IconEdit,
  IconEmail,
  IconPlus,
  IconRefresh,
  IconSend,
} from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { adminApi } from '@/api/admin'
import type {
  VersionAlertDelivery,
  VersionAlertMatch,
  VersionAlertRule,
  VersionAlertRuleInput,
  VersionAlertSeverity,
} from '@/api/adminTypes'
import { useSessionStore } from '@/stores/session'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const session = useSessionStore()
const { formatDate } = useFormatters()
const templateTokens = [
  '{{nickname}}',
  '{{product}}',
  '{{version}}',
  '{{requirement}}',
  '{{userAgent}}',
  '{{ruleName}}',
  '{{operatorId}}',
]
const rules = ref<VersionAlertRule[]>([])
const deliveries = ref<VersionAlertDelivery[]>([])
const loading = ref(true)

const editorVisible = ref(false)
const editorSaving = ref(false)
const editingId = ref<string | null>(null)
const editor = reactive<VersionAlertRuleInput>({
  name: '',
  enabled: true,
  product: 'PeerBanHelper',
  versionRequirement: '',
  severity: 'WARNING',
  subject: '',
  body: '',
  oncePerUser: true,
})

const testVisible = ref(false)
const testRule = ref<VersionAlertRule | null>(null)
const matchLoading = ref(false)
const testSending = ref(false)
const matchResult = ref<VersionAlertMatch | null>(null)
const testForm = reactive({
  recipient: '',
  userAgent: 'PeerBanHelper/9.3.0',
})

const ruleColumns = computed<TableColumnData[]>(() => [
  { title: t('admin.versionAlerts.rule'), slotName: 'rule', width: 260 },
  { title: t('admin.versionAlerts.versionRequirement'), slotName: 'version', width: 220 },
  { title: t('admin.versionAlerts.severity'), slotName: 'severity', width: 130 },
  { title: t('admin.versionAlerts.status'), slotName: 'status', width: 100 },
  { title: t('admin.versionAlerts.updatedAt'), slotName: 'updatedAt', width: 180 },
  { title: t('admin.versionAlerts.actions'), slotName: 'actions', width: 240 },
])

const deliveryColumns = computed<TableColumnData[]>(() => [
  { title: t('admin.versionAlerts.deliveryRule'), slotName: 'rule', width: 210 },
  { title: t('admin.versionAlerts.deliveryRecipient'), slotName: 'recipient', width: 220 },
  { title: t('admin.versionAlerts.deliveryVersion'), dataIndex: 'version', width: 100 },
  { title: t('admin.versionAlerts.deliveryTrigger'), slotName: 'trigger', width: 120 },
  { title: t('admin.versionAlerts.deliveryStatus'), slotName: 'status', width: 100 },
  { title: t('admin.versionAlerts.deliveryTime'), slotName: 'time', width: 180 },
  { title: t('admin.versionAlerts.error'), slotName: 'error', width: 240 },
])

async function load() {
  loading.value = true
  try {
    const [ruleResult, deliveryResult] = await Promise.all([
      adminApi.versionAlerts(),
      adminApi.versionAlertDeliveries(100),
    ])
    rules.value = ruleResult
    deliveries.value = deliveryResult
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

function severityColor(severity: VersionAlertSeverity) {
  return severity === 'SECURITY' ? 'red' : severity === 'WARNING' ? 'orange' : 'arcoblue'
}

function severityLabel(severity: VersionAlertSeverity) {
  return t(`admin.versionAlerts.severityLevels.${severity}`)
}

function openCreate() {
  editingId.value = null
  Object.assign(editor, {
    name: '',
    enabled: true,
    product: 'PeerBanHelper',
    versionRequirement: '',
    severity: 'WARNING',
    subject: '',
    body: '',
    oncePerUser: true,
  })
  editorVisible.value = true
}

function openEdit(rule: VersionAlertRule) {
  editingId.value = rule.id
  Object.assign(editor, {
    name: rule.name,
    enabled: rule.enabled,
    product: rule.product,
    versionRequirement: rule.versionRequirement,
    severity: rule.severity,
    subject: rule.subject,
    body: rule.body,
    oncePerUser: rule.oncePerUser,
  })
  editorVisible.value = true
}

async function saveRule() {
  if (
    !editor.name.trim() ||
    !editor.product.trim() ||
    !editor.versionRequirement.trim() ||
    !editor.subject.trim() ||
    !editor.body.trim()
  ) {
    Message.warning(t('admin.common.requiredFields'))
    return
  }
  editorSaving.value = true
  try {
    if (editingId.value == null) {
      await adminApi.createVersionAlert(editor)
      Message.success(t('admin.versionAlerts.created'))
    } else {
      await adminApi.updateVersionAlert(editingId.value, editor)
      Message.success(t('admin.versionAlerts.updated'))
    }
    editorVisible.value = false
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    editorSaving.value = false
  }
}

async function deleteRule(rule: VersionAlertRule) {
  try {
    await adminApi.deleteVersionAlert(rule.id)
    Message.success(t('admin.versionAlerts.deleted'))
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  }
}

function openTest(rule: VersionAlertRule) {
  testRule.value = rule
  testForm.recipient = session.user?.email ?? ''
  testForm.userAgent = `${rule.product}/9.3.0`
  matchResult.value = null
  testVisible.value = true
}

async function checkMatch() {
  if (!testRule.value) return
  matchLoading.value = true
  try {
    matchResult.value = await adminApi.matchVersionAlert(
      testRule.value.id,
      testForm.userAgent,
    )
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    matchLoading.value = false
  }
}

async function sendTest() {
  if (!testRule.value) return
  testSending.value = true
  try {
    await adminApi.testVersionAlert(
      testRule.value.id,
      testForm.recipient,
      testForm.userAgent,
    )
    Message.success(t('admin.versionAlerts.testSent'))
    testVisible.value = false
    await load()
  } catch (error) {
    Message.error(errorMessage(error, t('common.requestFailed')))
  } finally {
    testSending.value = false
  }
}

watch(
  () => testForm.userAgent,
  () => {
    matchResult.value = null
  },
)

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader
      :title="t('admin.versionAlerts.title')"
      :description="t('admin.versionAlerts.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('admin.versionAlerts.refresh') }}
        </a-button>
        <a-button type="primary" @click="openCreate">
          <template #icon><IconPlus /></template>
          {{ t('admin.versionAlerts.create') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="info" show-icon>
      <template #title>
        {{ t('admin.versionAlerts.triggerHint') }}
      </template>
    </a-alert>

    <a-spin :loading="loading" class="alerts-spin">
      <a-card class="surface-card table-card">
        <a-table
          :columns="ruleColumns"
          :data="rules"
          :pagination="false"
          :scroll="{ x: 1130 }"
          row-key="id"
        >
          <template #empty><a-empty :description="t('admin.versionAlerts.noRules')" /></template>
          <template #rule="{ record }">
            <div class="rule-cell">
              <span class="rule-icon"><IconEmail /></span>
              <div>
                <strong>{{ record.name }}</strong>
                <span>{{ record.product }}</span>
              </div>
            </div>
          </template>
          <template #version="{ record }">
            <code class="version-rule">{{ record.versionRequirement }}</code>
          </template>
          <template #severity="{ record }">
            <a-tag :color="severityColor(record.severity)">
              {{ severityLabel(record.severity) }}
            </a-tag>
          </template>
          <template #status="{ record }">
            <a-tag :color="record.enabled ? 'green' : 'gray'">
              {{ record.enabled ? t('admin.common.enabled') : t('admin.common.disabled') }}
            </a-tag>
          </template>
          <template #updatedAt="{ record }">{{ formatDate(record.updatedAt) }}</template>
          <template #actions="{ record }">
            <a-space>
              <a-button type="text" size="small" @click="openEdit(record)">
                <template #icon><IconEdit /></template>
                {{ t('admin.common.edit') }}
              </a-button>
              <a-button type="text" size="small" @click="openTest(record)">
                <template #icon><IconSend /></template>
                {{ t('admin.versionAlerts.test') }}
              </a-button>
              <a-popconfirm
                :content="t('admin.versionAlerts.deleteConfirm')"
                @ok="deleteRule(record)"
              >
                <a-button status="danger" type="text" size="small">
                  <template #icon><IconDelete /></template>
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table>
      </a-card>

      <a-card class="surface-card deliveries-card" :title="t('admin.versionAlerts.deliveries')">
        <a-table
          :columns="deliveryColumns"
          :data="deliveries"
          :pagination="false"
          :scroll="{ x: 1300, y: 420 }"
          row-key="id"
          size="small"
        >
          <template #empty>
            <a-empty :description="t('admin.versionAlerts.noDeliveries')" />
          </template>
          <template #rule="{ record }">{{ record.ruleName }}</template>
          <template #recipient="{ record }">
            <div class="recipient-cell">
              <span>{{ record.email }}</span>
              <small v-if="record.nickname">{{ record.nickname }}</small>
            </div>
          </template>
          <template #trigger="{ record }">
            <a-tag :color="record.trigger === 'AUTOMATIC' ? 'arcoblue' : 'gray'">
              {{
                record.trigger === 'AUTOMATIC'
                  ? t('admin.versionAlerts.automatic')
                  : t('admin.versionAlerts.testTrigger')
              }}
            </a-tag>
          </template>
          <template #status="{ record }">
            <a-tag :color="record.status === 'SENT' ? 'green' : 'red'">
              {{
                record.status === 'SENT'
                  ? t('admin.versionAlerts.sent')
                  : t('admin.versionAlerts.failed')
              }}
            </a-tag>
          </template>
          <template #time="{ record }">{{ formatDate(record.sentAt) }}</template>
          <template #error="{ record }">
            <span v-if="record.error" class="error-text" :title="record.error">
              {{ record.error }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </a-table>
      </a-card>
    </a-spin>

    <a-modal
      v-model:visible="editorVisible"
      :title="editingId == null ? t('admin.versionAlerts.create') : t('admin.versionAlerts.edit')"
      :footer="false"
      width="820px"
    >
      <a-form :model="editor" layout="vertical">
        <a-row :gutter="16">
          <a-col :xs="24" :md="16">
            <a-form-item :label="t('admin.versionAlerts.name')" required>
              <a-input v-model="editor.name" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="8">
            <a-form-item :label="t('admin.versionAlerts.enabled')">
              <a-switch v-model="editor.enabled" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="10">
            <a-form-item :label="t('admin.versionAlerts.product')" required>
              <a-input v-model="editor.product" />
              <template #extra>{{ t('admin.versionAlerts.productHint') }}</template>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="8">
            <a-form-item :label="t('admin.versionAlerts.versionRequirement')" required>
              <a-input v-model="editor.versionRequirement" />
              <template #extra>{{ t('admin.versionAlerts.versionHint') }}</template>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="6">
            <a-form-item :label="t('admin.versionAlerts.severity')">
              <a-select v-model="editor.severity">
                <a-option value="INFO">
                  {{ t('admin.versionAlerts.severityLevels.INFO') }}
                </a-option>
                <a-option value="WARNING">
                  {{ t('admin.versionAlerts.severityLevels.WARNING') }}
                </a-option>
                <a-option value="SECURITY">
                  {{ t('admin.versionAlerts.severityLevels.SECURITY') }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item :label="t('admin.versionAlerts.oncePerUser')">
              <a-switch v-model="editor.oncePerUser" />
              <template #extra>{{ t('admin.versionAlerts.onceHint') }}</template>
            </a-form-item>
          </a-col>
          <a-col :xs="24">
            <a-form-item :label="t('admin.versionAlerts.subject')" required>
              <a-input v-model="editor.subject" />
            </a-form-item>
          </a-col>
          <a-col :xs="24">
            <a-form-item :label="t('admin.versionAlerts.body')" required>
              <a-textarea
                v-model="editor.body"
                :auto-size="{ minRows: 10, maxRows: 24 }"
                class="mail-editor"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-alert type="info" class="template-hint">
          <template #title>{{ t('admin.versionAlerts.templateVariables') }}</template>
          {{ t('admin.versionAlerts.templateHint') }}
          <div class="template-tokens">
            <code v-for="token in templateTokens" :key="token">{{ token }}</code>
          </div>
        </a-alert>
        <div class="modal-actions">
          <a-button @click="editorVisible = false">{{ t('admin.common.cancel') }}</a-button>
          <a-button type="primary" :loading="editorSaving" @click="saveRule">
            {{ t('admin.versionAlerts.save') }}
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="testVisible"
      :title="t('admin.versionAlerts.testTitle')"
      :footer="false"
      width="640px"
    >
      <a-alert type="warning" show-icon>
        {{ t('admin.versionAlerts.actualEmailWarning') }}
      </a-alert>
      <a-form :model="testForm" layout="vertical" class="test-form">
        <a-form-item :label="t('admin.versionAlerts.recipient')" required>
          <a-input v-model="testForm.recipient" />
        </a-form-item>
        <a-form-item :label="t('admin.versionAlerts.sampleUserAgent')" required>
          <a-input v-model="testForm.userAgent" />
        </a-form-item>
        <div class="test-actions">
          <a-button :loading="matchLoading" @click="checkMatch">
            {{ t('admin.versionAlerts.checkMatch') }}
          </a-button>
          <a-button
            type="primary"
            :loading="testSending"
            :disabled="matchResult?.matched !== true"
            @click="sendTest"
          >
            <template #icon><IconSend /></template>
            {{ t('admin.versionAlerts.sendTest') }}
          </a-button>
        </div>
        <a-alert
          v-if="matchResult"
          :type="matchResult.matched ? 'success' : 'error'"
          show-icon
          class="match-result"
        >
          {{
            matchResult.matched
              ? t('admin.versionAlerts.matched', { version: matchResult.version })
              : t('admin.versionAlerts.notMatched')
          }}
        </a-alert>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.alerts-spin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.rule-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rule-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: rgb(var(--primary-6));
  background: var(--color-primary-light-1);
  border-radius: var(--app-radius);
}

.rule-cell > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.rule-cell span {
  color: var(--color-text-3);
  font-size: 11px;
}

.rule-cell strong,
.rule-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-rule {
  padding: 3px 7px;
  font-size: 12px;
  background: var(--color-fill-1);
  border-radius: var(--app-radius);
}

.recipient-cell {
  display: flex;
  flex-direction: column;
}

.recipient-cell small {
  color: var(--color-text-3);
  font-size: 10px;
}

.error-text {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  color: rgb(var(--red-6));
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deliveries-card {
  margin-top: 16px;
}

.mail-editor :deep(textarea) {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.template-hint {
  margin-bottom: 16px;
}

.template-tokens {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.template-tokens code {
  padding: 1px 5px;
  color: var(--color-text-2);
  font-size: 11px;
  background: var(--color-fill-2);
  border-radius: var(--app-radius);
}

.modal-actions,
.test-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.test-form {
  margin-top: 18px;
}

.match-result {
  margin-top: 16px;
}
</style>
