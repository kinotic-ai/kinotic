import * as allure from 'allure-js-commons'
import {beforeAll, describe, expect, inject, it} from 'vitest'

/**
 * Exercises the browser login REST surface: POST /api/auth/system/login must admit only
 * SYSTEM-scope IamUsers (V3 seeds admin@kinotic.local), establish a session cookie that
 * /api/auth/me accepts, and reject cross-scope or bad credentials with a generic 401.
 */
describe('System login route', () => {

    let baseUrl: string

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('SystemLogin')
        // @ts-ignore
        const host = inject('KINOTIC_HOST') as string
        // @ts-ignore
        const port = inject('KINOTIC_PORT') as number
        baseUrl = `http://${host}:${port}`
    }, 300000)

    async function login(path: string, email: string, password: string): Promise<Response> {
        return fetch(`${baseUrl}${path}`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email, password})
        })
    }

    function sessionCookie(res: Response): string {
        const cookies = res.headers.getSetCookie()
        const session = cookies.find(c => c.startsWith('vertx-web.session='))
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

    it('rejects an ORGANIZATION-scope user with a generic 401', async () => {
        const res = await login('/api/auth/system/login', 'kinotic@kinotic.local', 'kinotic')
        expect(res.status).toBe(401)
    })

    it('rejects a wrong password with a generic 401', async () => {
        const res = await login('/api/auth/system/login', 'admin@kinotic.local', 'wrong-password')
        expect(res.status).toBe(401)
    })

    it('leaves the org login route working for the org user', async () => {
        const res = await login('/api/auth/org/login', 'kinotic@kinotic.local', 'kinotic')
        expect(res.status).toBe(204)
    })
})
