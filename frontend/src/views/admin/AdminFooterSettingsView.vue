<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { IconSettings, IconSave } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { useAdminDraft } from '@/composables/useAdminDraft'
import { footerApi, type FooterConfig, type FooterMetricKey } from '@/api/footer'
import { errorMessage } from '@/utils/format'
const { t } = useI18n()
const loading = ref(true)
const saving = ref(false)
const failed = ref(false)
const performanceMetricKeys: FooterMetricKey[] = [
  'loadAverage',
  'cpu',
  'memory',
  'systemUptime',
  'serverUptime',
  'diskIo',
  'network',
  'httpRps',
  'sqlQps',
  'redisQps',
  'httpApiAverage',
  'httpApiP99',
  'httpPingAverage',
  'httpPingP99',
  'httpOtherAverage',
  'httpOtherP99',
]
const footerForm = reactive<FooterConfig>({
  supportButtons: [],
  disclaimerZh: '',
  disclaimerEn: '',
  disclaimerAudience: 'AUTHENTICATED',
  icp: '',
  icpAudience: 'AUTHENTICATED',
  columns: [],
  columnsAudience: 'AUTHENTICATED',
  timeAudience: 'AUTHENTICATED',
  loginAudience: 'AUTHENTICATED',
  performanceMetrics: performanceMetricKeys.map((key) => ({
    key,
    enabled: true,
    audience: 'ADMIN',
  })),
})
const audienceOptions = computed(() => [
  {
    label: t('siteFooterAdmin.authenticated'),
    value: 'AUTHENTICATED',
  },
  {
    label: t('siteFooterAdmin.adminOnly'),
    value: 'ADMIN',
  },
])
const draft = useAdminDraft(() => footerForm)

function applyFooterConfig(config: FooterConfig) {
  const normalized = structuredClone(config)
  normalized.supportButtons ??= []
  const configuredMetrics = new Map(
    normalized.performanceMetrics.map((metric) => [metric.key, metric]),
  )
  normalized.performanceMetrics = performanceMetricKeys.map(
    (key) =>
      configuredMetrics.get(key) ?? {
        key,
        enabled: true,
        audience: 'ADMIN',
      },
  )
  Object.assign(footerForm, normalized)
  draft.accept()
}

function validateFooter() {
  for (const button of footerForm.supportButtons) {
    const validUrl = (value: string) => /^(\/(?!\/)[^\\\s]*|https?:\/\/[^\s\\]+)$/i.test(value.trim())
    if ((!button.labelZh.trim() && !button.labelEn.trim()) || !validUrl(button.href) ||
      !validUrl(button.iconUrl)) {
      Message.warning(t('siteFooterAdmin.invalidSupportButton'))
      return false
    }
  }
  for (const column of footerForm.columns) {
    if (!column.titleZh.trim() && !column.titleEn.trim()) {
      Message.warning(t('siteFooterAdmin.invalidColumn'))
      return false
    }
    for (const link of column.links) {
      if (
        (!link.labelZh.trim() && !link.labelEn.trim()) ||
        !link.href.trim() ||
        (!link.href.startsWith('/') &&
          !/^https?:\/\/[^\s]+$/i.test(link.href.trim()))
      ) {
        Message.warning(t('siteFooterAdmin.invalidLink'))
        return false
      }
    }
  }
  return true
}

function addSupportButton() {
  if (footerForm.supportButtons.length >= 12) return
  footerForm.supportButtons.push({ labelZh: '', labelEn: '', href: '', iconUrl: '' })
}

function moveSupportButton(index: number, direction: number) {
  const target = index + direction
  if (target < 0 || target >= footerForm.supportButtons.length) return
  const [button] = footerForm.supportButtons.splice(index, 1)
  if (button) footerForm.supportButtons.splice(target, 0, button)
}

function addFooterColumn() {
  if (footerForm.columns.length >= 12) return
  footerForm.columns.push({
    titleZh: '',
    titleEn: '',
    links: [],
  })
}

function removeFooterColumn(index: number) {
  footerForm.columns.splice(index, 1)
}

function addFooterLink(columnIndex: number) {
  const column = footerForm.columns[columnIndex]
  if (!column || column.links.length >= 30) return
  column.links.push({
    labelZh: '',
    labelEn: '',
    href: '',
  })
}

function removeFooterLink(columnIndex: number, linkIndex: number) {
  footerForm.columns[columnIndex]?.links.splice(linkIndex, 1)
}

function footerMetricLabel(key: FooterMetricKey) {
  const httpMetric = key.match(
    /^http(Api|Ping|Other)(Average|P99)$/,
  )
  if (httpMetric) {
    const categoryKey =
      httpMetric[1] === 'Api'
        ? 'api'
        : httpMetric[1] === 'Ping'
          ? 'btnPing'
          : 'other'
    const valueKey = httpMetric[2] === 'Average' ? 'average' : 'p99'
    return `${t(`siteFooter.performance.${categoryKey}`)} ${t(
      `siteFooter.performance.${valueKey}`,
    )}`
  }
  return t(`siteFooter.performance.${key}`)
}


