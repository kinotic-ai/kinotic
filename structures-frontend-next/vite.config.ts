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
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./src"),
            }
        },
        server: {
            port: 5173,
            host: true,
            open: false,
            headers: {
                'Cache-Control': 'no-store'
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
