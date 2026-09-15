import {BasicCredentialsResolver, type ConnectOptions, Kinotic, KinoticSingleton, RpcError} from '@kinotic-ai/core'
import {ensureNodeWebSocket} from '@kinotic-ai/core/node'
import {execFile} from 'node:child_process'
import {promisify} from 'node:util'
import {firstValueFrom, lastValueFrom, take, toArray} from 'rxjs'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {
    buildConnectOptions,
    E2E_FIXTURE_PASSWORD,
    E2E_ORG_USER_EMAIL,
    E2E_ORGANIZATION_ID,
    kinoticHost,
    kinoticPort,
    kinoticPort2
} from '../TestHelpers.js'
import {ProbeService, type ProbeEvent} from './ProbeService.js'

ensureNodeWebSocket()

const execFileAsync = promisify(execFile)

// The host publishes the probe in an app zone of the e2e organization, which its org user may host and call
const ZONE = `app.${E2E_ORGANIZATION_ID}.node-failure`
const PROBE_SERVICE = `${ZONE}~e2e.nodefailure.ProbeService`

/** The compose container name of the second node, the one every test here kills. */
const NODE_2 = 'kinotic-server-2'

/**
 * How long a lost call may take to fail once its node is gone: Ignite's failure detection (10s by default)
 * plus the cluster-membership hop to the gateway that answers the caller.
 */
const FAILURE_DETECTION_MS = 60_000

/** How long a cancel may take to reach the producer, across nodes. */
const CANCEL_PROPAGATION_MS = 15_000

/** How long a restarted node may take to answer its health check. */
const NODE_RESTART_MS = 180_000

/** How long a client may take to reconnect to a restarted node; its backoff between attempts grows to two minutes. */
const CLIENT_RECONNECT_MS = 240_000

/** Every event the hosted probe reported, in order. */
const probeEvents: ProbeEvent[] = []

/**
 * Runs the suite's kill-and-restart scenarios against the two-node cluster the node-failure setup starts:
 * kinotic-server (node 1) and kinotic-server-2 (node 2). The probe host is the global Kinotic client;
 * callers are separate clients so each side of a call can be placed on the node the scenario needs.
 */
describe('Node failure handling for service proxies', () => {
    const callers: KinoticSingleton[] = []

    beforeAll(async () => {
        Kinotic.zonePrefix = ZONE
        new ProbeService(event => probeEvents.push(event))
        await Kinotic.connect(orgUserConnectOptions(kinoticPort2()))
    }, 120_000)

    afterAll(async () => {
        for (const caller of callers) {
            await caller.disconnect(true)
        }
        await Kinotic.disconnect(true)
        Kinotic.zonePrefix = null
    }, 120_000)

    /** Connects a new caller to the node listening on the given port; the suite disconnects it at the end. */
    async function connectCaller(port: number): Promise<KinoticSingleton> {
        const caller = new KinoticSingleton()
        await caller.connect(orgUserConnectOptions(port))
        callers.push(caller)
        return caller
    }

    it('fails a call whose host disconnects before replying', async () => {
        const caller = await connectCaller(kinoticPort())
        const started = eventCount('hang-started')

        const pending = caller.serviceProxy(PROBE_SERVICE).invoke('hang')
        await waitFor(() => eventCount('hang-started') > started, 10_000, 'the probe to receive hang()')

        await Kinotic.disconnect(true)
        try {
            await expectServiceUnavailable(pending, 'the lost call to fail')
        } finally {
            await Kinotic.connect(orgUserConnectOptions(kinoticPort2()))
        }

        await waitForProbe(caller, 'after host reconnect')
    })

    it('cancels the producer when a caller on another node unsubscribes its stream', async () => {
        const caller = await connectCaller(kinoticPort())
        const cancelled = eventCount('ticks-cancelled')

        const values = await firstValueFrom(caller.serviceProxy(PROBE_SERVICE)
                                                  .invokeStream('ticks', [100])
                                                  .pipe(take(3), toArray()))
        expect(values).toEqual([0, 1, 2])

        await waitFor(() => eventCount('ticks-cancelled') > cancelled, CANCEL_PROPAGATION_MS,
                      'the producer to be cancelled')
    })

    it('cancels the producer when the caller of a stream disconnects', async () => {
        const caller = await connectCaller(kinoticPort())
        const cancelled = eventCount('ticks-cancelled')

        const first = await firstValueFrom(caller.serviceProxy(PROBE_SERVICE).invokeStream('ticks', [100]))
        expect(first).toBe(0)

        await caller.disconnect(true)

        await waitFor(() => eventCount('ticks-cancelled') > cancelled, CANCEL_PROPAGATION_MS,
                      'the producer to be cancelled')
    })

    it('fails the pending call and stream of a serving node that is killed, and serves again once it is back', {timeout: 600_000}, async () => {
        const caller = await connectCaller(kinoticPort())
        const hangStarted = eventCount('hang-started')
        const ticksStarted = eventCount('ticks-started')

        const pendingCall = caller.serviceProxy(PROBE_SERVICE).invoke('hang')
        const pendingStream = lastValueFrom(caller.serviceProxy(PROBE_SERVICE).invokeStream('ticks', [100]))
        await waitFor(() => eventCount('hang-started') > hangStarted && eventCount('ticks-started') > ticksStarted,
                      10_000, 'the probe to receive hang() and ticks()')

        await killNode(NODE_2)

        try {
            await Promise.all([expectServiceUnavailable(pendingCall, 'the lost call to fail'),
                               expectServiceUnavailable(pendingStream, 'the lost stream to fail')])
        } finally {
            await startNode(NODE_2, kinoticPort2())
        }

        // the host reconnects on its own and registers the probe with the restarted node again
        await waitFor(() => Kinotic.eventBus.isConnected(), CLIENT_RECONNECT_MS, 'the host to reconnect')
        await waitForProbe(caller, 'after node restart')
    })

    it('fails the pending call and stream of a caller whose own node is killed, and cancels the producer', async () => {
        // The host moves to node 1 so that killing node 2 takes out the caller's gateway alone
        await Kinotic.disconnect(true)
        await Kinotic.connect(orgUserConnectOptions(kinoticPort()))
        const caller = await connectCaller(kinoticPort2())
        const hangStarted = eventCount('hang-started')
        const ticksStarted = eventCount('ticks-started')
        const cancelled = eventCount('ticks-cancelled')

        const pendingCall = caller.serviceProxy(PROBE_SERVICE).invoke('hang')
        const pendingStream = lastValueFrom(caller.serviceProxy(PROBE_SERVICE).invokeStream('ticks', [100]))
        await waitFor(() => eventCount('hang-started') > hangStarted && eventCount('ticks-started') > ticksStarted,
                      10_000, 'the probe to receive hang() and ticks()')

        await killNode(NODE_2)

        // the caller's client sees its connection drop, so it fails what it had in flight itself
        await Promise.all([
            expect(withTimeout(pendingCall, FAILURE_DETECTION_MS, 'the lost call to fail'))
                .rejects.toThrow('Connection lost'),
            expect(withTimeout(pendingStream, FAILURE_DETECTION_MS, 'the lost stream to fail'))
                .rejects.toThrow('Connection lost')
        ])

        // the surviving node learns the requester's node is gone and cancels the stream it was relaying
        await waitFor(() => eventCount('ticks-cancelled') > cancelled, FAILURE_DETECTION_MS,
                      'the producer to be cancelled')
    })
})

