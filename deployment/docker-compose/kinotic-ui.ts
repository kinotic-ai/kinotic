/**
 * Serves the compose stack's UIs, built into this image from kinotic-frontend, by the rules the Cloudflare
 * sites Worker serves them by in production: the portal on 9090 and the system console on 9091. A path that
 * names a file is that file, or 404 when the build holds none; any other path is a route of the UI and is
 * served its index.html. Hashed assets are cached for good and everything else is revalidated.
 */
import { resolve, sep } from 'node:path'
import { cacheControl, resolveFile } from '../cloudflare/sites-worker/worker.js'

const UIS = [
    { name: 'portal', root: resolve(import.meta.dir, 'portal'), port: 9090 },
    { name: 'system console', root: resolve(import.meta.dir, 'console'), port: 9091 },
]

for (const ui of UIS) {
    Bun.serve({
        port: ui.port,
        hostname: '0.0.0.0',
        async fetch(request) {
            const file = resolveFile(new URL(request.url).pathname)
            const path = resolve(ui.root, '.' + file)
            // a path resolving outside the UI's directory is answered as a file the build does not hold
            const found = path.startsWith(ui.root + sep) ? Bun.file(path) : null
            let ret
            if (found !== null && await found.exists()) {
                ret = new Response(found, { headers: { 'cache-control': cacheControl(file) } })
            } else {
                ret = new Response('Not found', { status: 404 })
            }
            return ret
        },
    })
    console.log(`Serving the ${ui.name} from ${ui.root} on port ${ui.port}`)
}
