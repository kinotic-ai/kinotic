import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// In development the page and kinotic-server share one origin: these routes forward to a
// local server, so the session cookie the login sets is first-party and the realtime
// connection resolves the page's own location. A deployment sets VITE_KINOTIC_HOST/PORT/USE_SSL
// at build time instead and the proxy sits idle.
const BACKEND_PROXY = {
    '/api': { target: 'http://localhost:58503', changeOrigin: true },
    // STOMP WebSocket
    '/v1': { target: 'http://localhost:58503', changeOrigin: true, ws: true },
}

export default defineConfig({
    plugins: [vue()],
    server: {
        port: 5180,
        proxy: BACKEND_PROXY,
    },
    preview: {
        port: 5180,
        proxy: BACKEND_PROXY,
    },
})
