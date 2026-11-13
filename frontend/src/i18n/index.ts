import { createI18n } from 'vue-i18n'
import { footerMessages } from './footer'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export type AppLocale = 'zh-CN' | 'en-US'

export const supportedLocales: Array<{ value: AppLocale; label: string }> = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' },
]

const localeStorageKey = 'sparkle-locale'

function resolveInitialLocale(): AppLocale {
  const savedLocale = localStorage.getItem(localeStorageKey)
  if (savedLocale === 'zh-CN' || savedLocale === 'en-US') {
    return savedLocale
  }
  return navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
}

export const initialLocale = resolveInitialLocale()

const i18n = createI18n({
  legacy: false,
  locale: initialLocale,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': { ...zhCN, ...footerMessages['zh-CN'] },
    'en-US': { ...enUS, ...footerMessages['en-US'] },
  },
})

export function setLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  localStorage.setItem(localeStorageKey, locale)
  document.documentElement.lang = locale
  document.title = i18n.global.t('app.title')
}

setLocale(initialLocale)

export default i18n
