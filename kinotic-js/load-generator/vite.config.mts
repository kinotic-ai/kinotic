import { defineConfig } from 'vite'
import {externals} from './plugins/externals'
import {shims} from './plugins/shims'
import {config} from './plugins/config'
import tsconfigPaths from 'vite-tsconfig-paths'

export default defineConfig({
  // basically what is explained here https://dev.to/rxliuli/developing-and-building-nodejs-applications-with-vite-311n
  plugins: [shims(), externals(), config(), tsconfigPaths()],
  build: {
    // Don't clean the dist directory before building
    emptyOutDir: false
  },
  optimizeDeps: {
    include: [
      '@opentelemetry/resources',
      '@opentelemetry/api',
      '@opentelemetry/sdk-node',
      '@opentelemetry/sdk-trace-node',
      '@opentelemetry/semantic-conventions'
    ],
    esbuildOptions: {
      target: 'node18'
    }
  },
  ssr: {
    noExternal: [
      '@opentelemetry/resources',
      '@opentelemetry/api',
      '@opentelemetry/sdk-node',
      '@opentelemetry/sdk-trace-node',
      '@opentelemetry/semantic-conventions'
    ]
  }
})
