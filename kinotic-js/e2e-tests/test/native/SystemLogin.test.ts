import * as allure from 'allure-js-commons'
import {beforeAll, describe, expect, it} from 'vitest'
import {E2E_ORGANIZATION_ID, managementServer, restBase, systemServer} from '../TestHelpers.js'

/**
 * Exercises the browser login REST surface: POST /api/auth/system/login must admit only
 * system users (V2 seeds admin@kinotic.local) while the system console's login takes a password, which V1
 * seeds enabled, establish a session cookie that
 * /api/auth/me accepts, and reject cross-scope or bad credentials with a generic 401.
 */
describe('System login route', () => {

    let baseUrl: string

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('SystemLogin')
        baseUrl = restBase(systemServer())
    }, 300000)

    async function login(path: string, email: string, password: string, server: string = baseUrl): Promise<Response> {
        return fetch(`${server}${path}`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email, password})
        })
    }

    function sessionCookie(res: Response): string {
        const cookies = res.headers.getSetCookie()
        const session = cookies.find(c => c.startsWith('__Host-kinotic-system-session='))
        expect(session, 'login response must set the session cookie').toBeDefined()
        return session!.split(';')[0]
    }

    it('logs in the seeded system admin and establishes a working session', async () => {
        const res = await login('/api/auth/system/login', 'admin@kinotic.local', 'kinotic')
        expect(res.status).toBe(204)

        const me = await fetch(`${baseUrl}/api/auth/me`, {
            headers: {Cookie: sessionCookie(res)}
        })
        expect(me.status).toBe(204)
    })

    it('offers an email and a password with nothing configured', async () => {
        const res = await fetch(`${baseUrl}/api/auth/system/login/providers`)
        expect(res.status).toBe(200)
        const providers = await res.json() as {id: string, type: string}[]
        expect(providers.some(p => p.type === 'PASSWORD')).toBe(true)
    })

    it('rejects an ORGANIZATION-scope user with a generic 401', async () => {
        const res = await login('/api/auth/system/login', 'kinotic@kinotic.local', 'kinotic')
        expect(res.status).toBe(401)
    })

    it('rejects a wrong password with a generic 401', async () => {
        const res = await login('/api/auth/system/login', 'admin@kinotic.local', 'wrong-password')
        expect(res.status).toBe(401)
    })

    it('leaves the org login route working for the org user', async () => {
        const res = await login(`/api/auth/org/login/${E2E_ORGANIZATION_ID}`, 'kinotic@kinotic.local', 'kinotic', restBase(managementServer()))
        expect(res.status).toBe(204)
    })

    it('refuses the system admin at the management server\'s login', async () => {
        const res = await login(`/api/auth/org/login/${E2E_ORGANIZATION_ID}`, 'admin@kinotic.local', 'kinotic', restBase(managementServer()))
        expect(res.status).toBe(401)
    })

    it('mounts the system login on the system server only', async () => {
        const res = await login('/api/auth/system/login', 'admin@kinotic.local', 'kinotic', restBase(managementServer()))
        expect(res.status).toBe(404)
    })
})
