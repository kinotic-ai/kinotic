import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { PrimeVueResolver } from '@primevue/auto-import-resolver'
import path from "path"
import ts from "typescript"

// Lowers TC39 stage-3 decorators in src/domain entities before Vite's oxc transform,
// which cannot lower decorator syntax. The decorators must stay in the source because
// the kinotic-cli reads them from the AST to generate entity definitions.
function lowerDomainDecorators(): Plugin {
    const domainDir = path.resolve(__dirname, 'src/domain') + path.sep
    return {
        name: 'lower-domain-decorators',
        enforce: 'pre',
        transform(code, id) {
            if (!id.startsWith(domainDir) || !id.endsWith('.ts') || !code.includes('@')) {
                return
            }
            const result = ts.transpileModule(code, {
                fileName: id,
                compilerOptions: {
                    target: ts.ScriptTarget.ES2022,
                    module: ts.ModuleKind.ESNext,
                    useDefineForClassFields: true,
                    sourceMap: true,
                },
            })
            return { code: result.outputText, map: result.sourceMapText }
        },
    }
}

// https://vite.dev/config/
export default defineConfig(
    {
        plugins: [
            lowerDomainDecorators(),
            vue(),
            Components({
                resolvers: [
                    PrimeVueResolver()
                ]
            })
        ],
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./src"),
                "node:module": path.resolve(__dirname, "src/shims/node-module.ts"),
            }
        },
        server: {
            port: 5173,
            host: true,
            open: false,
            headers: {
                'Cache-Control': 'no-store'
            }
        },
        build: {
            sourcemap: true,
            rollupOptions: {
                output: {
                    sourcemapExcludeSources: false
                }
            }
        },
        define: {
            __VUE_OPTIONS_API__: true,
            __VUE_PROD_DEVTOOLS__: false
        }
    }
)
