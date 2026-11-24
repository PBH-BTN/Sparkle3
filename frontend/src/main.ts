import '@/composables/useTheme'
import { init } from '@faststats/web'
import { errorTracking } from '@faststats/web/error'
import { webVitals } from '@faststats/web/web-vitals'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/main.css'
import App from './App.vue'
import router from './router'
import { installChunkRecovery } from './router/chunkRecovery'
import i18n from './i18n'

init({
  siteKey: 'ae21273607c94220b3ad7a32ff5dc5e5',
  consent: 'anonymous',
  extensions: [errorTracking(), webVitals()],
})

const app = createApp(App)

app.use(createPinia())
installChunkRecovery(router)
app.use(router)
app.use(i18n)
app.mount('#app')
