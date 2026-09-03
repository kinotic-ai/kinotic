import { randomBytes } from 'node:crypto'
import { mkdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { Util } from '@/internal/api/Util'
import type { OtlpEndpoint } from '@/internal/api/model/OtlpEndpoint'
import type { OtlpSignal } from '@/internal/api/model/OtlpSignal'

/**
 * Host ports the OTLP receivers are issued from, one per workload that elects telemetry. A
 * node runs a handful of micro VMs, so the range is sized for that rather than for density.
 */
const FIRST_PORT = 43180
const PORT_COUNT = 100

/**
 * The OTLP endpoints this node has issued to its workloads. Every workload that elects
 * telemetry gets a host port the node's shipper listens on for it alone, and a bearer token
 * only its guest is given — so the receiver a push arrives on says which workload sent it,
 * and a guest cannot push into another workload's stream. An endpoint holds for the
 * workload's life: the guest's environment is set once, when its VM is created, and a
 * restart boots the same VM, which is why the record outlives the vm-manager process.
 */
export class OtlpEndpointRepository {

    private readonly file: string
    private readonly endpoints: Map<string, OtlpEndpoint>

    constructor(dataDir: string) {
        mkdirSync(dataDir, { recursive: true })
        this.file = join(dataDir, 'otlp-endpoints.json')
        this.endpoints = this.load()
    }

    /** The endpoint issued to the workload, or null when it holds none. */
    find(workloadId: string): OtlpEndpoint | null {
        return this.endpoints.get(workloadId) ?? null
    }

    /**
     * Issues the workload an endpoint bound to the given host address and accepting the given
     * signals, or returns the one it already holds. Fails when every port in the range is taken.
     */
    issue(workloadId: string, listenAddress: string, signals: OtlpSignal[]): OtlpEndpoint {
        let ret = this.endpoints.get(workloadId)
        if (ret === undefined) {
            ret = { listenAddress, port: this.freePort(), token: randomBytes(32).toString('hex'), signals }
            this.endpoints.set(workloadId, ret)
            this.persist()
        }
        return ret
    }

    /** Releases the workload's endpoint, returning its port to the pool. */
    release(workloadId: string): void {
        if (this.endpoints.delete(workloadId)) {
            this.persist()
        }
    }

    /**
     * Releases the endpoints of every workload not in the given set, which a vm-manager that
     * died between removing a workload and releasing its endpoint left holding a port.
     */
    reconcile(activeWorkloadIds: Set<string>): void {
        const stale = [...this.endpoints.keys()].filter(workloadId => !activeWorkloadIds.has(workloadId))
        for (const workloadId of stale) {
            console.log(`Releasing the OTLP endpoint left by workload ${workloadId}`)
            this.endpoints.delete(workloadId)
        }
        if (stale.length > 0) {
            this.persist()
        }
    }

    private freePort(): number {
        const taken = new Set(Array.from(this.endpoints.values(), endpoint => endpoint.port))
        const port = Array.from({ length: PORT_COUNT }, (_, offset) => FIRST_PORT + offset)
                          .find(candidate => !taken.has(candidate))
        if (port === undefined) {
            throw new Error(`Every OTLP receiver port on this node (${FIRST_PORT}-${FIRST_PORT + PORT_COUNT - 1}) is in use`)
        }
        return port
    }

    // A missing record is a node that has issued nothing yet; an unreadable one is not, since
    // treating it as empty would reissue the ports and tokens of guests that still hold the old
    private load(): Map<string, OtlpEndpoint> {
        let ret = new Map<string, OtlpEndpoint>()
        try {
            ret = new Map(Object.entries(JSON.parse(readFileSync(this.file, 'utf-8'))))
        } catch (error) {
            if ((error as NodeJS.ErrnoException).code !== 'ENOENT') {
                throw new Error(`Cannot read the OTLP endpoints issued by this node from ${this.file}`, { cause: error })
            }
        }
        return ret
    }

    private persist(): void {
        Util.writeJsonAtomically(this.file, Object.fromEntries(this.endpoints))
    }
}
