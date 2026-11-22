import {writeFileSync} from 'node:fs'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {Kinotic, KinoticSingleton} from '../../src'
import {createConnectOptions} from '../TestHelper'
import {BENCH_ENTITY_SERVICE, type ReadPlan, RuntimeBenchService} from './RuntimeBenchService'

/**
 * Measures what moving a stateless microservice from a runtime VM to a Cloudflare Worker costs its callers, end to
 * end through the test server's gateway. It runs only when WORKER_BENCH is set, against a test server that forwards
 * to a deployed Worker (see README.md in this directory). Every call starts at a client connected to the gateway:
 *
 * - "gateway → persistence": the caller reads the test server's fake persistence itself, the floor every path pays
 * - "runtime": gateway → a microservice hosted on this process's own connection, as a runtime VM hosts it today →
 *   gateway → persistence, over the runtime's STOMP connection
 * - "worker": gateway → the test server's forwarding consumer → the Cloudflare Worker → the gateway's REST RPC route
 *   → persistence
 *
 * Environment:
 * - WORKER_BENCH          set to run the benchmark
 * - PERSISTENCE_DELAY_MS  how long the fake persistence takes per read (default 3, about an Elasticsearch get)
 * - ITERATIONS            measured calls per latency scenario (default 200), after WARMUP unmeasured ones (default 20)
 * - CONCURRENCY           concurrent callers in the throughput scenarios (default 32), each for DURATION_S (default 20)
 * - RESULTS_FILE          where the results are written as JSON (default worker-overhead-results.json)
 */

const ZONE = 'app.test-org.worker-overhead'
const RUNTIME_SERVICE = `${ZONE}~kinotic.bench.RuntimeBenchService`
const WORKER_SERVICE = 'management-api~org.kinotic.testserver.WorkerBench'

const PERSISTENCE_DELAY_MS = Number(process.env.PERSISTENCE_DELAY_MS ?? 3)
const ITERATIONS = Number(process.env.ITERATIONS ?? 200)
const WARMUP = Number(process.env.WARMUP ?? 20)
const CONCURRENCY = Number(process.env.CONCURRENCY ?? 32)
const DURATION_S = Number(process.env.DURATION_S ?? 20)
const RESULTS_FILE = process.env.RESULTS_FILE ?? 'worker-overhead-results.json'

type Path = 'gateway → persistence' | 'runtime' | 'worker'

interface Scenario {
    path: Path
    calls: number
    parallel: boolean
}

interface Stats {
    count: number
    meanMs: number
    p50Ms: number
    p90Ms: number
    p99Ms: number
    maxMs: number
}

interface LatencyResult {
    scenario: string
    caller: Stats
    /** each persistence read, as the runtime or the Worker timed it */
    read?: Stats
    /** worker scenarios: the Worker's own wall time per invocation */
    inWorker?: Stats
}

interface ThroughputResult {
    scenario: string
    concurrency: number
    durationS: number
    callsPerSecond: number
    errors: number
    caller: Stats
}

const LATENCY_SCENARIOS: Scenario[] = [
    {path: 'gateway → persistence', calls: 1, parallel: false},
    ...(['runtime', 'worker'] as Path[]).flatMap(path => [
        {path, calls: 0, parallel: false},
        {path, calls: 1, parallel: false},
        {path, calls: 5, parallel: false},
        {path, calls: 5, parallel: true},
    ])
]

const THROUGHPUT_SCENARIOS: Scenario[] = [
    {path: 'runtime', calls: 1, parallel: false},
    {path: 'worker', calls: 1, parallel: false},
]

function nameOf(scenario: Scenario): string {
    let ret: string
    if (scenario.path === 'gateway → persistence') {
        ret = scenario.path
    } else {
        const reads = scenario.calls === 0 ? 'no reads' : `${scenario.calls} ${scenario.parallel ? 'parallel' : 'sequential'} read${scenario.calls > 1 ? 's' : ''}`
        ret = `${scenario.path}: ${reads}`
    }
    return ret
}

function stats(samples: number[]): Stats {
    const sorted = [...samples].sort((a, b) => a - b)
    const at = (q: number) => sorted[Math.min(sorted.length - 1, Math.floor(q * sorted.length))] ?? 0
    const round = (value: number) => Math.round(value * 100) / 100
    return {
        count: sorted.length,
        meanMs: round(sorted.reduce((sum, v) => sum + v, 0) / Math.max(1, sorted.length)),
        p50Ms: round(at(0.5)),
        p90Ms: round(at(0.9)),
        p99Ms: round(at(0.99)),
        maxMs: round(sorted[sorted.length - 1] ?? 0)
    }
}

