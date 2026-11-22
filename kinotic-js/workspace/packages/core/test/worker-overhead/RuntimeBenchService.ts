import {type IKinotic, Publish} from '../../src'

/** What one benchmarked call asks for: how many persistence reads, how they run, and how long each takes. */
export interface ReadPlan {
    calls: number
    parallel: boolean
    id: string
    delayMs: number
}

/** The fake persistence the test server publishes. */
export const BENCH_ENTITY_SERVICE = 'management-api~org.kinotic.testserver.IBenchEntityService'

/**
 * A microservice as it runs today, on a runtime VM: the worker-overhead benchmark hosts it on its own connection to
 * the gateway, and it reads the test server's fake persistence back through the gateway over that connection.
 */
@Publish('kinotic.bench')
export class RuntimeBenchService {

    private readonly kinotic: IKinotic

    /**
     * @param kinotic the client hosting this service, which its reads go through
     */
    constructor(kinotic: IKinotic) {
        this.kinotic = kinotic
    }

    /**
     * Makes the reads the plan asks for.
     * @return the wall time of each read, in milliseconds, in the order they were started
     */
    async run(plan: ReadPlan): Promise<{readMs: number[]}> {
        const read = async () => {
            const started = performance.now()
            await this.kinotic.serviceProxy(BENCH_ENTITY_SERVICE).invoke('findById', [plan.id, plan.delayMs])
            return performance.now() - started
        }
        let readMs: number[]
        if (plan.parallel) {
            readMs = await Promise.all(Array.from({length: plan.calls}, read))
        } else {
            readMs = []
            for (let i = 0; i < plan.calls; i++) {
                readMs.push(await read())
            }
        }
        return {readMs}
    }
}
