import {ConnectionInfo, Kinotic, KinoticSingleton, Pageable, SessionKeepAliveMode, createAuthenticatedWebSocketFactory} from '@kinotic-ai/core'
import {KinoticOsCredentialsAuthProvider, MachineService} from '@kinotic-ai/os-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, inject, it} from 'vitest'
import {initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'

// the machine identity V5__e2e_app_fixtures seeds for these tests (clientSecret: kinotic)
const MACHINE_CLIENT_ID = '00000000-0000-0000-0000-000000000010'
const MACHINE_CLIENT_SECRET = 'kinotic'
// a kinotic-test org USER — a valid identity + password that must nevertheless be refused
// as machine credentials
const ORG_USER_ID = '00000000-0000-0000-0000-000000000002'

/**
 * Covers machine identities: credential-header connections through the Kinotic client — the
 * way every machine (vm-manager, an external API caller of an org's application) reaches
 * Kinotic — and the management lifecycle org members drive through MachineService.
 */
describe('Kinotic JS', () => {

    const base = () => `http://${inject('KINOTIC_HOST')}:${inject('KINOTIC_PORT')}`

    /**
     * Attempts a full Kinotic client connection authenticated by machine credentials on the
     * upgrade headers — exactly how a machine connects.
     */
    async function machineConnect(clientId: string, clientSecret: string,
                                  organizationId?: string, applicationId?: string): Promise<'connected' | 'rejected'> {
        const machineKinotic = new KinoticSingleton()
        const ci = new ConnectionInfo()
        ci.host = inject('KINOTIC_HOST')
        ci.port = inject('KINOTIC_PORT')
        ci.useSSL = false
        ci.maxConnectionAttempts = 1
        ci.sessionKeepAlive = SessionKeepAliveMode.NONE
        ci.webSocketFactory = createAuthenticatedWebSocketFactory(ci,
            new KinoticOsCredentialsAuthProvider(clientId, clientSecret, organizationId, applicationId))
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
        await allure.subSuite('MachineIdentity')
        // the org user (kinotic@kinotic.local / kinotic-test) that manages machines
        await initKinoticClient()
    }, 120000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    it('authenticates a machine with its credentials on the connection', async () => {
        expect(await machineConnect(MACHINE_CLIENT_ID, MACHINE_CLIENT_SECRET)).toBe('connected')
        // scope headers, when supplied, must agree with the machine's own scope
        expect(await machineConnect(MACHINE_CLIENT_ID, MACHINE_CLIENT_SECRET, 'kinotic-test', 'e2e-mcp')).toBe('connected')
    })

    it('rejects connections that do not prove a machine identity', async () => {
        // wrong secret
        expect(await machineConnect(MACHINE_CLIENT_ID, 'wrong')).toBe('rejected')
        // unknown client
        expect(await machineConnect('no-such-machine', MACHINE_CLIENT_SECRET)).toBe('rejected')
        // a USER id with its correct password — ids without '@' resolve only to machines
        expect(await machineConnect(ORG_USER_ID, 'kinotic')).toBe('rejected')
        // valid credentials but scope headers that contradict the machine's own scope
        expect(await machineConnect(MACHINE_CLIENT_ID, MACHINE_CLIENT_SECRET, 'kinotic-test', 'e2e-datastream')).toBe('rejected')
        expect(await machineConnect(MACHINE_CLIENT_ID, MACHINE_CLIENT_SECRET, 'some-other-org')).toBe('rejected')
        // the OAuth surface does not speak client_credentials — machines connect, not mint
        const grant = await fetch(`${base()}/api/auth/oauth/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({grant_type: 'client_credentials',
                client_id: MACHINE_CLIENT_ID, client_secret: MACHINE_CLIENT_SECRET})
        })
        expect(grant.status).toBe(400)
        expect((await grant.json()).error).toBe('unsupported_grant_type')
        // each rejected connect costs a jitter delay + reconnect cycle before the client
        // gives up (maxConnectionAttempts is only checked on the NEXT attempt), so five
        // rejections need far more than the default 5s test timeout
    }, 120000)

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

        // the provisioned credentials connect, with and without declared scope
        expect(await machineConnect(machineId, created.clientSecret)).toBe('connected')
        expect(await machineConnect(machineId, created.clientSecret, 'kinotic-test', 'e2e-machines')).toBe('connected')

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
