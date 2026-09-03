import { randomBytes } from 'node:crypto'
import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { TraceEndpoint } from '@/internal/api/model/TraceEndpoint'

/**
 * Host ports the trace receivers are issued from, one per workload that elects tracing. A
 * node runs a handful of micro VMs, so the range is sized for that rather than for density.
 */
const FIRST_PORT = 43180
const PORT_COUNT = 100

/**
 * The OTLP endpoints this node has issued to its workloads. Every workload that elects tracing
 * gets a host port the node's shipper listens on for it alone, and a bearer token only its
 * guest is given — so the receiver a push arrives on says which workload sent it, and a guest
 * cannot push into another workload's stream. An endpoint holds for the workload's life: the
 * guest's environment is set once, when its VM is created, and a restart boots the same VM,
 * which is why the record outlives the vm-manager process.
 */
export class TraceEndpointRepository {

    /**
     * The OTLP exporter configuration a guest is given to reach its endpoint, in the standard
     * OpenTelemetry variables every SDK reads, so a workload's runtime needs no code of its own
     * to export there. Only traces are exported: the endpoint carries nothing else.
     *
     * @param host the address the guest reaches its node on, which each provider knows
     */
    static guestEnvironment(host: string, endpoint: TraceEndpoint): Record<string, string> {
        return {
            OTEL_EXPORTER_OTLP_ENDPOINT: `http://${host}:${endpoint.port}`,
            OTEL_EXPORTER_OTLP_PROTOCOL: 'http/protobuf',
            // Header values are percent-encoded, per the OTLP exporter specification
            OTEL_EXPORTER_OTLP_HEADERS: `authorization=Bearer%20${endpoint.token}`,
            OTEL_TRACES_EXPORTER: 'otlp',
            OTEL_METRICS_EXPORTER: 'none',
            OTEL_LOGS_EXPORTER: 'none',
        }
    }

    private readonly file: string
    private readonly endpoints: Map<string, TraceEndpoint>

    constructor(dataDir: string) {
        mkdirSync(dataDir, { recursive: true })
        this.file = join(dataDir, 'trace-endpoints.json')
        this.endpoints = this.load()
    }

    /** The endpoint issued to the workload, or null when it holds none. */
    find(workloadId: string): TraceEndpoint | null {
        return this.endpoints.get(workloadId) ?? null
    }

    /**
     * Issues the workload an endpoint bound to the given host address, or returns the one it
     * already holds. Fails when every port in the range is taken.
     */
    issue(workloadId: string, listenAddress: string): TraceEndpoint {
        let ret = this.endpoints.get(workloadId)
        if (ret === undefined) {
            ret = { listenAddress, port: this.freePort(), token: randomBytes(32).toString('hex') }
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

    private freePort(): number {
        const taken = new Set(Array.from(this.endpoints.values(), endpoint => endpoint.port))
        const port = Array.from({ length: PORT_COUNT }, (_, offset) => FIRST_PORT + offset)
                          .find(candidate => !taken.has(candidate))
        if (port === undefined) {
            throw new Error(`Every trace receiver port on this node (${FIRST_PORT}-${FIRST_PORT + PORT_COUNT - 1}) is in use`)
        }
        return port
    }

    // A missing record is a node that has issued nothing yet; an unreadable one is not, since
    // treating it as empty would reissue the ports and tokens of guests that still hold the old
    private load(): Map<string, TraceEndpoint> {
        let ret = new Map<string, TraceEndpoint>()
        try {
            ret = new Map(Object.entries(JSON.parse(readFileSync(this.file, 'utf-8'))))
        } catch (error) {
            if ((error as NodeJS.ErrnoException).code !== 'ENOENT') {
                throw new Error(`Cannot read the trace endpoints issued by this node from ${this.file}`, { cause: error })
            }
        }
        return ret
    }

    // Written atomically (write + rename) so a crash mid-write cannot corrupt the record
    private persist(): void {
        writeFileSync(`${this.file}.tmp`, JSON.stringify(Object.fromEntries(this.endpoints)))
        renameSync(`${this.file}.tmp`, this.file)
    }
}
