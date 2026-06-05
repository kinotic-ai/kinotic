import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { PrimeVueResolver } from '@primevue/auto-import-resolver'
import path from "path"


// https://vite.dev/config/
export default defineConfig(
    {
        plugins: [
            vue(),
            Components({
                resolvers: [
                    PrimeVueResolver()
                ]
            })
        ],
        // The class-component auth pages (Login/Signup/VerifyEmail/CompleteOrg) use
        // vue-facing-decorator's @Component, i.e. TS legacy decorators. Vite's dev transform runs
        // esbuild, which auto-discovers the root tsconfig.json — a solution-style file with no
        // compilerOptions — and does NOT follow project references, so it never sees
        // experimentalDecorators (declared only in tsconfig.app.json). Without it esbuild emits
        // broken JS for those decorators ("Invalid or unexpected token"); declare it here so the
        // dev transform matches the type-check config.
        esbuild: {
            tsconfigRaw: {
                compilerOptions: {
                    experimentalDecorators: true,
                },
            },
        },
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./src"),
                "node:module": path.resolve(__dirname, "src/shims/node-module.ts"),
            }
        },
        server: {
            port: 5173,
            host: true,
            open: false,
            headers: {
                'Cache-Control': 'no-store'
            },
            // Proxy backend API to kinotic-server so the dev server can drive auth flows.
            // Override the target with VITE_KINOTIC_API_URL when running against KinD/remote.
            // changeOrigin=false keeps the Host header as localhost:5173 so the session cookie
            // Vert.x SessionHandler sets matches the browser-visible origin (otherwise the
            // OIDC callback can't find the session it stored at /api/login/start time).
            // For OIDC roundtrips, set kinotic.appBaseUrl=http://localhost:5173 on the backend
            // so the redirect_uri matches the Entra-registered "5173" entry.
            proxy: {
                '/api': {
                    target: process.env.VITE_KINOTIC_API_URL || 'http://localhost:58503',
                    changeOrigin: false,
                    secure: false
                }
            }
        },
        build: {
            sourcemap: true,
            rollupOptions: {
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
