import { resolve } from 'path'
import { defineConfig } from 'vitest/config'
import dts from 'vite-plugin-dts'
import { externalizeDeps } from 'vite-plugin-externalize-deps'


// https://vitejs.dev/guide/build.html#library-mode
export default defineConfig({
    build: {
        lib: {
            entry: resolve(__dirname, 'src/index.ts'),
            name: 'continuum',
            fileName: 'continuum',
            formats: ["es", "cjs"],
        },
        sourcemap: true,
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
        coverage: {
            provider: 'v8',
            reporter: ['text', 'json', 'html'],
        },
        env: {
            DEBUG: 'continuum:*'
        },
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
