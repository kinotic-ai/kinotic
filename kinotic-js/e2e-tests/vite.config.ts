import {resolve} from 'path'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(
    {
        plugins: [vue()],
        resolve:{
            alias:{
                '@' : resolve(__dirname, 'src'),
                // Tests run against the workspace SDK build rather than the published package, so
                // suites can exercise services that have not been published yet (e.g.
                // DelegateService). The dist bundle is self-contained and its @kinotic-ai/core
                // imports resolve to the installed core. Remove once the packages are published
                // and the package.json versions bumped.
                '@kinotic-ai/os-api': resolve(__dirname, '../workspace/packages/os-api/dist/index.js')
            },
        },
        test: {
            globals: true,
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
        }
    }
)