/** Connects as the e2e organization's user to the node listening on the given port. */
function orgUserConnectOptions(port: number): ConnectOptions {
    return buildConnectOptions(
        new BasicCredentialsResolver(E2E_ORG_USER_EMAIL, E2E_FIXTURE_PASSWORD, E2E_ORGANIZATION_ID),
        {host: kinoticHost(), port, useSSL: false})
}

/** Asserts the pending call or stream fails with the platform's RpcServiceUnavailableException within the failure-detection budget. */
async function expectServiceUnavailable(pending: Promise<unknown>, description: string): Promise<void> {
    const error = await withTimeout(pending, FAILURE_DETECTION_MS, description)
        .then(value => new Error(`Expected ${description} but it produced ${JSON.stringify(value)}`),
              e => e)
    expect(error).toBeInstanceOf(RpcError)
    expect((error as RpcError).exceptionName).toBe('RpcServiceUnavailableException')
}

/**
 * Waits until the probe answers the caller's echo again: a host that has just reconnected registers the
 * probe with its node a moment after its connection is up, and a call in that window has no handler yet.
 */
async function waitForProbe(caller: KinoticSingleton, value: string): Promise<void> {
    let lastError: unknown = null
    await waitFor(async () => {
        try {
            expect(await caller.serviceProxy(PROBE_SERVICE).invoke('echo', [value])).toBe(value)
            return true
        } catch (e) {
            lastError = e
            return false
        }
    }, CLIENT_RECONNECT_MS, () => `the probe to answer echo(); last error: ${lastError}`)
}

function eventCount(event: ProbeEvent): number {
    return probeEvents.filter(e => e === event).length
}

/** Kills the node's container outright, the way a crashed or evicted node leaves the cluster. */
async function killNode(containerName: string): Promise<void> {
    console.log(`Killing ${containerName}...`)
    await execFileAsync('docker', ['kill', containerName])
}

/** Starts the node's container again and waits until its gateway answers its health check. */
async function startNode(containerName: string, stompPort: number): Promise<void> {
    console.log(`Starting ${containerName}...`)
    await execFileAsync('docker', ['start', containerName])
    const healthUrl = `http://${kinoticHost()}:${stompPort}/health`
    await waitFor(async () => {
        try {
            const response = await fetch(healthUrl)
            return response.ok
        } catch {
            return false
        }
    }, NODE_RESTART_MS, `${containerName} to answer ${healthUrl}`)
    console.log(`${containerName} is up.`)
}

/** Polls the condition until it holds, failing with the description when the timeout passes first. */
async function waitFor(condition: () => boolean | Promise<boolean>,
                       timeoutMs: number,
                       description: string | (() => string)): Promise<void> {
    const deadline = Date.now() + timeoutMs
    while (!(await condition())) {
        if (Date.now() > deadline) {
            throw new Error(`Timed out after ${timeoutMs}ms waiting for ${typeof description === 'string' ? description : description()}`)
        }
        await new Promise(resolve => setTimeout(resolve, 250))
    }
}

/** Rejects with a descriptive error when the promise is still pending after the timeout. */
function withTimeout<T>(promise: Promise<T>, timeoutMs: number, description: string): Promise<T> {
    let timer: ReturnType<typeof setTimeout>
    const timedOut = new Promise<never>((_, reject) => {
        timer = setTimeout(() => reject(new Error(`Timed out after ${timeoutMs}ms waiting for ${description}`)), timeoutMs)
    })
    return Promise.race([promise, timedOut]).finally(() => clearTimeout(timer))
}
