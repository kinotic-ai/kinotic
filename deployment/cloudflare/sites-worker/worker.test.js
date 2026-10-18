import { describe, expect, test } from 'bun:test'
import { cacheTtl, resolveFile, resolveHost } from './worker.js'

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
        expect(resolveHost('www.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('system.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('orders.acme.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('a--b--c--d.acme.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('--web.acme.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('orders--web..kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('x.orders--web.acme.kinotic.io', ZONE)).toBeNull()
        expect(resolveHost('orders--web.acme.example.com', ZONE)).toBeNull()
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
