<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { IconCopy, IconRefresh, IconSafe } from '@arco-design/web-vue/es/icon'
import PageHeader from '@/components/PageHeader.vue'
import { uiApi } from '@/api'
import type {
  RulePreviewEntry,
  RulePreviewResponse,
  RulePreviewType,
} from '@/api/types'
import { errorMessage, useFormatters } from '@/utils/format'

const { t } = useI18n()
const { formatDate, formatNumber } = useFormatters()
const loading = ref(false)
const data = ref<RulePreviewResponse | null>(null)
const activeType = ref<RulePreviewType>('ip_denylist')
const keyword = ref('')

const sections = computed(() => data.value?.sections ?? [])
const activeSection = computed(
  () => sections.value.find((section) => section.type === activeType.value) ?? null,
)
const filteredEntries = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  const entries = activeSection.value?.entries ?? []
  if (!term) return entries
  return entries.filter((entry) =>
    [sourceLabel(entry.source), entry.category ?? '', entry.content]
      .join('\n')
      .toLowerCase()
      .includes(term),
  )
})

function sectionCount(type: RulePreviewType) {
  return sections.value.find((section) => section.type === type)?.entries.length ?? 0
}

function sourceLabel(source: string) {
  const key = `rulePreview.sources.${source}`
  const translated = t(key)
  return translated === key ? source : translated
}

async function load() {
  loading.value = true
  try {
    data.value = await uiApi.rulePreview()
    if (!data.value.sections.some((section) => section.type === activeType.value)) {
      activeType.value = data.value.sections[0]?.type ?? 'ip_denylist'
    }
  } catch (error) {
    Message.error(errorMessage(error, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function copyEntry(entry: RulePreviewEntry) {
  try {
    await navigator.clipboard.writeText(entry.content)
    Message.success(t('common.copied'))
  } catch {
    Message.error(t('rulePreview.copyFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack rule-preview-page">
    <PageHeader
      :title="t('rulePreview.title')"
      :description="t('rulePreview.description')"
    >
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><IconRefresh /></template>
          {{ t('rulePreview.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <a-alert type="warning" show-icon>
      <template #title>
        <IconSafe />
        {{ t('rulePreview.noticeTitle') }}
      </template>
      {{ t('rulePreview.notice') }}
    </a-alert>

    <a-card class="surface-card">
      <div class="preview-toolbar">
        <a-input-search
          v-model="keyword"
          :placeholder="t('rulePreview.searchPlaceholder')"
          allow-clear
        />
        <span v-if="data" class="generated-at">
          {{ t('rulePreview.generatedAt', { time: formatDate(data.generatedAt) }) }}
        </span>
      </div>

      <a-spin :loading="loading" class="rule-preview-spin">
        <a-tabs v-model:active-key="activeType" type="rounded" lazy-load>
          <a-tab-pane
            v-for="section in sections"
            :key="section.type"
            :title="`${t(`rulePreview.types.${section.type}`)} (${formatNumber(section.entries.length)})`"
          >
            <div class="section-heading">
              <div>
                <h2>{{ t(`rulePreview.types.${section.type}`) }}</h2>
                <p>{{ t(`rulePreview.typeDescriptions.${section.type}`) }}</p>
              </div>
              <a-tag color="arcoblue">
                {{ t('rulePreview.entryCount', { count: sectionCount(section.type) }) }}
              </a-tag>
            </div>

            <div v-if="filteredEntries.length" class="rule-entry-list">
              <article
                v-for="(entry, index) in filteredEntries"
                :key="`${entry.source}-${entry.category}-${index}`"
                class="rule-entry"
              >
                <header>
                  <div class="rule-entry-heading">
                    <a-tag color="gray">{{ sourceLabel(entry.source) }}</a-tag>
                    <strong>{{ entry.category || t('rulePreview.generatedRule') }}</strong>
                  </div>
                  <div class="rule-entry-actions">
                    <span>
                      {{ t('rulePreview.characters', { count: entry.content.length }) }}
                    </span>
                    <a-button size="mini" type="text" @click="copyEntry(entry)">
                      <template #icon><IconCopy /></template>
                      {{ t('common.copy') }}
                    </a-button>
                  </div>
                </header>
                <pre>{{ entry.content || t('rulePreview.emptyGeneratedContent') }}</pre>
              </article>
            </div>
            <a-empty v-else :description="t('rulePreview.noRules')" />
          </a-tab-pane>
        </a-tabs>
      </a-spin>
    </a-card>
  </div>
</template>

<style scoped>
.rule-preview-page {
  width: min(1380px, 100%);
}

.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.preview-toolbar :deep(.arco-input-search) {
  width: min(420px, 100%);
}

.generated-at {
  color: var(--color-text-3);
  font-size: 12px;
}

.rule-preview-spin {
  display: block;
  width: 100%;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin: 4px 0 14px;
}

.section-heading h2 {
  margin: 0;
  color: var(--color-text-1);
  font-size: 18px;
  font-weight: 600;
}

.section-heading p {
  margin: 5px 0 0;
  color: var(--color-text-3);
  line-height: 1.6;
}

.rule-entry-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rule-entry {
  overflow: hidden;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.rule-entry header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border-2);
}

.rule-entry-heading,
.rule-entry-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.rule-entry-heading strong {
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-entry-actions {
  flex: 0 0 auto;
  color: var(--color-text-3);
  font-size: 11px;
}

.rule-entry pre {
  max-height: 420px;
  padding: 14px;
  margin: 0;
  overflow: auto;
  color: var(--color-text-2);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 720px) {
  .preview-toolbar,
  .section-heading,
  .rule-entry header {
    align-items: stretch;
    flex-direction: column;
  }

  .preview-toolbar :deep(.arco-input-search) {
    width: 100%;
  }

  .rule-entry-actions {
    justify-content: space-between;
  }
}
</style>
