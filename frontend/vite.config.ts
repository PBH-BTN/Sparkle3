import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ArcoResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [
        ArcoResolver({
          sideEffect: true,
        }),
      ],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': 'http://localhost:7799',
      '/oauth2': 'http://localhost:7799',
      '/login/oauth2': 'http://localhost:7799',
      '/user/avatar': 'http://localhost:7799',
      '/ping': {
        target: 'http://localhost:7799',
        bypass(request) {
          if (request.url?.split('?')[0] === '/ping/queryIp/widget') {
            return '/index.html'
          }
        },
      },
    },
  },
  build: {
    outDir: '../build/generated/frontend',
    emptyOutDir: true,
    sourcemap: false,
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
})
