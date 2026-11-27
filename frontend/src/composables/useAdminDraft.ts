import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Modal } from '@arco-design/web-vue'

export function useAdminDraft(value: () => unknown) {
  const { t } = useI18n()
  const baseline = ref<string>()
  const dirty = computed(() => baseline.value !== undefined && JSON.stringify(value()) !== baseline.value)
  function accept(key?: string) {
    const current = value()
    if (key && baseline.value !== undefined && current && typeof current === 'object') {
      const saved = JSON.parse(baseline.value)
      saved[key] = (current as Record<string, unknown>)[key]
      baseline.value = JSON.stringify(saved)
    } else baseline.value = JSON.stringify(current)
  }
  function confirmDiscard(): boolean | Promise<boolean> {
    return !dirty.value || new Promise<boolean>((resolve) => {
      Modal.confirm({ content: t('admin.organization.unsaved'), okText: t('common.confirm'), cancelText: t('common.cancel'), onOk: () => resolve(true), onCancel: () => resolve(false) })
    })
  }
  function beforeUnload(event: BeforeUnloadEvent) {
    if (dirty.value) { event.preventDefault(); event.returnValue = '' }
  }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
  onBeforeRouteLeave(confirmDiscard)
  return { accept, dirty, confirmDiscard }
}
