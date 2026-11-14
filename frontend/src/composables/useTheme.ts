import { computed, readonly, ref } from 'vue'
export type ThemeMode = 'system' | 'light' | 'dark'
export const themeModes: ThemeMode[] = ['system', 'light', 'dark']
const storageKey = 'sparkle:theme'
const media = window.matchMedia('(prefers-color-scheme: dark)')
function readMode(): ThemeMode {
  try {
    const value = localStorage.getItem(storageKey) as ThemeMode
    return themeModes.includes(value) ? value : 'system'
  } catch { return 'system' }
}
const mode = ref<ThemeMode>(readMode())
const systemDark = ref(media.matches)
const isDark = computed(() => mode.value === 'dark' || (mode.value === 'system' && systemDark.value))
function applyTheme() {
  if (isDark.value) document.body.setAttribute('arco-theme', 'dark')
  else document.body.removeAttribute('arco-theme')
  document.documentElement.style.colorScheme = isDark.value ? 'dark' : 'light'
  document.documentElement.dataset.theme = isDark.value ? 'dark' : 'light'
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', isDark.value ? '#17171a' : '#ffffff')
}
export function setThemeMode(value: ThemeMode) {
  mode.value = value
  try { localStorage.setItem(storageKey, value) } catch { /* Retain the session preference. */ }
  applyTheme()
}
media.addEventListener('change', (event) => { systemDark.value = event.matches; applyTheme() })
window.addEventListener('storage', (event) => {
  if (event.key === storageKey || event.key === null) { mode.value = readMode(); applyTheme() }
})
applyTheme()
export function useTheme() { return { mode: readonly(mode), isDark, setThemeMode } }
