import { afterEach, describe, expect, test } from 'bun:test'
import worker, { cacheControl, cacheTtl, isDeactivated, platformUis, resolveFile, resolveHost } from './worker.js'

const ZONE = 'kinotic.io'

describe('resolveHost', () => {
    test('a site names its organization and the directory it is served from', () => {
        expect(resolveHost('orders--web.acme.kinotic.io', ZONE)).toEqual({
            organizationId: 'acme', site: 'orders--web.acme.kinotic.io', tenantId: null,
        })
    })

    test('a site addressed to a tenant is served from the application\'s directory', () => {
        expect(resolveHost('globex--orders--web.acme.kinotic.io', ZONE)).toEqual({
            organizationId: 'acme', site: 'orders--web.acme.kinotic.io', tenantId: 'globex',
        })
    })

    test('an organization\'s own host names no site', () => {
        expect(resolveHost('acme.kinotic.io', ZONE)).toEqual({ organizationId: 'acme', site: null, tenantId: null })
    })

    test('the sites domain may be a subdomain of the zone', () => {
        expect(resolveHost('orders--web.acme.dev-nm.kinotic.io', 'dev-nm.kinotic.io')).toEqual({
            organizationId: 'acme', site: 'orders--web.acme.dev-nm.kinotic.io', tenantId: null,
        })
        expect(resolveHost('orders--web.acme.kinotic.io', 'dev-nm.kinotic.io')).toBeNull()
    })

    test('a host that is not a site is refused', () => {
        expect(resolveHost('kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('apps-api.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('acme--orders.apps-api.kinotic.io', ZONE)).toBeNull()
        for (const label of ['www', 'api', 'apps-api', 'system-api', 'portal', 'console', 'mail', 'admin', 'login', 'status',
                             'docs', 'find', 'social', 'sso', 'providers', 'choices', 'system', 'xn']) {
            expect(resolveHost(`${label}.kinotic.io`, ZONE)).toBeNull()
        }
        expect(resolveHost('orders.acme.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('a--b--c--d.acme.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('--web.acme.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('orders--web..kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('x.orders--web.acme.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('orders--web.acme.example.com', ZONE)).toBeNull()
    })
})

describe('platformUis', () => {
    test('the binding names the hosts, or none', () => {
        expect(platformUis('portal.kinotic.ai,console.kinotic.ai')).toEqual(new Set(['portal.kinotic.ai', 'console.kinotic.ai']))
        expect(platformUis(' Dev-Portal.kinotic.ai , dev-console.kinotic.ai ')).toEqual(new Set(['dev-portal.kinotic.ai', 'dev-console.kinotic.ai']))
        expect(platformUis('')).toEqual(new Set())
    })
})

describe('resolveFile', () => {
    test('a path naming a file is served as it is', () => {
        expect(resolveFile('/assets/index-abc123.js')).toBe('/assets/index-abc123.js')
        expect(resolveFile('/version.json')).toBe('/version.json')
        expect(resolveFile('/favicon.ico')).toBe('/favicon.ico')
    })

    test('a route of the single-page application is served its index', () => {
        expect(resolveFile('/')).toBe('/index.html')
        expect(resolveFile('/dashboard/42')).toBe('/index.html')
        expect(resolveFile('/v1.2/orders')).toBe('/index.html')
    })
})

describe('cacheTtl', () => {
    test('hashed assets are kept for a year, everything else briefly', () => {
        expect(cacheTtl('/assets/index-abc123.js')).toBe(31536000)
        expect(cacheTtl('/index.html')).toBe(60)
        expect(cacheTtl('/version.json')).toBe(60)
    })
})

describe('cacheControl', () => {
    test('browsers keep hashed assets for good and revalidate everything else', () => {
        expect(cacheControl('/assets/index-abc123.js')).toBe('public, max-age=31536000, immutable')
        expect(cacheControl('/index.html')).toBe('no-cache')
    })
})

describe('deactivated organizations', () => {
    const realFetch = globalThis.fetch
    const env = {
        SITES_DOMAIN: ZONE,
        SITES_STORAGE_URL: 'https://stsites.blob.core.windows.net',
        SITES_STORAGE_SAS: 'sv=x',
        PORTAL_BASE_URL: 'https://portal.kinotic.ai',
        UI_HOSTS: 'portal.kinotic.ai',
        SYSTEM_API_BASE_URL: 'https://system-api.kinotic.ai',
    }

    afterEach(() => {
        globalThis.fetch = realFetch
    })

    // answers the system server's question with the given standing, and every storage read with an index
    function serve(deactivated, requested) {
        globalThis.fetch = async (url) => {
            requested.push(String(url))
            return String(url).startsWith(env.SYSTEM_API_BASE_URL)
                    ? Response.json({ deactivated })
                    : new Response('<html></html>', { headers: { 'content-type': 'text/html' } })
        }
    }

    test('a deactivated organization\'s site redirects to the portal\'s deactivated page', async () => {
        const requested = []
        serve(true, requested)
        const response = await worker.fetch(new Request('https://orders--web.acme.kinotic.io/orders'), env)
        expect(response.status).toBe(302)
        expect(response.headers.get('location')).toBe('https://portal.kinotic.ai/deactivated/acme')
        expect(requested).toEqual(['https://system-api.kinotic.ai/api/billing/organizations/acme/deactivated'])
    })

    test('an active organization\'s site is served', async () => {
        const requested = []
        serve(false, requested)
        const response = await worker.fetch(new Request('https://orders--web.acme.kinotic.io/orders'), env)
        expect(response.status).toBe(200)
        expect(requested[1]).toStartWith('https://stsites.blob.core.windows.net/sites/orders--web.acme.kinotic.io/index.html?')
    })

    test('a site is served when no system server is bound or it cannot answer', async () => {
        expect(await isDeactivated('acme', '')).toBe(false)
        globalThis.fetch = async () => {
            throw new Error('unreachable')
        }
        expect(await isDeactivated('acme', env.SYSTEM_API_BASE_URL)).toBe(false)
    })

    test('a site is served when the system server does not answer in time', async () => {
        globalThis.fetch = (url, init) => new Promise((resolve, reject) => {
            init.signal.addEventListener('abort', () => reject(init.signal.reason))
        })
        expect(await isDeactivated('acme', env.SYSTEM_API_BASE_URL)).toBe(false)
    }, 5000)
})
