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
                "node:module": path.resolve(__dirname, "../../packages/common/src/shims/node-module.ts"),
            }
        },
        server: {
            port: 5173,
            host: true,
            open: false,
            headers: {
                'Cache-Control': 'no-store'
            },
            allowedHosts: ['.ngrok-free.app', '.ngrok-free.dev', '.ngrok.app', '.ngrok.dev', '.ngrok.io'],
            // One origin for SPA + backend, so a single ngrok tunnel to this dev server can
            // receive GitHub/OIDC callbacks: `pnpm dev:tunnel` clears VITE_KINOTIC_HOST, making
            // apiUrl() and Kinotic.connect() same-origin, and these routes forward to the local
            // kinotic-server. In plain `pnpm dev` the app calls localhost:58503 directly and
            // this proxy sits idle.
            proxy: {
                '/api': { target: 'http://localhost:58503', changeOrigin: true },
                // STOMP WebSocket (ApiGatewayVertcleFactory.STOMP_WEBSOCKET_PATH)
                '/v1': { target: 'http://localhost:58503', changeOrigin: true, ws: true },
                // OAuth 2.1 metadata + MCP endpoint, so MCP hosts can use the tunnel too
                '/.well-known': { target: 'http://localhost:58503', changeOrigin: true },
                '/mcp': { target: 'http://localhost:58503', changeOrigin: true },
            }
        },
        build: {
            sourcemap: true,
            rollupOptions: {
                // Pre-bundled deps (e.g. @vueuse/core) ship /* #__PURE__ */ annotations in
                // positions Rolldown cannot attach to an expression; we never author these
                // annotations ourselves, so silencing the check only quiets third-party noise.
                checks: { invalidAnnotation: false },
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
