import { defineConfig } from 'vitest/config'

export default defineConfig({
    test: {
        setupFiles: ["allure-vitest/setup"],
        reporters: [
            "verbose",
            [
                "allure-vitest/reporter",
                {
                    resultsDir: "allure-results",
                    globalLabels: { parentSuite: "Kinotic Spawn" },
                },
            ],
        ],
    },
})
