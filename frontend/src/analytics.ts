import { createClient } from '@faststats/web'
import type { RouteLocationNormalized, Router } from 'vue-router'

const officialOrigins = new Set([
  'https://sparkle.pbh-btn.com',
  'https://sparkle.ghostchu.com',
])

const trackedPaths = new Set([
  '/',
  '/login',
  '/register',
  '/client-discovery',
  '/rules/preview',
  '/ranking/swarmStatistics',
])

function canTrack(route: RouteLocationNormalized) {
  return trackedPaths.has(route.fullPath)
}

export function installAnalytics(router: Router) {
  if (!import.meta.env.PROD || !officialOrigins.has(window.location.origin)) return

  const analytics = createClient({
    siteKey: 'ae21273607c94220b3ad7a32ff5dc5e5',
    consent: 'denied',
  })
  analytics.start()

  router.beforeEach((to) => {
    if (!canTrack(to)) analytics.setConsentMode('denied')
  })

  router.afterEach((to, _from, failure) => {
    const current = failure ? router.currentRoute.value : to
    // The SDK reads the browser URL, including during back/forward navigation.
    const { pathname, search, hash } = window.location
    const allowed = canTrack(current) && current.fullPath === pathname + search + hash
    analytics.setConsentMode(allowed ? 'anonymous' : 'denied')
  })
}