async function load() {
  if (!(await draft.confirmDiscard())) return
  loading.value = true
  failed.value = false
  try { applyFooterConfig(await footerApi.config()) }
  catch (error) { failed.value = true; Message.error(errorMessage(error, t('common.loadFailed'))) }
  finally { loading.value = false }
}
async function save() {
  if (failed.value || loading.value || !validateFooter()) return
  saving.value = true
  try { applyFooterConfig(await footerApi.updateConfig(footerForm)); Message.success(t('admin.common.updated')) }
  catch (error) { Message.error(errorMessage(error, t('common.requestFailed'))) }
  finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <div class="page-stack">
    <PageHeader :title="t('admin.organization.pages.footer')">
      <template #actions><a-button type="primary" :loading="saving" :disabled="loading || failed" @click="save"><template #icon><IconSave /></template>{{ t('admin.common.save') }}</a-button></template>
    </PageHeader>
    <a-alert v-if="failed" type="error">{{ t('admin.organization.loadFailed') }}<template #action><a-button @click="load">{{ t('common.retry') }}</a-button></template></a-alert>
    <a-spin :loading="loading" class="settings-spin">
      <a-card class="surface-card footer-settings-card">
        <template #title>
          <span class="card-title">
            <IconSettings />
            {{ t('siteFooterAdmin.title') }}
          </span>
        </template>
        <template #extra>
          <span class="footer-settings-description">
            {{ t('siteFooterAdmin.description') }}
          </span>
        </template>

        <a-form :model="footerForm" layout="vertical">
          <section class="footer-settings-section">
            <div class="footer-section-heading">
              <div>
                <strong>{{ t('siteFooterAdmin.supportTitle') }}</strong>
                <p>{{ t('siteFooterAdmin.supportDescription') }}</p>
              </div>
              <a-button :disabled="footerForm.supportButtons.length >= 12" @click="addSupportButton">
                {{ t('siteFooterAdmin.addSupportButton') }}
              </a-button>
            </div>
            <p v-if="!footerForm.supportButtons.length">{{ t('siteFooterAdmin.noSupportButtons') }}</p>
            <div class="footer-column-editor-list">
              <div v-for="(button, index) in footerForm.supportButtons" :key="index" class="footer-column-editor">
                <div class="settings-grid">
                  <a-form-item :label="t('siteFooterAdmin.supportAltZh')"><a-input v-model="button.labelZh" :max-length="100" /></a-form-item>
                  <a-form-item :label="t('siteFooterAdmin.supportAltEn')"><a-input v-model="button.labelEn" :max-length="100" /></a-form-item>
                  <a-form-item :label="t('siteFooterAdmin.supportLink')"><a-input v-model="button.href" :max-length="2048" /></a-form-item>
                  <a-form-item :label="t('siteFooterAdmin.supportIcon')"><a-input v-model="button.iconUrl" :max-length="2048" /></a-form-item>
                </div>
                <a-space wrap>
                  <a-button :disabled="index === 0" @click="moveSupportButton(index, -1)">{{ t('siteFooterAdmin.moveEarlier') }}</a-button>
                  <a-button :disabled="index === footerForm.supportButtons.length - 1" @click="moveSupportButton(index, 1)">{{ t('siteFooterAdmin.moveLater') }}</a-button>
                  <a-button status="danger" @click="footerForm.supportButtons.splice(index, 1)">{{ t('siteFooterAdmin.removeLink') }}</a-button>
                </a-space>
              </div>
            </div>
          </section>
          <a-divider />
          <section class="footer-settings-section">
            <div class="footer-section-heading">
              <div>
                <strong>{{ t('siteFooterAdmin.disclaimerTitle') }}</strong>
                <p>{{ t('siteFooterAdmin.disclaimerDescription') }}</p>
              </div>
              <a-select
                v-model="footerForm.disclaimerAudience"
                :options="audienceOptions"
                class="audience-select"
              />
            </div>
            <div class="settings-grid">
              <a-form-item :label="t('siteFooterAdmin.disclaimerZh')">
                <a-textarea
                  v-model="footerForm.disclaimerZh"
                  :auto-size="{ minRows: 5, maxRows: 12 }"
                  :max-length="20000"
                />
              </a-form-item>
              <a-form-item :label="t('siteFooterAdmin.disclaimerEn')">
                <a-textarea
                  v-model="footerForm.disclaimerEn"
                  :auto-size="{ minRows: 5, maxRows: 12 }"
                  :max-length="20000"
                />
              </a-form-item>
            </div>
            <div class="settings-grid">
              <a-form-item :label="t('siteFooterAdmin.icp')">
                <a-input v-model="footerForm.icp" :max-length="128" />
                <template #extra>{{ t('siteFooterAdmin.icpHint') }}</template>
              </a-form-item>
              <a-form-item :label="t('siteFooterAdmin.icpAudience')">
                <a-select
                  v-model="footerForm.icpAudience"
                  :options="audienceOptions"
                />
              </a-form-item>
            </div>
          </section>

          <a-divider />

          <section class="footer-settings-section">
            <div class="footer-section-heading">
              <div>
                <strong>{{ t('siteFooterAdmin.columnsTitle') }}</strong>
                <p>{{ t('siteFooterAdmin.columnsDescription') }}</p>
              </div>
              <div class="footer-section-actions">
                <a-select
                  v-model="footerForm.columnsAudience"
                  :options="audienceOptions"
                  class="audience-select"
                />
                <a-button type="outline" @click="addFooterColumn">
                  {{ t('siteFooterAdmin.addColumn') }}
                </a-button>
              </div>
            </div>

            <p v-if="footerForm.columns.length === 0" class="empty-columns">
              {{ t('siteFooterAdmin.noColumns') }}
            </p>
            <div class="footer-column-editor-list">
              <div
                v-for="(column, columnIndex) in footerForm.columns"
                :key="columnIndex"
                class="footer-column-editor"
              >
                <div class="footer-column-editor-heading">
                  <strong>
                    {{
                      t('siteFooterAdmin.column', {
                        index: columnIndex + 1,
                      })
                    }}
                  </strong>
                  <a-button
                    type="text"
                    size="small"
                    @click="removeFooterColumn(columnIndex)"
                  >
                    {{ t('siteFooterAdmin.removeColumn') }}
                  </a-button>
                </div>
                <div class="settings-grid">
                  <a-form-item :label="t('siteFooterAdmin.columnTitleZh')">
                    <a-input v-model="column.titleZh" :max-length="100" />
                  </a-form-item>
                  <a-form-item :label="t('siteFooterAdmin.columnTitleEn')">
                    <a-input v-model="column.titleEn" :max-length="100" />
                  </a-form-item>
                </div>

                <div class="footer-link-list">
                  <div class="field-label">{{ t('siteFooterAdmin.links') }}</div>
                  <div
                    v-for="(link, linkIndex) in column.links"
                    :key="linkIndex"
                    class="footer-link-editor"
                  >
                    <a-input
                      v-model="link.labelZh"
                      :placeholder="t('siteFooterAdmin.linkLabelZh')"
                      :max-length="100"
                    />
                    <a-input
                      v-model="link.labelEn"
                      :placeholder="t('siteFooterAdmin.linkLabelEn')"
                      :max-length="100"
                    />
                    <a-input
                      v-model="link.href"
                      :placeholder="t('siteFooterAdmin.linkUrlPlaceholder')"
                      :max-length="2048"
                    />
                    <a-button type="text" @click="removeFooterLink(columnIndex, linkIndex)">
                      {{ t('siteFooterAdmin.removeLink') }}
                    </a-button>
                  </div>
                  <a-button
                    type="dashed"
                    long
                    @click="addFooterLink(columnIndex)"
                  >
                    {{ t('siteFooterAdmin.addLink') }}
                  </a-button>
                </div>
              </div>
            </div>
          </section>

          <a-divider />

          <section class="footer-settings-section">
            <div class="settings-grid">
              <a-form-item :label="t('siteFooterAdmin.timeAudience')">
                <a-select
                  v-model="footerForm.timeAudience"
                  :options="audienceOptions"
                />
                <template #extra>
                  {{ t('siteFooterAdmin.timeDescription') }}
                </template>
              </a-form-item>
              <a-form-item :label="t('siteFooterAdmin.loginAudience')">
                <a-select
                  v-model="footerForm.loginAudience"
                  :options="audienceOptions"
                />
                <template #extra>
                  {{ t('siteFooterAdmin.loginDescription') }}
                </template>
              </a-form-item>
            </div>
          </section>

          <a-divider />

          <section class="footer-settings-section">
            <div class="footer-section-heading">
              <div>
                <strong>{{ t('siteFooterAdmin.performanceTitle') }}</strong>
                <p>{{ t('siteFooterAdmin.performanceDescription') }}</p>
              </div>
            </div>
            <div class="metric-visibility-grid">
              <template
                v-for="(metric, metricIndex) in footerForm.performanceMetrics"
                :key="metric.key"
              >
                <div v-if="metricIndex === 10" class="metric-group-heading">
                  <strong>{{ t('siteFooterAdmin.httpResponses') }}</strong>
                  <span>{{ t('siteFooterAdmin.httpResponsesHint') }}</span>
                </div>
                <div class="metric-visibility-row">
                  <span>{{ footerMetricLabel(metric.key) }}</span>
                  <a-switch v-model="metric.enabled" size="small" />
                  <a-select
                    v-model="metric.audience"
                    :options="audienceOptions"
                    size="small"
                    :disabled="!metric.enabled"
                  />
                </div>
              </template>
            </div>
          </section>
        </a-form>
      </a-card>

    </a-spin>
  </div>
</template>
<style scoped src="./adminSettings.css"></style>
