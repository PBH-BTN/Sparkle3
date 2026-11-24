import '@/composables/useTheme'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/main.css'
import App from './App.vue'
import { installAnalytics } from './analytics'
import router from './router'
import { installChunkRecovery } from './router/chunkRecovery'
import i18n from './i18n'

const app = createApp(App)

app.use(createPinia())
installChunkRecovery(router)
installAnalytics(router)
app.use(router)
app.use(i18n)
app.mount('#app')
