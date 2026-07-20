import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src'),
        }
    },
    server: {
        // Off the portal's 5173 so both apps can run side by side in dev.
        port: 5174,
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
})
