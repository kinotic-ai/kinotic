import http from 'node:http'
import {BasicCredentialsResolver, type CredentialsResolver, KinoticSingleton, type ServerInfo} from '@kinotic-ai/core'
import * as allure from 'allure-js-commons'
import {beforeAll, describe, expect, it} from 'vitest'
import {E2E_APP_TENANT,
        E2E_FIXTURE_PASSWORD,
        E2E_ORGANIZATION_ID,
        E2E_ORG_USER_EMAIL,
        E2E_SYSTEM_USER_EMAIL,
        appFixtureEmail,
        appServer,
        buildConnectOptions,
        orgServer,
        restBase,
        systemServer} from '../TestHelpers.js'

// the organization's APP_RUNTIME machine V3__e2e_app_fixtures seeds (clientSecret: kinotic)
const RUNTIME_CLIENT_ID = '00000000-0000-0000-0000-000000000013'
// the application whose seeded user signs in, and another application of the same organization
const APP_ID = 'e2e-mcp'
const OTHER_APP_ID = 'e2e-datastream'

/**
 * The API host of one of the organization's applications, under the domain of the e2e stack's
 * appApiBaseUrl, which the stack leaves at the app server's default, http://localhost:58505.
 */
function apiHost(applicationId: string): string {
    return `${E2E_ORGANIZATION_ID}--${applicationId}.localhost:58505`
}

function orgUser(): CredentialsResolver {
    return new BasicCredentialsResolver(E2E_ORG_USER_EMAIL, E2E_FIXTURE_PASSWORD, E2E_ORGANIZATION_ID)
}

function systemAdmin(): CredentialsResolver {
    return new BasicCredentialsResolver(E2E_SYSTEM_USER_EMAIL, E2E_FIXTURE_PASSWORD)
}

function appUser(): CredentialsResolver {
    return new BasicCredentialsResolver(appFixtureEmail(APP_ID, E2E_APP_TENANT), E2E_FIXTURE_PASSWORD,
                                        E2E_ORGANIZATION_ID, APP_ID)
}

function runtime(): CredentialsResolver {
    return new BasicCredentialsResolver(RUNTIME_CLIENT_ID, E2E_FIXTURE_PASSWORD, E2E_ORGANIZATION_ID)
}

/**
 * Presents the credentials on an upgrade addressed to the host, as a client that resolved the
 * host's name to the server sends it.
 */
function atHost(credentials: CredentialsResolver, host: string): CredentialsResolver {
    return {
        name: `${credentials.name} at ${host}`,
        async resolve(server: ServerInfo) {
            const resolved = await credentials.resolve(server)
            return resolved && {...resolved, authHeaders: {...resolved.authHeaders, Host: host}}
        }
    }
}

/** Opens a Kinotic connection to the server, as any client does, and reports whether the server admitted it. */
async function connect(server: ServerInfo, credentials: CredentialsResolver): Promise<'connected' | 'rejected'> {
    const kinotic = new KinoticSingleton()
    try {
        await kinotic.connect({...buildConnectOptions(credentials, server), maxConnectionAttempts: 1})
        await kinotic.disconnect()
        return 'connected'
    } catch {
        return 'rejected'
    }
}

interface HostResponse {
    status: number
    cookies: string[]
    body: string
}

/** Sends a request to the server addressed to the host, as a request to the host's name arrives there. */
function requestAt(server: ServerInfo, host: string, method: string, path: string,
                   headers: Record<string, string> = {}, body?: string): Promise<HostResponse> {
    return new Promise((resolve, reject) => {
        const request = http.request({host: server.host, port: server.port ?? undefined, method, path,
                                      headers: {...headers, Host: host}}, response => {
            let text = ''
            response.setEncoding('utf8')
            response.on('data', chunk => text += chunk)
            response.on('end', () => resolve({status: response.statusCode!,
                                               cookies: response.headers['set-cookie'] ?? [],
                                               body: text}))
        })
        request.on('error', reject)
        request.end(body)
    })
}

/** The name=value pair of the session cookie the response set. */
function sessionCookie(cookies: string[], name: string): string {
    const cookie = cookies.find(c => c.startsWith(`${name}=`))
    expect(cookie, `the login must set ${name}`).toBeDefined()
    return cookie!.split(';')[0]
}

/**
 * Covers what separates the org, system and app servers: each admits only the participants of its
 * own plane, an application's API host admits only that application, and a session authenticates
 * only at the host that issued it.
 */
