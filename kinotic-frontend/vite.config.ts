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

const GATEWAY_TARGET = 'http://localhost:58503'

// Public hostname of a dev tunnel (ngrok, Cloudflare, …) fronting this dev server, unset for plain
// local development. Point VITE_KINOTIC_HOST at the same hostname and the SPA, the REST endpoints
// and the STOMP upgrade all share the tunnel's origin, which is what the SameSite=Lax session
// cookie and the OIDC start/callback pair both require.
const tunnelHost = process.env.VITE_DEV_TUNNEL_HOST

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
            // vite rejects a Host header it does not recognise, and the HMR client would otherwise
            // dial the dev server's own port rather than the tunnel's
            allowedHosts: tunnelHost ? [tunnelHost] : [],
            hmr: tunnelHost ? { protocol: 'wss', host: tunnelHost, clientPort: 443 } : undefined,
            // Dormant unless VITE_KINOTIC_HOST names this dev server, in which case helpers.ts
            // emits URLs that land here and these forward them to the gateway.
            proxy: {
                '/api': GATEWAY_TARGET,
                '/mcp': GATEWAY_TARGET,
                '/.well-known': GATEWAY_TARGET,
                '/v1': { target: GATEWAY_TARGET, ws: true }
            },
            headers: {
                'Cache-Control': 'no-store'
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
