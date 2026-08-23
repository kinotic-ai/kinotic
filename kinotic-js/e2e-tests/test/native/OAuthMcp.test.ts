import {Kinotic, Pageable} from '@kinotic-ai/core'
import {DelegateKind, DelegateService, OAuthApprovalService} from '@kinotic-ai/management-api'
import type {DelegatingParticipantIdentity} from '@kinotic-ai/management-api'
import * as allure from 'allure-js-commons'
import {randomBytes, createHash} from 'node:crypto'
import {WebSocket} from 'ws'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {initKinoticClient, postForm, restBase, shutdownKinoticClient, stompUrl} from '../TestHelpers.js'

const DEVICE_CODE_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:device_code'

/**
 * Attempts the STOMP WebSocket upgrade with the given bearer token. Resolves with the HTTP status
 * the gateway answered the upgrade with, or 'open' when the handshake succeeded — a failed
 * handshake ends the response before the upgrade, so the socket never opens.
 */
function stompHandshake(url: string, token: string): Promise<number | 'open'> {
    return new Promise(resolve => {
        const socket = new WebSocket(url, ['v12.stomp'], {headers: {Authorization: `Bearer ${token}`}})
        socket.on('open', () => {
            socket.close()
            resolve('open')
        })
        socket.on('unexpected-response', (_request, response) => {
            socket.terminate()
            resolve(response.statusCode ?? -1)
        })
        socket.on('error', () => resolve(-1))
    })
}

/**
 * Covers the MCP authorization surface: the 401 discovery challenge, the metadata documents a host
 * reads to find the authorization server, the Client ID Metadata Document rules the authorize
 * endpoint enforces on a client_id, and the device grant the CLI logs in with.
 *
 * The authorization-code happy path is not covered here: a client_id must be an https URL whose
 * host does not resolve to a special-use address, which no host reachable from this suite
 * satisfies. Covering it needs a public HTTPS fixture serving a metadata document, or the Client ID
 * Metadata Document Service that draft-ietf-oauth-client-id-metadata-document Section 4.2
 * recommends authorization servers offer for exactly this reason.
 */
