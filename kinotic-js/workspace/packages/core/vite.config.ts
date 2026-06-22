import { resolve } from 'path'
import { defineConfig } from 'vitest/config'

export default defineConfig({
    resolve:{
        alias:{
            '@' : resolve(__dirname, 'src')
        },
    },
    test: {
        coverage: {
            provider: 'v8',
            reporter: ['text', 'json', 'html'],
        },
        env: {
            DEBUG: 'kinotic:*'
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
