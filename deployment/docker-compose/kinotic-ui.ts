/**
 * Serves the compose stack's UIs, built into this image from kinotic-frontend, the way the sites storage
 * account and its Worker serve them in production: the portal on 9090 and the system console on 9091. A
 * path naming no file answers with the UI's index.html, so the UI's router resolves every route.
 */
import { resolve, sep } from 'node:path'

const UIS = [
    { name: 'portal', root: resolve(import.meta.dir, 'portal'), port: 9090 },
    { name: 'system console', root: resolve(import.meta.dir, 'console'), port: 9091 },
]

for (const ui of UIS) {
    const index = Bun.file(`${ui.root}/index.html`)
    Bun.serve({
        port: ui.port,
        hostname: '0.0.0.0',
        async fetch(request) {
            const path = resolve(ui.root, '.' + new URL(request.url).pathname)
            // A path resolving outside the UI's directory is answered with index.html like any other unknown path
            const file = path.startsWith(ui.root + sep) ? Bun.file(path) : index
            return new Response(await file.exists() ? file : index)
        },
    })
    console.log(`Serving the ${ui.name} from ${ui.root} on port ${ui.port}`)
}