describe('Kinotic JS', () => {

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('OAuthMcp')
        // the org user (kinotic@kinotic.local / kinotic-test) that approves the device grant
        await initKinoticClient()
    }, 120000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    it('challenges an unauthenticated request with OAuth discovery', async () => {
        const response = await fetch(`${restBase()}/mcp`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({jsonrpc: '2.0', id: 1, method: 'tools/list'})
        })
        expect(response.status).toBe(401)
        expect(response.headers.get('WWW-Authenticate'))
            .toContain('resource_metadata="')

        const resourceMetadata = await (await fetch(`${restBase()}/.well-known/oauth-protected-resource/mcp`)).json()
        expect(resourceMetadata.resource).toMatch(/\/mcp$/)
        expect(resourceMetadata.authorization_servers).toHaveLength(1)

        const asMetadata = await (await fetch(`${restBase()}/.well-known/oauth-authorization-server`)).json()
        expect(asMetadata.authorization_endpoint).toMatch(/\/api\/auth\/oauth\/authorize$/)
        expect(asMetadata.token_endpoint).toMatch(/\/api\/auth\/oauth\/token$/)
        expect(asMetadata.code_challenge_methods_supported).toEqual(['S256'])
        // clients identify themselves by metadata document; there is nothing to register with
        expect(asMetadata.client_id_metadata_document_supported).toBe(true)
        expect(asMetadata.registration_endpoint).toBeUndefined()
    })

    it('refuses dynamic client registration', async () => {
        const response = await fetch(`${restBase()}/api/auth/oauth/register`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({client_name: 'OAuth e2e', redirect_uris: ['https://example.com/cb']})
        })
        expect(response.status).toBe(404)
    })

    it('rejects a client_id that is not a usable metadata document URL', async () => {
        const challenge = createHash('sha256').update(randomBytes(48).toString('base64url')).digest('base64url')
        const authorize = (clientId: string) =>
            fetch(`${restBase()}/api/auth/oauth/authorize`
                      + `?client_id=${encodeURIComponent(clientId)}`
                      + `&redirect_uri=${encodeURIComponent('http://localhost:33418/callback')}`
                      + `&response_type=code&code_challenge=${challenge}&code_challenge_method=S256`,
                  {redirect: 'manual'})

        // not a URL at all — the shape dynamic registration used to mint
        expect((await authorize('s6BhdRkqt3')).status).toBe(400)
        // http, so the host is not one the client provably controls
        expect((await authorize('http://example.com/client.json')).status).toBe(400)
        // https but no path component
        expect((await authorize('https://example.com')).status).toBe(400)
        // a private address the gateway must not be steered into fetching
        expect((await authorize('https://192.168.1.1/client.json')).status).toBe(400)
    })

    it('rejects a device grant that does not name the pre-registered CLI client', async () => {
        const anonymous = await fetch(`${restBase()}/api/auth/oauth/device_authorization`, {method: 'POST'})
        expect(anonymous.status).toBe(400)
        expect((await anonymous.json()).error).toBe('invalid_client')

        const unknown = await postForm(`${restBase()}/api/auth/oauth/device_authorization`,
                                       {client_id: 'some-other-client'})
        expect(unknown.status).toBe(400)
    })

    it('logs the CLI in through the device grant', async () => {
        const start = await (await postForm(`${restBase()}/api/auth/oauth/device_authorization`,
                                            {client_id: 'kinotic-cli', device_name: 'e2e-laptop'})).json()
        expect(start.user_code).toBeTruthy()

        // the /device page approval, exactly as DeviceVerification.vue performs it over STOMP
        await new OAuthApprovalService(Kinotic).approveDevice(start.user_code)

        const tokenResponse = await postForm(`${restBase()}/api/auth/oauth/token`,
                                             {grant_type: DEVICE_CODE_GRANT_TYPE, device_code: start.device_code})
        expect(tokenResponse.status).toBe(200)
        expect(tokenResponse.headers.get('Cache-Control')).toBe('no-store')
        const tokens = await tokenResponse.json()

        expect(await stompHandshake(stompUrl(), tokens.access_token)).toBe('open')

        // The device grant's token carries aud=kinotic, but no entry point verifies the claim, so
        // it also calls MCP tools. Restoring the check (see docs/NavidNotes.md) makes this a 401.
        const mcpResponse = await fetch(`${restBase()}/mcp`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json', Authorization: `Bearer ${tokens.access_token}`},
            body: JSON.stringify({jsonrpc: '2.0', id: 1, method: 'tools/list'})
        })
        expect(mcpResponse.status).toBe(200)

        // rotation re-mints a token with the same reach
        const refreshed = await postForm(`${restBase()}/api/auth/oauth/token`,
                                         {grant_type: 'refresh_token', refresh_token: tokens.refresh_token})
        expect(refreshed.status).toBe(200)
        const rotated = await refreshed.json()
        expect(await stompHandshake(stompUrl(), rotated.access_token)).toBe('open')

        // the grant created a CLI delegate owned by the approving user, holding one labeled session
        const delegateService = new DelegateService(Kinotic)
        const delegates = await delegateService.findMyDelegates(Pageable.create(0, 25))
        const cliDelegate: DelegatingParticipantIdentity | undefined =
            delegates.content?.find(d => d.delegateKind === DelegateKind.CLI)
        expect(cliDelegate).toBeDefined()
        expect(cliDelegate!.enabled).toBe(true)
        const sessions = await delegateService.findSessions(cliDelegate!.id!)
        expect(sessions.length).toBeGreaterThan(0)
        expect(sessions[0].label).toBe('e2e-laptop')

        // reusing the rotated-out token revokes the family
        const reuse = await postForm(`${restBase()}/api/auth/oauth/token`,
                                     {grant_type: 'refresh_token', refresh_token: tokens.refresh_token})
        expect(reuse.status).toBe(400)
        expect((await reuse.json()).error).toBe('invalid_grant')

        // revoking the delegate rejects its unexpired access token at the next request
        await delegateService.revokeDelegate(cliDelegate!.id!)
        expect(await stompHandshake(stompUrl(), rotated.access_token)).toBe(401)
    }, 60000)

})
