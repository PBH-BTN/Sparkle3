import { defineStore } from 'pinia'
import { uiApi } from '@/api'
import type { SessionUser, TrafficSummary } from '@/api/types'

interface SessionState {
  loading: boolean
  loaded: boolean
  authenticated: boolean
  user: SessionUser | null
  traffic: TrafficSummary | null
}

export const useSessionStore = defineStore('session', {
  state: (): SessionState => ({
    loading: false,
    loaded: false,
    authenticated: false,
    user: null,
    traffic: null,
  }),
  actions: {
    async load() {
      if (this.loading) return
      this.loading = true
      try {
        const session = await uiApi.session()
        this.authenticated = session.authenticated
        this.user = session.user
        this.traffic = session.traffic
      } finally {
        this.loading = false
        this.loaded = true
      }
    },
    clear() {
      this.authenticated = false
      this.user = null
      this.traffic = null
    },
  },
})
