import * as allure from 'allure-js-commons'
import {beforeAll, describe, expect, it} from 'vitest'
import {E2E_FIXTURE_PASSWORD, E2E_ORG_USER_EMAIL, E2E_ORGANIZATION_ID, managementServer, restBase} from '../TestHelpers.js'

/**
 * An organization the V3 fixtures give a member under the e2e organization user's email that holds no password, whose
 * login takes passwords.
 */
const SECOND_ORGANIZATION_ID = 'e2e-second'
/** An organization the V3 fixtures give a login that takes no password. */
const SSO_ONLY_ORGANIZATION_ID = 'e2e-sso-only'
/** The Okta configuration the V3 fixtures give an organization the e2e organization user does not belong to. */
const OTHER_ORGANIZATION_CONFIGURATION_ID = 'e2e-other-okta'

/**
 * The organization login's REST surface on the management server: a login addressed to an organization by its
 * id, which signs in a user of that organization alone, the configurations each login offers, and the landing
 * page's find-my-organizations email and organization choices.
 */
describe('Organization login routes', () => {

    let baseUrl: string

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('OrganizationLogin')
        baseUrl = restBase(managementServer())
    }, 300000)

    function post(path: string, body: unknown, cookie?: string): Promise<Response> {
        const headers: Record<string, string> = {'Content-Type': 'application/json'}
        if (cookie) headers.Cookie = cookie
        return fetch(`${baseUrl}${path}`, {method: 'POST', headers, body: JSON.stringify(body)})
    }

    function get(path: string, cookie?: string): Promise<Response> {
        return fetch(`${baseUrl}${path}`, cookie ? {headers: {Cookie: cookie}} : undefined)
    }

    function login(organizationId: string, email: string = E2E_ORG_USER_EMAIL, password: string = E2E_FIXTURE_PASSWORD): Promise<Response> {
        return post(`/api/auth/org/login/${organizationId}`, {email, password})
    }

    // the gateway's session cookie, whose name carries the server's plane
    function sessionCookie(res: Response): string {
        const session = res.headers.getSetCookie().find(c => c.startsWith('__Host-kinotic-') && c.includes('-session='))
        expect(session, 'the response must set the session cookie').toBeDefined()
        return session!.split(';')[0]
    }

    it('signs a member in at the organization and establishes a session', async () => {
        const res = await login(E2E_ORGANIZATION_ID)
        expect(res.status).toBe(204)
        const me = await get('/api/auth/me', sessionCookie(res))
        expect(me.status).toBe(204)
    })

    it('refuses the member at an organization it does not belong to, with the generic 401', async () => {
        expect((await login('nobody')).status).toBe(401)
        // the system's own id names no organization
        expect((await login('system')).status).toBe(401)
        expect((await login(E2E_ORGANIZATION_ID, E2E_ORG_USER_EMAIL, 'wrong-password')).status).toBe(401)
    })

    it('lists the providers an organization\'s login offers, its email and password and the system\'s GitHub registration among them', async () => {
        const res = await get(`/api/auth/org/login/${E2E_ORGANIZATION_ID}/providers`)
        expect(res.status).toBe(200)
        const providers = await res.json() as {id: string, type: string, name: string, provider: string | null}[]
        expect(providers[0]).toEqual({id: E2E_ORGANIZATION_ID, type: 'PASSWORD', name: 'Email and password', provider: null})
        expect(providers.some(p => p.id === 'github-platform' && p.type === 'OIDC' && p.provider === 'github')).toBe(true)

        expect((await get('/api/auth/org/login/nobody/providers')).status).toBe(404)
    })

    it('offers no password at an organization that allows no password sign-in', async () => {
        const res = await get(`/api/auth/org/login/${SSO_ONLY_ORGANIZATION_ID}/providers`)
        expect(res.status).toBe(200)
        expect((await res.json() as {type: string}[]).some(p => p.type === 'PASSWORD')).toBe(false)
        expect((await login(SSO_ONLY_ORGANIZATION_ID)).status).toBe(401)
    })

    it('signs in with one organization\'s password there alone, though another has a member under the same email', async () => {
        expect((await login(E2E_ORGANIZATION_ID)).status).toBe(204)
        expect((await login(SECOND_ORGANIZATION_ID)).status).toBe(401)
    })

    it('lists the provider keys the landing and sign-up pages offer', async () => {
        const res = await get('/api/auth/org/login/providers')
        expect(res.status).toBe(200)
        expect(await res.json()).toContain('github')
    })

    it('emails a person their organizations for an address alone, behind no challenge in this stack', async () => {
        // the e2e stack configures no Turnstile site, so the page is told to present nothing
        const challenge = await get('/api/auth/org/login/find')
        expect(challenge.status).toBe(200)
        expect(await challenge.json()).toEqual({})

        expect((await post('/api/auth/org/login/find', {})).status).toBe(400)
        // email sending is disabled in the stack, so the send logs the links; the response is the same either way
        expect((await post('/api/auth/org/login/find', {email: E2E_ORG_USER_EMAIL})).status).toBe(204)
        expect((await post('/api/auth/org/login/find', {email: 'nobody@example.test'})).status).toBe(204)
    })

    it('offers no organization choices to a page whose session completed no landing sign-in', async () => {
        const anonymous = await get('/api/auth/org/login/choices')
        expect(anonymous.status).toBe(200)
        expect(await anonymous.json()).toEqual([])
        expect(anonymous.headers.getSetCookie().length, 'a request without a session must create none').toBe(0)

        const signedIn = await get('/api/auth/org/login/choices', sessionCookie(await login(E2E_ORGANIZATION_ID)))
        expect(signedIn.status).toBe(200)
        expect(await signedIn.json()).toEqual([])
    })

    it('connects a sign-in method for the configurations the user\'s organization offers only', async () => {
        expect((await post('/api/auth/org/methods/start/github-platform', {})).status).toBe(401)
        const cookie = sessionCookie(await login(E2E_ORGANIZATION_ID))

        // another organization's configuration is refused, so a link cannot bind its directory's identities to a
        // user that organization never admitted
        expect((await post(`/api/auth/org/methods/start/${OTHER_ORGANIZATION_CONFIGURATION_ID}`, {}, cookie)).status).toBe(403)
        expect((await post('/api/auth/org/methods/start/no-such-configuration', {}, cookie)).status).toBe(400)

        // the system's registration starts the round-trip to its provider
        const started = await fetch(`${baseUrl}/api/auth/org/methods/start/github-platform`,
                                    {method: 'POST', headers: {Cookie: cookie}, redirect: 'manual'})
        expect(started.status).toBe(302)
        expect(started.headers.get('location')).toContain('github.com/login/oauth/authorize')
    })
})