describe('Server planes', () => {

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('ServerPlanes')
    })

    it('admits each participant only at the server of its plane', async () => {
        expect(await connect(orgServer(), orgUser())).toBe('connected')
        expect(await connect(systemServer(), orgUser())).toBe('rejected')
        expect(await connect(appServer(), orgUser())).toBe('rejected')

        expect(await connect(systemServer(), systemAdmin())).toBe('connected')
        expect(await connect(orgServer(), systemAdmin())).toBe('rejected')
        expect(await connect(appServer(), systemAdmin())).toBe('rejected')

        expect(await connect(appServer(), appUser())).toBe('connected')
        expect(await connect(orgServer(), appUser())).toBe('rejected')
        expect(await connect(systemServer(), appUser())).toBe('rejected')

        // a runtime publishes into its application's zone, which only the app server hosts
        expect(await connect(appServer(), runtime())).toBe('connected')
        expect(await connect(orgServer(), runtime())).toBe('rejected')
        // every connect pays the client's connection jitter delay before its only attempt
    }, 120000)

    it('admits at an application\'s API host only that application and its organization\'s runtimes', async () => {
        expect(await connect(appServer(), atHost(appUser(), apiHost(APP_ID)))).toBe('connected')
        expect(await connect(appServer(), atHost(appUser(), apiHost(OTHER_APP_ID)))).toBe('rejected')
        expect(await connect(appServer(), atHost(runtime(), apiHost(OTHER_APP_ID)))).toBe('connected')
    }, 60000)

    it('authenticates a session only at the API host that issued it', async () => {
        const login = await requestAt(appServer(), apiHost(APP_ID), 'POST', '/api/auth/app/login',
                                      {'Content-Type': 'application/json'},
                                      JSON.stringify({email: appFixtureEmail(APP_ID, E2E_APP_TENANT),
                                                      password: E2E_FIXTURE_PASSWORD}))
        expect(login.status).toBe(204)
        const session = sessionCookie(login.cookies, '__Host-kinotic-app-session')

        expect((await requestAt(appServer(), apiHost(APP_ID), 'GET', '/api/auth/me', {Cookie: session})).status).toBe(204)
        // replayed at another application's API host, the session is refused and destroyed
        expect((await requestAt(appServer(), apiHost(OTHER_APP_ID), 'GET', '/api/auth/me', {Cookie: session})).status).toBe(401)
        expect((await requestAt(appServer(), apiHost(APP_ID), 'GET', '/api/auth/me', {Cookie: session})).status).toBe(401)
    })

    it('authenticates a session only at the server that issued it', async () => {
        const login = await fetch(`${restBase(orgServer())}/api/auth/org/login`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email: E2E_ORG_USER_EMAIL, password: E2E_FIXTURE_PASSWORD})
        })
        expect(login.status).toBe(204)
        const orgSession = sessionCookie(login.headers.getSetCookie(), '__Host-kinotic-org-session')
        const sessionId = orgSession.substring(orgSession.indexOf('=') + 1)
        expect((await fetch(`${restBase(orgServer())}/api/auth/me`, {headers: {Cookie: orgSession}})).status).toBe(204)

        // the app server reads only its own cookie, and holds only the sessions it issued
        expect((await requestAt(appServer(), apiHost(APP_ID), 'GET', '/api/auth/me',
                                {Cookie: orgSession})).status).toBe(401)
        expect((await requestAt(appServer(), apiHost(APP_ID), 'GET', '/api/auth/me',
                                {Cookie: `__Host-kinotic-app-session=${sessionId}`})).status).toBe(401)
    })

    it('serves each application\'s OAuth metadata at its own API host', async () => {
        const metadata = await requestAt(appServer(), apiHost(APP_ID), 'GET', '/.well-known/oauth-authorization-server')
        expect(metadata.status).toBe(200)
        const issuer = `http://${apiHost(APP_ID)}`
        expect(JSON.parse(metadata.body)).toMatchObject({issuer,
                                                         authorization_endpoint: `${issuer}/api/auth/oauth/authorize`,
                                                         token_endpoint: `${issuer}/api/auth/oauth/token`})

        // the app server's own address is no application's API host
        expect((await requestAt(appServer(), 'localhost:58505', 'GET', '/.well-known/oauth-authorization-server')).status)
            .toBe(404)
    })
})
