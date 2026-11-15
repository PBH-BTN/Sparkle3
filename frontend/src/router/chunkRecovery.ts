import type { Router } from 'vue-router'

const recoveryKey = 'sparkle:route-chunk-recovery'
const chunkLoadError = /^(Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed|Unable to preload CSS for|Loading chunk \S+ failed)\b/i

export function installChunkRecovery(router: Router) {
  let navigating = false
  window.addEventListener('pageshow', (event) => {
    // Back/forward can restore the previous document with its JavaScript state intact.
    if (event.persisted) navigating = false
  })

  router.onError((error, to) => {
    if (navigating) return
    if (!(error instanceof Error) || !chunkLoadError.test(error.message)) {
      console.error(error)
      return
    }

    const target = new URL(window.location.origin + router.resolve(to.fullPath).href)
    try {
      // Persist across the document navigation so a failed retry cannot reload indefinitely.
      if (window.sessionStorage.getItem(recoveryKey) === target.href) {
        console.error(error)
        return
      }
      window.sessionStorage.setItem(recoveryKey, target.href)
    } catch {
      console.error(error)
      return
    }

    navigating = true
    if (target.pathname === window.location.pathname && target.search === window.location.search) {
      // A hash-only change normally stays in the same document.
      if (target.hash !== window.location.hash) {
        window.history.replaceState(window.history.state, '', target.href)
      }
      window.location.reload()
    } else {
      window.location.assign(target.href)
    }
  })

  router.afterEach((_to, _from, failure) => {
    if (failure || navigating) return
    try {
      window.sessionStorage.removeItem(recoveryKey)
    } catch {
      // Storage may be disabled by the browser.
    }
  })
}
