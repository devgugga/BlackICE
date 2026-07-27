import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // Dev-only cross-port proxy to the Quarkus backend (BFF). changeOrigin
      // lets the Set-Cookie session cookie from Quarkus be accepted by the
      // browser as if it came from the Vite origin, since both are localhost.
      // Full same-origin E2E (no proxy needed) lands in Task 6 via Traefik.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    exclude: ['e2e/**'],
  },
})
