/**
 * The local edge that serves every organization's published sites from Azurite, the way the Cloudflare
 * Worker serves them from the sites storage account: it runs deployment/cloudflare/sites-worker/worker.js
 * itself, with the bindings terraform gives the Worker. On start it ensures the private sites container
 * exists and signs the Worker's read SAS with Azurite's well-known development key, which terraform does
 * for the real account.
 *
 * Environment:
 *   SITES_DOMAIN       the domain every site is under (default sites.localhost)
 *   AZURITE_ENDPOINT   Azurite's blob endpoint, with the account (default http://127.0.0.1:10000/devstoreaccount1)
 *   PORTAL_BASE_URL    where an organization's own host sends the browser (default http://localhost:9090)
 *   PORT               the port the sites are served on (default 8088)
 */
import { createHmac } from 'node:crypto'
import worker from '../cloudflare/sites-worker/worker.js'

const ACCOUNT = 'devstoreaccount1'
const ACCOUNT_KEY = 'Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=='
const CONTAINER = 'sites'
const SAS_VERSION = '2020-12-06'
const EDGE_SAS_LIFETIME_MS = 10 * 365 * 24 * 60 * 60_000

const endpoint = process.env.AZURITE_ENDPOINT ?? `http://127.0.0.1:10000/${ACCOUNT}`
const port = Number(process.env.PORT ?? 8088)

function sign(stringToSign: string): string {
    return createHmac('sha256', Buffer.from(ACCOUNT_KEY, 'base64')).update(stringToSign, 'utf-8').digest('base64')
}

/** Creates the private sites container; one that exists already is left as it is. */
async function ensureContainer(): Promise<void> {
    const url = `${endpoint}/${CONTAINER}?restype=container`
    const date = new Date().toUTCString()
    const canonicalResource = `/${ACCOUNT}${new URL(url).pathname}\nrestype:container`
    const stringToSign = ['PUT', '', '', '', '', '', '', '', '', '', '', '',
                          `x-ms-date:${date}\nx-ms-version:${SAS_VERSION}`, canonicalResource].join('\n')
    const response = await fetch(url, {
        method: 'PUT',
        headers: { 'x-ms-date': date, 'x-ms-version': SAS_VERSION, Authorization: `SharedKey ${ACCOUNT}:${sign(stringToSign)}` },
    })
    if (response.status !== 201 && response.status !== 409) {
        throw new Error(`creating the ${CONTAINER} container failed with ${response.status} ${await response.text()}`)
    }
}

/** A read SAS on the whole sites container, the binding terraform gives the Worker. */
function containerSas(): string {
    const permissions = 'r'
    const expiry = new Date(Date.now() + EDGE_SAS_LIFETIME_MS).toISOString().replace(/\.\d{3}Z$/, 'Z')
    const stringToSign = [permissions, '', expiry, `/blob/${ACCOUNT}/${CONTAINER}`, '', '', 'https,http', SAS_VERSION,
                          'c', '', '', '', '', '', '', ''].join('\n')
    return new URLSearchParams({ sp: permissions, se: expiry, spr: 'https,http', sv: SAS_VERSION, sr: 'c', sig: sign(stringToSign) }).toString()
}

// Azurite starts alongside the edge, so the container is retried until it answers
for (let attempt = 1; ; attempt++) {
    try {
        await ensureContainer()
        break
    } catch (error) {
        if (attempt === 30) {
            throw error
        }
        await Bun.sleep(1000)
    }
}

const env = {
    SITES_DOMAIN: process.env.SITES_DOMAIN ?? 'sites.localhost',
    SITES_STORAGE_URL: endpoint,
    SITES_STORAGE_SAS: containerSas(),
    PORTAL_BASE_URL: process.env.PORTAL_BASE_URL ?? 'http://localhost:9090',
    UI_HOSTS: '',
}
Bun.serve({ port, hostname: '0.0.0.0', fetch: request => worker.fetch(request, env) })
console.log(`Serving the sites under ${env.SITES_DOMAIN} on port ${port} from ${endpoint}/${CONTAINER}`)
