import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
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
})
