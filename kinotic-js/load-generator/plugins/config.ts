import path from 'path'
import { Plugin } from 'vite'

export function config(options?: { entry?: string }): Plugin {
    const entry = options?.entry ?? 'src/main.ts'
    return {
        name: 'node-config',
        config() {
            return {
                build: {
                    lib: {
                        entry: path.resolve(entry),
                        formats: ['es'],
                        fileName: (format) => `${path.basename(entry, path.extname(entry))}.mjs`,
                    },
                    rollupOptions: {
                        input: entry,
                        external: []
                    },
                    target: 'esnext'
                },
                resolve: {
                    alias: {
                        '@': path.resolve( 'src')
                    }
                },
            }
        },
        apply: 'build',
    }
}
