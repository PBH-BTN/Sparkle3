<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  onBeforeUnmount,
  onMounted,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import zhCN from '@arco-design/web-vue/es/locale/lang/zh-cn'
import enUS from '@arco-design/web-vue/es/locale/lang/en-us'
import AppLayout from '@/components/AppLayout.vue'
import GlobalAnnouncements from '@/components/GlobalAnnouncements.vue'
import { useSessionStore } from '@/stores/session'

const LoginView = defineAsyncComponent(() => import('@/views/LoginView.vue'))

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { locale } = useI18n()
let sessionRefreshTimer: number | undefined

const arcoLocale = computed(() => (locale.value === 'zh-CN' ? zhCN : enUS))
const isStandalone = computed(() => Boolean(route.meta.standalone) || route.name === 'login')
const showLogin = computed(
  () =>
    !isStandalone.value &&
    route.name !== 'login' &&
    session.loaded &&
    !session.authenticated,
)

function handleUnauthorized() {
  session.clear()
  router.replace('/login')
}

watch(
  () => [session.authenticated, session.user?.admin, route.name, route.meta.admin] as const,
  ([authenticated, admin, routeName, requiresAdmin]) => {
    if (authenticated && routeName === 'login') {
      router.replace('/')
    }
    if (authenticated && requiresAdmin && !admin) {
      router.replace('/')
    }
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('sparkle:unauthorized', handleUnauthorized)
  session.load().catch(() => undefined)
  sessionRefreshTimer = window.setInterval(() => {
    if (session.authenticated && document.visibilityState === 'visible') {
      session.load().catch(() => undefined)
    }
  }, 60_000)
})

onBeforeUnmount(() => {
  window.removeEventListener('sparkle:unauthorized', handleUnauthorized)
  window.clearInterval(sessionRefreshTimer)
})
</script>

<template>
  <a-config-provider :locale="arcoLocale">
    <GlobalAnnouncements />

    <a-spin
      v-if="!isStandalone && (!session.loaded || (session.authenticated && !session.user))"
      class="app-loading"
      :size="32"
    />

    <router-view v-else-if="isStandalone" />

    <LoginView v-else-if="showLogin" />

    <AppLayout v-else-if="session.authenticated">
      <router-view />
    </AppLayout>

    <LoginView v-else />
  </a-config-provider>
</template>
