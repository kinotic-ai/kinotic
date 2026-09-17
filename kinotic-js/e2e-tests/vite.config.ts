import {resolve} from 'path'
import {defineConfig, type ViteUserConfig} from 'vitest/config'
import vue from '@vitejs/plugin-vue'

/**
 * The config every e2e suite shares: the Allure reporter and the vitest globals, with the suite's own
 * settings (its globalSetup, which files it runs) layered on top.
 * @param test the suite's own vitest settings
 */
export function e2eConfig(test: NonNullable<ViteUserConfig['test']>): ViteUserConfig {
    return {
        plugins: [vue()],
        resolve: {
            alias: {
                '@': resolve(__dirname, 'src')
            },
        },
        test: {
            globals: true,
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
            ...test
        }
    }
}

// https://vite.dev/config/
export default defineConfig(e2eConfig({
    globalSetup: './test/setup.ts',
    include: ['test/native/**/*.test.ts'],
}))
