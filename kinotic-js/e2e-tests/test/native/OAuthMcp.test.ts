import {Client} from '@modelcontextprotocol/sdk/client/index.js'
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import {Kinotic} from '@kinotic-ai/core'
import * as allure from 'allure-js-commons'
import {createHash, randomBytes} from 'node:crypto'
import {afterAll, beforeAll, describe, expect, inject, it} from 'vitest'
import {initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'

const FIND_PROJECTS_BY_REPO = 'os-api.org.kinotic.os.api.services.ProjectService.findByRepoFullName'
const REDIRECT_URI = 'http://localhost:33418/callback'

/**
 * Drives the full MCP authorization flow the way a Claude host does: discovery from the 401
 * challenge, dynamic client registration, the PKCE authorization-code flow (with the consent
 * step approved over STOMP as the signed-in org user), the token exchange, and finally a
 * bearer-only MCP connection that must land in the approving user's organization scope.
 */
describe('Kinotic JS', () => {

    const base = () => `http://${inject('KINOTIC_HOST')}:${inject('KINOTIC_PORT')}`

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('OAuthMcp')
        // the org user (kinotic@kinotic.local / kinotic-test) that will approve the consent step
        await initKinoticClient()
    }, 120000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    it('challenges an unauthenticated request with OAuth discovery', async () => {
        const response = await fetch(`${base()}/mcp`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({jsonrpc: '2.0', id: 1, method: 'tools/list'})
        })
        expect(response.status).toBe(401)
        expect(response.headers.get('WWW-Authenticate'))
            .toContain('resource_metadata="')

        const resourceMetadata = await (await fetch(`${base()}/.well-known/oauth-protected-resource/mcp`)).json()
        expect(resourceMetadata.resource).toMatch(/\/mcp$/)
        expect(resourceMetadata.authorization_servers).toHaveLength(1)

        const asMetadata = await (await fetch(`${base()}/.well-known/oauth-authorization-server`)).json()
        expect(asMetadata.authorization_endpoint).toMatch(/\/api\/auth\/oauth\/authorize$/)
        expect(asMetadata.token_endpoint).toMatch(/\/api\/auth\/oauth\/token$/)
        expect(asMetadata.registration_endpoint).toMatch(/\/api\/auth\/oauth\/register$/)
        expect(asMetadata.code_challenge_methods_supported).toEqual(['S256'])
    })

    it('completes the PKCE authorization-code flow and calls MCP bearer-only', async () => {
        // dynamic client registration (RFC 7591)
        const registration = await (await fetch(`${base()}/api/auth/oauth/register`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({client_name: 'OAuth e2e', redirect_uris: [REDIRECT_URI]})
        })).json()
        expect(registration.client_id).toBeTruthy()
        expect(registration.token_endpoint_auth_method).toBe('none')

        // authorize: the gateway stores the request and sends the browser to the SPA consent page
        const verifier = randomBytes(48).toString('base64url')
        const challenge = createHash('sha256').update(verifier).digest('base64url')
        const state = randomBytes(16).toString('base64url')
        const authorizeUrl = `${base()}/api/auth/oauth/authorize`
            + `?client_id=${encodeURIComponent(registration.client_id)}`
            + `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}`
            + `&response_type=code&code_challenge=${challenge}&code_challenge_method=S256`
            + `&state=${state}&resource=${encodeURIComponent(`${base()}/mcp`)}`
        const authorizeResponse = await fetch(authorizeUrl, {redirect: 'manual'})
        expect(authorizeResponse.status).toBe(302)
        const consentUrl = new URL(authorizeResponse.headers.get('Location')!)
        const requestId = consentUrl.searchParams.get('request_id')!
        expect(consentUrl.pathname).toBe('/oauth/consent')
        expect(requestId).toBeTruthy()

        // the consent step, exactly as the SPA page performs it over STOMP
        const oauthApproval = (Kinotic as any).oauthApproval
        const pending = await oauthApproval.describe(requestId)
        expect(pending.clientName).toBe('OAuth e2e')
        const redirect = new URL(await oauthApproval.approve(requestId))
        expect(redirect.origin + redirect.pathname).toBe(REDIRECT_URI)
        expect(redirect.searchParams.get('state')).toBe(state)
        const code = redirect.searchParams.get('code')!
        expect(code).toBeTruthy()

        // token exchange (form-encoded per RFC 6749)
        const tokenResponse = await fetch(`${base()}/api/auth/oauth/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                grant_type: 'authorization_code',
                code,
                client_id: registration.client_id,
                redirect_uri: REDIRECT_URI,
                code_verifier: verifier
            })
        })
        expect(tokenResponse.status).toBe(200)
        const tokens = await tokenResponse.json()
        expect(tokens.token_type).toBe('Bearer')
        expect(tokens.refresh_token).toBeTruthy()

        // a code is single-use: replaying it must fail
        const replay = await fetch(`${base()}/api/auth/oauth/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                grant_type: 'authorization_code',
                code,
                client_id: registration.client_id,
                redirect_uri: REDIRECT_URI,
                code_verifier: verifier
            })
        })
        expect(replay.status).toBe(400)

        // bearer-only MCP: no scope headers — the token's own claims decide the scope, so the
        // approving org user sees os-api tools
        const mcpClient = new Client({name: 'kinotic-oauth-e2e', version: '1.0.0'})
        await mcpClient.connect(new StreamableHTTPClientTransport(new URL(`${base()}/mcp`),
                {requestInit: {headers: {Authorization: `Bearer ${tokens.access_token}`}}}))
        try {
            const toolNames = (await mcpClient.listTools()).tools.map(tool => tool.name)
            expect(toolNames).toContain(FIND_PROJECTS_BY_REPO)
        } finally {
            await mcpClient.close()
        }

        // refresh rotation: the new pair works, and reusing the rotated-out token is rejected
        const refreshResponse = await fetch(`${base()}/api/auth/oauth/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({grant_type: 'refresh_token', refresh_token: tokens.refresh_token})
        })
        expect(refreshResponse.status).toBe(200)
        expect((await refreshResponse.json()).access_token).toBeTruthy()

        const reuse = await fetch(`${base()}/api/auth/oauth/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({grant_type: 'refresh_token', refresh_token: tokens.refresh_token})
        })
        expect(reuse.status).toBe(400)
        expect((await reuse.json()).error).toBe('invalid_grant')
    }, 60000)
})
