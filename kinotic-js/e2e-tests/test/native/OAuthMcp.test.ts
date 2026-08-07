import {ConnectionInfo, Kinotic, KinoticSingleton, Pageable, SessionKeepAliveMode, createAuthenticatedWebSocketFactory} from '@kinotic-ai/core'
import {DelegateKind, DelegateService, KinoticOsCredentialsAuthProvider, MachineService, OAuthApprovalService} from '@kinotic-ai/os-api'
import type {DelegatingParticipantIdentity} from '@kinotic-ai/os-api'
import * as allure from 'allure-js-commons'
import {randomBytes, createHash} from 'node:crypto'
import {WebSocket} from 'ws'
import {afterAll, beforeAll, describe, expect, inject, it} from 'vitest'
import {initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'

const DEVICE_CODE_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:device_code'

// the machine identity V3__kinotic_test_users seeds for these tests (client_secret: kinotic)
const MACHINE_CLIENT_ID = '00000000-0000-0000-0000-000000000010'
const MACHINE_CLIENT_SECRET = 'kinotic'
// the kinotic-test org USER the same migration seeds — a valid identity + password that must
// nevertheless be refused as machine credentials
const ORG_USER_ID = '00000000-0000-0000-0000-000000000002'

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
 * endpoint enforces on a client_id, the device grant the CLI logs in with, and the
 * credential-header connections machine identities authenticate with.
 *
 * The authorization-code happy path is not covered here: a client_id must be an https URL whose
 * host does not resolve to a special-use address, which no host reachable from this suite
 * satisfies. Covering it needs a public HTTPS fixture serving a metadata document, or the Client ID
 * Metadata Document Service that draft-ietf-oauth-client-id-metadata-document Section 4.2
 * recommends authorization servers offer for exactly this reason.
 */
describe('Kinotic JS', () => {

    const base = () => `http://${inject('KINOTIC_HOST')}:${inject('KINOTIC_PORT')}`
    const stompUrl = () => `ws://${inject('KINOTIC_HOST')}:${inject('KINOTIC_PORT')}/v1`

    /**
     * Attempts a full Kinotic client connection authenticated by machine credentials on the
     * upgrade headers — exactly how a machine (vm-manager, an external API caller) connects.
     */
    async function machineConnect(clientId: string, clientSecret: string): Promise<'connected' | 'rejected'> {
        const machineKinotic = new KinoticSingleton()
        const ci = new ConnectionInfo()
        ci.host = inject('KINOTIC_HOST')
        ci.port = inject('KINOTIC_PORT')
        ci.useSSL = false
        ci.maxConnectionAttempts = 1
        ci.sessionKeepAlive = SessionKeepAliveMode.NONE
        ci.webSocketFactory = createAuthenticatedWebSocketFactory(ci, new KinoticOsCredentialsAuthProvider(clientId, clientSecret))
        try {
            await machineKinotic.connect(ci)
            await machineKinotic.disconnect()
            return 'connected'
        } catch {
            return 'rejected'
        }
    }

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
        expect(asMetadata.code_challenge_methods_supported).toEqual(['S256'])
        // clients identify themselves by metadata document; there is nothing to register with
        expect(asMetadata.client_id_metadata_document_supported).toBe(true)
        expect(asMetadata.registration_endpoint).toBeUndefined()
    })

    it('refuses dynamic client registration', async () => {
        const response = await fetch(`${base()}/api/auth/oauth/register`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({client_name: 'OAuth e2e', redirect_uris: ['https://example.com/cb']})
        })
        expect(response.status).toBe(404)
    })

    it('rejects a client_id that is not a usable metadata document URL', async () => {
        const challenge = createHash('sha256').update(randomBytes(48).toString('base64url')).digest('base64url')
        const authorize = (clientId: string) =>
            fetch(`${base()}/api/auth/oauth/authorize`
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
        const anonymous = await fetch(`${base()}/api/auth/oauth/device_authorization`, {method: 'POST'})
        expect(anonymous.status).toBe(400)
        expect((await anonymous.json()).error).toBe('invalid_client')

        const unknown = await fetch(`${base()}/api/auth/oauth/device_authorization`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({client_id: 'some-other-client'})
        })
        expect(unknown.status).toBe(400)
    })

    it('logs the CLI in through the device grant', async () => {
        const start = await (await fetch(`${base()}/api/auth/oauth/device_authorization`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({client_id: 'kinotic-cli', device_name: 'e2e-laptop'})
        })).json()
        expect(start.user_code).toBeTruthy()

        // the /device page approval, exactly as DeviceVerification.vue performs it over STOMP
        await new OAuthApprovalService(Kinotic).approveDevice(start.user_code)

        const tokenResponse = await fetch(`${base()}/api/auth/oauth/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({grant_type: DEVICE_CODE_GRANT_TYPE, device_code: start.device_code})
        })
        expect(tokenResponse.status).toBe(200)
        expect(tokenResponse.headers.get('Cache-Control')).toBe('no-store')
        const tokens = await tokenResponse.json()

        expect(await stompHandshake(stompUrl(), tokens.access_token)).toBe('open')

        // The device grant's token carries aud=kinotic, but no entry point verifies the claim, so
        // it also calls MCP tools. Restoring the check (see docs/NavidNotes.md) makes this a 401.
        const mcpResponse = await fetch(`${base()}/mcp`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json', Authorization: `Bearer ${tokens.access_token}`},
            body: JSON.stringify({jsonrpc: '2.0', id: 1, method: 'tools/list'})
        })
        expect(mcpResponse.status).toBe(200)

        // rotation re-mints a token with the same reach
        const refreshed = await fetch(`${base()}/api/auth/oauth/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({grant_type: 'refresh_token', refresh_token: tokens.refresh_token})
        })
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
        const reuse = await fetch(`${base()}/api/auth/oauth/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({grant_type: 'refresh_token', refresh_token: tokens.refresh_token})
        })
        expect(reuse.status).toBe(400)
        expect((await reuse.json()).error).toBe('invalid_grant')

        // revoking the delegate rejects its unexpired access token at the next request
        await delegateService.revokeDelegate(cliDelegate!.id!)
        expect(await stompHandshake(stompUrl(), rotated.access_token)).toBe(401)
    }, 60000)

    it('authenticates a machine with its credentials on the connection', async () => {
        expect(await machineConnect(MACHINE_CLIENT_ID, MACHINE_CLIENT_SECRET)).toBe('connected')
    })

    it('rejects connections that do not prove a machine identity', async () => {
        // wrong secret
        expect(await machineConnect(MACHINE_CLIENT_ID, 'wrong')).toBe('rejected')
        // unknown client
        expect(await machineConnect('no-such-machine', MACHINE_CLIENT_SECRET)).toBe('rejected')
        // a USER id with its correct password — ids without '@' resolve only to machines
        expect(await machineConnect(ORG_USER_ID, 'kinotic')).toBe('rejected')
        // the OAuth surface no longer speaks client_credentials — machines connect, not mint
        const grant = await fetch(`${base()}/api/auth/oauth/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({grant_type: 'client_credentials',
                client_id: MACHINE_CLIENT_ID, client_secret: MACHINE_CLIENT_SECRET})
        })
        expect(grant.status).toBe(400)
        expect((await grant.json()).error).toBe('unsupported_grant_type')
    })

    it('manages the machine lifecycle through MachineService', async () => {
        // the signed-in org user provisions a machine for one of the org's applications.
        // The application is created through the API: migration-seeded kinotic_application
        // rows are written with a plain _id, so the org-scoped composite-id lookup behind
        // createMachine cannot see them.
        await Kinotic.applications.createApplicationIfNotExist('e2e-machines', 'e2e fixture application for the machine lifecycle test')
        const machineService = new MachineService(Kinotic)
        const created = await machineService.createMachine('e2e Lifecycle Machine', 'e2e-machines')
        const machineId = created.machine.id!
        expect(created.clientSecret).toBeTruthy()

        // the provisioned credentials connect
        expect(await machineConnect(machineId, created.clientSecret)).toBe('connected')

        // and the machine is listed for its application
        const listed = await machineService.findMachines('e2e-machines', Pageable.create(0, 50))
        expect(listed.content?.some(m => m.id === machineId)).toBe(true)

        // rotation kills the old secret and issues a working replacement
        const rotatedSecret = await machineService.rotateSecret(machineId)
        expect(await machineConnect(machineId, created.clientSecret)).toBe('rejected')
        expect(await machineConnect(machineId, rotatedSecret)).toBe('connected')

        // disabling cuts the machine off on its next connection
        await machineService.setMachineEnabled(machineId, false)
        expect(await machineConnect(machineId, rotatedSecret)).toBe('rejected')

        // enabling restores access with the same secret
        await machineService.setMachineEnabled(machineId, true)
        expect(await machineConnect(machineId, rotatedSecret)).toBe('connected')

        // removal is permanent
        await machineService.removeMachine(machineId)
        expect(await machineConnect(machineId, rotatedSecret)).toBe('rejected')
    }, 90000)
})
