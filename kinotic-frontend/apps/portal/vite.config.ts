import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { PrimeVueResolver } from '@primevue/auto-import-resolver'
import path from "path"
import { bundleGuards } from "../../packages/common/vite/bundleGuards"

// https://vite.dev/config/
// Hosts a tunnel may front this app with (`pnpm dev:tunnel`, `pnpm dev:tunnel:build`)
const TUNNEL_HOSTS = ['.ngrok-free.app', '.ngrok-free.dev', '.ngrok.app', '.ngrok.dev', '.ngrok.io']

// One origin for SPA + backend, so a single ngrok tunnel to this server can receive
// GitHub/OIDC callbacks: tunnel mode clears VITE_KINOTIC_HOST, making apiUrl() and
// Kinotic.connect() same-origin, and these routes forward to the local management server. In
// plain `pnpm dev` the app calls localhost:58503 directly and this proxy sits idle.
const BACKEND_PROXY = {
    '/api': { target: 'http://localhost:58503', changeOrigin: true },
    // STOMP WebSocket (ApiGatewayVertcleFactory.STOMP_WEBSOCKET_PATH)
    '/v1': { target: 'http://localhost:58503', changeOrigin: true, ws: true },
    // OAuth 2.1 metadata + MCP endpoint, so MCP hosts can use the tunnel too
    '/.well-known': { target: 'http://localhost:58503', changeOrigin: true },
    '/mcp': { target: 'http://localhost:58503', changeOrigin: true },
}

export default defineConfig(
    {
        plugins: [
            vue(),
            Components({
                resolvers: [
                    PrimeVueResolver()
                ]
            }),
            bundleGuards()
        ],
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./src"),
                "node:module": path.resolve(__dirname, "../../packages/common/src/shims/node-module.ts"),
            }
        },
        server: {
            port: 5173,
            host: true,
            open: false,
            headers: {
                'Cache-Control': 'no-store'
            },
            allowedHosts: TUNNEL_HOSTS,
            proxy: BACKEND_PROXY,
            // Vite answers CORS preflights itself, for localhost origins only, before the proxy
            // sees them; off, every preflight reaches the management server, whose CORS policy decides.
            // The portal is same-origin here and needs none of its own.
            cors: false,
        },
        // `pnpm dev:tunnel:build` serves the production build behind the tunnel on the dev
        // server's port and origin, so the tunnel carries a few compressed bundles instead
        // of every unminified module the dev server would send
        preview: {
            port: 5173,
            host: true,
            allowedHosts: TUNNEL_HOSTS,
            proxy: BACKEND_PROXY,
            cors: false,
        },
        build: {
            sourcemap: true,
            rollupOptions: {
                // Pre-bundled deps (e.g. @vueuse/core) ship /* #__PURE__ */ annotations in
                // positions Rolldown cannot attach to an expression; we never author these
                // annotations ourselves, so silencing the check only quiets third-party noise.
                checks: { invalidAnnotation: false },
                output: {
                    sourcemapExcludeSources: false
                }
            }
        },
        define: {
            __VUE_OPTIONS_API__: true,
            __VUE_PROD_DEVTOOLS__: false
        }
    }
)
