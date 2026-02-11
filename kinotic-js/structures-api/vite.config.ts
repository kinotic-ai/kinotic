import { resolve } from 'path'
import { defineConfig } from 'vitest/config'
import dts from 'vite-plugin-dts'
import { externalizeDeps } from 'vite-plugin-externalize-deps'

// https://vitejs.dev/guide/build.html#library-mode
export default defineConfig({
    build: {
        lib: {
            entry: resolve(__dirname, 'src/index.ts'),
            name: 'structures-api',
            fileName: 'structures-api',
            formats: ["es", "cjs"],
        },
        outDir: 'dist'
    },
    resolve:{
        alias:{
            '@' : resolve(__dirname, 'src')
        },
    },
    plugins: [
        externalizeDeps(),
        dts()
    ],
    test: {
        globalSetup: './test/setup.ts',
        setupFiles: ["allure-vitest/setup"],
        reporters: [
            "verbose",
            [
                "allure-vitest/reporter",
                {
                    resultsDir: "allure-results",
                },
            ],
        ],
    },
})
