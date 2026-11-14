import type { RouterScrollBehavior } from 'vue-router'

export const scrollBehavior: RouterScrollBehavior = (to, from) => {
  if (
    to.name === 'ip-query'
    && from.name === 'ip-query'
    && to.path === from.path
    && to.hash === from.hash
    && to.query.scope !== from.query.scope
    && [...new Set([...Object.keys(to.query), ...Object.keys(from.query)])]
      .filter((key) => key !== 'scope')
      .every((key) => JSON.stringify(to.query[key]) === JSON.stringify(from.query[key]))
  ) {
    return false
  }
  return { top: 0 }
}
