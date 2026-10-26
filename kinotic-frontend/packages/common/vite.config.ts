import { readFileSync } from 'fs'
import path from 'path'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import pkg from './package.json' with { type: 'json' }

// Every dependency and peer dependency, with its subpaths, so the consumer's single copy is used.
// management-api augments core's Kinotic singleton, so a second bundled copy of either would give
// the consumer different `Kinotic.*` service types.
const EXTERNAL_PACKAGES = [...Object.keys(pkg.dependencies), ...Object.keys(pkg.peerDependencies)]
const EXTERNAL_RE = new RegExp(`^(${EXTERNAL_PACKAGES.join('|')})(/.*)?$`)

// The build variables helpers.ts reads, kept as they are so the consumer's build supplies them;
// lib mode otherwise replaces every import.meta.env.* with this build's value
const CONSUMER_ENV_DEFINES = Object.fromEntries(['VITE_KINOTIC_HOST', 'VITE_KINOTIC_PORT', 'VITE_KINOTIC_USE_SSL']
    .map(name => [`import.meta.env.${name}`, `import.meta.env.${name}`]))

const ASSET_RE = /\.(png|svg)$/
const LUCIDE_ICON_RE = /\/@lucide\/vue\/dist\/esm\/icons\//

/**
 * Copies the images the components import to `dist/assets` and imports them from there, so the
 * consumer's bundler emits them as files; lib mode would otherwise inline them as data URLs.
 */
function emitImportedAssets(): Plugin {
    return {
        name: 'kinotic-emit-imported-assets',
        enforce: 'pre',
        resolveId(source, importer) {
            let ret: { id: string, external: true } | null = null
            if (importer && ASSET_RE.test(source) && source.startsWith('.')) {
                const fileName = `assets/${path.basename(source)}`
                this.emitFile({ type: 'asset', fileName, source: readFileSync(path.resolve(path.dirname(importer), source)) })
                // Relative to the dist root, where every chunk importing an image lands
                ret = { id: `./${fileName}`, external: true }
            }
            return ret
        },
    }
}

// The published build; the workspace apps consume `src` directly and never run it.
export default defineConfig({
    plugins: [emitImportedAssets(), vue()],
    define: CONSUMER_ENV_DEFINES,
    // Warnings and errors only: the file listing would print a line for each of Lucide's icon chunks
    logLevel: 'warn',
    build: {
        sourcemap: true,
        lib: {
            entry: {
                index: path.resolve(import.meta.dirname, 'src/index.ts'),
                management: path.resolve(import.meta.dirname, 'src/management.ts'),
            },
            formats: ['es'],
            fileName: (_format, entryName) => `${entryName}.js`,
            cssFileName: 'style',
        },
        rollupOptions: {
            external: id => EXTERNAL_RE.test(id),
            output: {
                // lucideIcons.ts loads its icons by file, and Lucide adds and removes icon files between
                // minor versions, so the icons are bundled here as one chunk each rather than resolved
                // against whichever @lucide/vue the consumer installs
                chunkFileNames: chunk => LUCIDE_ICON_RE.test(chunk.facadeModuleId ?? '') ? 'icons/[name]-[hash].js' : '[name]-[hash].js',
            },
        },
    },
})
