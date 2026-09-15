import {defineConfig} from 'vitest/config'
import {e2eConfig} from './vite.config'

// The node-failure suite kills and restarts cluster nodes, so it runs on its own two-node stack, one
// file at a time, with budgets that cover a node's failure detection and restart.
export default defineConfig(e2eConfig({
    globalSetup: './test/node-failure/setup.ts',
    include: ['test/node-failure/**/*.test.ts'],
    fileParallelism: false,
    testTimeout: 240_000,
    hookTimeout: 600_000,
}))
