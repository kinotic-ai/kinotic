/**
 * The test workload of the worker-overhead benchmark, deployed to Cloudflare as a stand-in for a stateless
 * customer microservice. The benchmark's BenchService, playing the gateway's HTTP consumer, POSTs it one invocation,
 * and the Worker reads the test server's fake persistence through the gateway's REST RPC route as many times as the
 * invocation asks.
 *
 * Request: POST /srv/<anything>, Content-Type application/json, body a JSON array holding one {@link Plan}.
 * Response: a {@link WorkerTimings} object.
 */

interface Env {
    /** Base URL the Worker reaches the test server's gateway at, such as https://kinotic-bench.example.com */
    KINOTIC_API_URL: string
}

/** What one invocation asks the Worker to do. */
interface Plan {
    /** How many persistence reads to make */
    calls: number
    /** Whether the reads run concurrently rather than one after another */
    parallel: boolean
    /** The id each read asks for */
    id: string
    /** How long the fake persistence takes to answer each read, in milliseconds */
    delayMs: number
}

/** How long the Worker spent, measured inside it. */
interface WorkerTimings {
    /** Wall time from the request arriving to the response being ready, in milliseconds */
    totalMs: number
    /** Wall time of each read, in milliseconds, in the order they were started */
    readMs: number[]
}

const FIND_BY_ID_PATH = '/api/srv/management-api~org.kinotic.testserver.IBenchEntityService/findById'

export default {
    async fetch(request: Request, env: Env): Promise<Response> {
        const started = Date.now()
        let ret: Response
        const url = new URL(request.url)
        if (request.method !== 'POST' || !url.pathname.startsWith('/srv/')) {
            ret = new Response('Not found', {status: 404})
        } else {
            const [plan] = await request.json() as [Plan]
            const read = () => findById(env, plan)
            let readMs: number[]
            if (plan.parallel) {
                readMs = await Promise.all(Array.from({length: plan.calls}, read))
            } else {
                readMs = []
                for (let i = 0; i < plan.calls; i++) {
                    readMs.push(await read())
                }
            }
            // Date.now() advances only across I/O inside a Worker, which is exactly what these spans measure
            const timings: WorkerTimings = {totalMs: Date.now() - started, readMs}
            ret = Response.json(timings)
        }
        return ret
    }
}

async function findById(env: Env, plan: Plan): Promise<number> {
    const started = Date.now()
    // the test server's security service admits any clientId
    const response = await fetch(env.KINOTIC_API_URL + FIND_BY_ID_PATH, {
        method: 'POST',
        headers: {'content-type': 'application/json', clientId: 'worker-overhead', clientSecret: 'worker-overhead'},
        body: JSON.stringify([plan.id, plan.delayMs])
    })
    const body = await response.text()
    if (!response.ok) {
        throw new Error(`findById answered ${response.status}: ${body}`)
    }
    return Date.now() - started
}
