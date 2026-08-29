import { afterAll, describe, expect, it } from 'bun:test'
import { cpSync, mkdirSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { execSync, spawnSync } from 'node:child_process'
import { BasicCredentialsResolver, Kinotic, KinoticSingleton } from '@kinotic-ai/core'
import { ensureNodeWebSocket } from '@kinotic-ai/core/node'
import { Workload, WorkloadOrchestrationService, WorkloadStatus } from '@kinotic-ai/system-api'
import { KATA_CLH_RUNTIME } from '@/internal/api/providers/CloudHypervisorProvider'

// The whole deployment path in one run: a project checkout on the node, the workload-runner
// image booting it as a micro VM through the server's orchestrator, and the service it
// publishes answering a call. Nothing here is faked, which is also why nothing here runs
// unattended — it needs a registered node, a reachable server, and seeded org/app records.
//
// Opt in with KINOTIC_WORKLOAD_E2E=1 once the lab is up; see the runbook in
// vmm-r&d/docker-kata-ch-test/NOTES.md. CI has no nested virtualization, so it skips.
const OPTED_IN = process.env.KINOTIC_WORKLOAD_E2E === '1'
const canRun = OPTED_IN
    && process.getuid?.() === 0
    && spawnSync('docker', ['info', '-f', '{{json .Runtimes}}'], { encoding: 'utf-8' })
        .stdout?.includes(KATA_CLH_RUNTIME) === true

/** Node the workload is pinned to — the one the vm-manager under test registered as. */
const NODE_ID = process.env.KINOTIC_NODE_ID ?? 'lab-node-1'
/** Where the node keeps workload mounts; must be the directory the vm-manager reported. */
const DATA_DIR = process.env.KINOTIC_WORKLOAD_DATA_DIR ?? '/var/lib/kinotic/workloads'
/** Address the guest reaches the server on — the node's own bridge gateway. */
const SERVER_FROM_GUEST = process.env.KINOTIC_E2E_GUEST_SERVER_HOST ?? '172.17.0.1'
const SERVER_PORT = process.env.KINOTIC_SERVER_PORT ?? '58503'

// Seeded by the e2e fixture migrations: an organization, an application it owns, and an
// organization-scope user. The application record is what makes its zone routable.
const ORGANIZATION_ID = process.env.KINOTIC_E2E_ORGANIZATION_ID ?? 'kinotic-test'
const APPLICATION_ID = process.env.KINOTIC_E2E_APPLICATION_ID ?? 'atlas-crm'
const ORG_USER = process.env.KINOTIC_E2E_ORG_USER ?? 'kinotic@kinotic.local'
const ORG_PASSWORD = process.env.KINOTIC_E2E_PASSWORD ?? 'kinotic'
/** An application's services live in this zone, which is what makes the address routable. */
const APP_ZONE = `app.${ORGANIZATION_ID}.${APPLICATION_ID}`
const SERVER_HOST = process.env.KINOTIC_SERVER_HOST ?? '127.0.0.1'

const WORKLOAD_ID = `e2e-echo-${Date.now().toString(36)}`
const CHECKOUT = join(DATA_DIR, WORKLOAD_ID)
const BOOT_TIMEOUT_MS = 600_000
// Tearing a micro VM down outlasts bun's default hook timeout, and a cleanup that gives up
// leaves the node's capacity held by a workload nothing will destroy later
const CLEANUP_TIMEOUT_MS = 120_000

describe.skipIf(!canRun)('a project deployed to a node answers calls to its service', () => {

    let orchestrator: KinoticSingleton | null = null

    // Destroying the workload is what returns its share of the node's capacity: a record left
    // behind holds its allocation, and the node refuses the next deployment for lack of room
    afterAll(async () => {
        if (orchestrator !== null) {
            await new WorkloadOrchestrationService(orchestrator).destroyWorkload(WORKLOAD_ID).catch(() => {})
            await orchestrator.disconnect().catch(() => {})
        }
        rmSync(CHECKOUT, { recursive: true, force: true })
    }, CLEANUP_TIMEOUT_MS)

    it('publishes a service from inside the micro VM and answers a call to it', async () => {
        // The sync workload produces this from a git checkout in the real flow; the test
        // stages it directly, so what is under test is the runtime workload and the server
        mkdirSync(DATA_DIR, { recursive: true })
        cpSync(join(import.meta.dir, 'fixtures', 'echo-project'), CHECKOUT, { recursive: true })
        execSync('bun install', { cwd: CHECKOUT, stdio: 'ignore' })

        ensureNodeWebSocket()
        orchestrator = Kinotic
        await orchestrator.connect()

        const workload = new Workload(WORKLOAD_ID, 'kinoticai/workload-runner:latest')
        workload.id = WORKLOAD_ID
        workload.nodeId = NODE_ID
        // The limit covers the guest and the VMM that runs it, so a figure equal to the guest's
        // own memory gets the hypervisor killed before the agent ever answers
        workload.memoryMb = 2048
        workload.diskSizeMb = 2048
        workload.organizationId = ORGANIZATION_ID
        workload.applicationId = APPLICATION_ID
        workload.volumeMounts = [{ hostPath: CHECKOUT, guestPath: '/app', readOnly: true }]
        workload.environment = {
            KINOTIC_SERVER_HOST: SERVER_FROM_GUEST,
            KINOTIC_SERVER_PORT: SERVER_PORT,
            KINOTIC_SERVER_USE_SSL: 'false',
            KINOTIC_CLIENT_ID: ORG_USER,
            KINOTIC_CLIENT_SECRET: ORG_PASSWORD,
            KINOTIC_ORGANIZATION_ID: ORGANIZATION_ID,
            KINOTIC_PROJECT_APPLICATION_ID: APPLICATION_ID,
        }
        // The server is on the node itself here, which a production deployment does not do —
        // the node permits it only because it is running as a development environment
        workload.network.allowedHosts = [`${SERVER_FROM_GUEST}/32`]

        const deployed = await new WorkloadOrchestrationService(orchestrator).deployWorkload(workload)
        expect(deployed.status).toBe(WorkloadStatus.RUNNING)

        // The guest boots a kernel and connects back before it can answer, so the call retries
        const response = await callUntilAnswered(`${APP_ZONE}~e2e.lab.EchoService`)

        expect(response).toBe('echo:hello-from-e2e')
    }, BOOT_TIMEOUT_MS)

    /**
     * Calls the service the deployed project publishes, retrying while the guest is still
     * coming up — until it answers there is no handler at the address, which is the same
     * error a wrong address gives, so the wait has to be bounded by the caller's timeout.
     */
    async function callUntilAnswered(serviceIdentifier: string): Promise<unknown> {
        // Called as the organization user that owns the application, which is who may address
        // its zone — a system-scope caller is refused before it ever reaches the service
        const app = new KinoticSingleton()
        await app.connect({
            server: { host: SERVER_HOST, port: Number(SERVER_PORT), useSSL: false },
            credentials: new BasicCredentialsResolver(ORG_USER, ORG_PASSWORD, ORGANIZATION_ID),
        })
        try {
            const proxy = app.serviceProxy(serviceIdentifier)
            let lastError: unknown = null
            for (let attempt = 0; attempt < 60; attempt++) {
                try {
                    return await proxy.invoke('echo', ['hello-from-e2e'])
                } catch (error) {
                    lastError = error
                    execSync('sleep 2')
                }
            }
            throw lastError
        } finally {
            await app.disconnect().catch(() => {})
        }
    }
})