describe.runIf(process.env.WORKER_BENCH)('Worker overhead', () => {
    const caller = new KinoticSingleton()
    const latencyResults: LatencyResult[] = []
    const throughputResults: ThroughputResult[] = []

    /** Makes one call down the scenario's path and answers the reads and Worker time it reports. */
    async function call(scenario: Scenario): Promise<{readMs: number[], totalMs?: number}> {
        const plan: ReadPlan = {calls: scenario.calls, parallel: scenario.parallel, id: 'bench-entity', delayMs: PERSISTENCE_DELAY_MS}
        let ret: {readMs: number[], totalMs?: number}
        if (scenario.path === 'gateway → persistence') {
            await caller.serviceProxy(BENCH_ENTITY_SERVICE).invoke('findById', [plan.id, plan.delayMs])
            ret = {readMs: []}
        } else if (scenario.path === 'runtime') {
            ret = await caller.serviceProxy(RUNTIME_SERVICE).invoke('run', [plan])
        } else {
            ret = await caller.serviceProxy(WORKER_SERVICE).invoke('run', [plan])
        }
        return ret
    }

    beforeAll(async () => {
        // This process's global client is the runtime hosting the microservice; the caller is a client of its own
        Kinotic.zonePrefix = ZONE
        new RuntimeBenchService(Kinotic)
        await Kinotic.connect(createConnectOptions())
        await caller.connect(createConnectOptions())
    }, 120_000)

    afterAll(async () => {
        await caller.disconnect()
        await Kinotic.disconnect()
        Kinotic.zonePrefix = null

        writeFileSync(RESULTS_FILE, JSON.stringify({
            persistenceDelayMs: PERSISTENCE_DELAY_MS,
            iterations: ITERATIONS,
            latency: latencyResults,
            throughput: throughputResults
        }, null, 2))
        console.log(`\nLatency in ms as the caller sees it (fake persistence answers each read after ${PERSISTENCE_DELAY_MS} ms)`)
        console.table(latencyResults.map(r => ({
            scenario: r.scenario,
            p50: r.caller.p50Ms,
            p90: r.caller.p90Ms,
            p99: r.caller.p99Ms,
            mean: r.caller.meanMs,
            'read p50': r.read?.p50Ms,
            'in Worker p50': r.inWorker?.p50Ms
        })))
        console.log(`\nThroughput: ${CONCURRENCY} concurrent callers for ${DURATION_S}s`)
        console.table(throughputResults.map(r => ({
            scenario: r.scenario,
            'calls/s': r.callsPerSecond,
            errors: r.errors,
            p50: r.caller.p50Ms,
            p99: r.caller.p99Ms
        })))
        console.log(`Results written to ${RESULTS_FILE}`)
    }, 60_000)

    for (const scenario of LATENCY_SCENARIOS) {
        it(`latency: ${nameOf(scenario)}`, async () => {
            for (let i = 0; i < WARMUP; i++) {
                await call(scenario)
            }
            const callerMs: number[] = []
            const readMs: number[] = []
            const workerMs: number[] = []
            for (let i = 0; i < ITERATIONS; i++) {
                const started = performance.now()
                const result = await call(scenario)
                callerMs.push(performance.now() - started)
                if (scenario.path !== 'gateway → persistence') {
                    expect(result.readMs).toHaveLength(scenario.calls)
                }
                readMs.push(...result.readMs)
                if (result.totalMs !== undefined) {
                    workerMs.push(result.totalMs)
                }
            }
            latencyResults.push({
                scenario: nameOf(scenario),
                caller: stats(callerMs),
                ...(readMs.length > 0 ? {read: stats(readMs)} : {}),
                ...(workerMs.length > 0 ? {inWorker: stats(workerMs)} : {})
            })
        }, 600_000)
    }

    for (const scenario of THROUGHPUT_SCENARIOS) {
        it(`throughput: ${nameOf(scenario)}`, async () => {
            const deadline = performance.now() + DURATION_S * 1000
            const callerMs: number[] = []
            let errors = 0
            const loop = async () => {
                while (performance.now() < deadline) {
                    const started = performance.now()
                    try {
                        await call(scenario)
                        callerMs.push(performance.now() - started)
                    } catch (e) {
                        errors++
                    }
                }
            }
            const started = performance.now()
            await Promise.all(Array.from({length: CONCURRENCY}, loop))
            const elapsedS = (performance.now() - started) / 1000
            throughputResults.push({
                scenario: nameOf(scenario),
                concurrency: CONCURRENCY,
                durationS: Math.round(elapsedS * 10) / 10,
                callsPerSecond: Math.round(callerMs.length / elapsedS),
                errors,
                caller: stats(callerMs)
            })
            expect(callerMs.length).toBeGreaterThan(0)
        }, (DURATION_S + 120) * 1000)
    }
})
