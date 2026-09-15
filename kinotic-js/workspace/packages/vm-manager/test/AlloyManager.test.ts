import { describe, expect, it } from 'bun:test'
import { spawn, spawnSync } from 'node:child_process'
import { chmodSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { Workload } from '@kinotic-ai/management-api'
import { AlloyManager, type AlloyManagerOptions } from '@/internal/api/telemetry/AlloyManager'
import type { TelemetryTarget } from '@/internal/api/model/TelemetryTarget'
import { LogFormat } from '@/internal/api/model/LogFormat'

// AlloyManager resolves `alloy` from the PATH, so a harmless stand-in on the PATH lets
// the full start path (binary resolution, orphan takeover, spawn, pid file) run for real.
// It ignores SIGHUP as Alloy does on a config reload, and never execs, so the config path
// the manager passed stays in its argv where liveAlloys can find it. The run is bounded so
// a leaked stand-in cannot hold the test runner's inherited stdio open indefinitely.
const fakeBinDir = mkdtempSync(join(tmpdir(), 'alloy-bin-'))
writeFileSync(join(fakeBinDir, 'alloy'),
              "#!/bin/sh\ntrap '' HUP\ni=0\nwhile [ $i -lt 150 ]; do sleep 0.2; i=$((i+1)); done\n")
chmodSync(join(fakeBinDir, 'alloy'), 0o755)
process.env.PATH = `${fakeBinDir}:${process.env.PATH}`

const TEMPO_URL = 'http://tempo:4318'
const MIMIR_URL = 'http://mimir:9009/otlp'

type Destinations = Partial<Pick<AlloyManagerOptions, 'lokiUrl' | 'tempoUrl' | 'mimirUrl'>>

function manager(dataDir: string, destinations: Destinations = {}): AlloyManager {
    return new AlloyManager({ lokiUrl: 'http://loki:3100', tempoUrl: null, mimirUrl: null, nodeId: 'node-1', dataDir, ...destinations })
}

function target(overrides: Partial<TelemetryTarget> = {}): TelemetryTarget {
    return {
        workloadId: '9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c',
        vmId: 'KeUwLBZv2RFz',
        logPath: '/var/kinotic/vm-logs/9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c/*.log',
        format: LogFormat.PLAIN,
        otlp: null,
        organizationId: 'acme',
        applicationId: null,
        ...overrides,
    }
}

/** A target whose workload elected telemetry and was issued an endpoint on this node. */
function withEndpoint(overrides: Partial<TelemetryTarget> = {}): TelemetryTarget {
    return target({
        otlp: { listenAddress: '127.0.0.1', port: 43180, token: 'a1b2c3', signals: ['traces', 'metrics'] },
        ...overrides,
    })
}

/** Applies the targets and returns the pipeline config the manager wrote. */
async function configFor(targets: TelemetryTarget[], destinations: Destinations = {}): Promise<string> {
    const dataDir = mkdtempSync(join(tmpdir(), 'alloy-config-'))
    const alloyManager = manager(dataDir, destinations)
    try {
        await alloyManager.applyTargets(targets)
        return readFileSync(join(dataDir, 'config.alloy'), 'utf-8')
    } finally {
        await alloyManager.stop()
    }
}

describe('applyTargets pipeline generation', () => {

    it('routes a workload with an organization to that org tenant', async () => {
        const config = await configFor([target()])

        expect(config).toContain('tenant         = "acme"')
        expect(config).toContain('workload_id    = "9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c"')
        expect(config).toContain('vm_id          = "KeUwLBZv2RFz"')
        expect(config).toContain('node_id        = "node-1"')
        expect(config).toContain('__path__       = "/var/kinotic/vm-logs/9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c/*.log"')
    })

    it('unwraps the json-file envelope for a Docker target before tenant routing', async () => {
        const config = await configFor([target({
            logPath: '/var/lib/docker/containers/abc123/abc123-json.log',
            format: LogFormat.DOCKER_JSON,
        })])

        expect(config).toContain('stage.docker {}')
        expect(config).toContain('forward_to = [loki.process.docker.receiver]')
        expect(config).toContain('__path__       = "/var/lib/docker/containers/abc123/abc123-json.log"')
        // The docker stage recovers the message and hands off to the one tenant-routing stage
        expect(config).toContain('tenant         = "acme"')
    })

    it('omits the docker stage when no target needs it', async () => {
        const config = await configFor([target()])

        expect(config).not.toContain('stage.docker')
        expect(config).toContain('forward_to = [loki.process.workloads.receiver]')
    })

    it('routes a platform workload (no organization) to the system tenant', async () => {
        const config = await configFor([target({ organizationId: null })])

        expect(config).toContain('tenant         = "kinotic-system"')
    })

    it('labels application_id only when the workload has one', async () => {
        const withApp = await configFor([target({ applicationId: 'app-7' })])
        const withoutApp = await configFor([target()])

        expect(withApp).toContain('application_id = "app-7"')
        expect(withoutApp).not.toContain('application_id')
    })

    it('rescans a target path every second, so a short-lived run is discovered while it writes', async () => {
        const config = await configFor([target()])

        expect(config).toContain('sync_period  = "1s"')
    })

    it('derives valid component names from UUID workload ids', async () => {
        const config = await configFor([target()])

        expect(config).toContain('local.file_match "wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c"')
        expect(config).toContain('targets    = local.file_match.wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c.targets')
    })

    it('routes the tenant via a transient label that is dropped before push', async () => {
        const config = await configFor([target()])

        expect(config).toContain('stage.tenant')
        expect(config).toContain('source = "tenant"')
        expect(config).toContain('values = ["tenant"]')
    })

    it('renders write and process stages even with no targets', async () => {
        const config = await configFor([])

        expect(config).toContain('loki.write "default"')
        expect(config).toContain('url = "http://loki:3100/loki/api/v1/push"')
        expect(config).not.toContain('loki.source.file')
    })
})

describe('OTLP pipeline generation', () => {

    it('receives a workload\'s traces on its own endpoint, behind its own bearer token', async () => {
        const config = await configFor([withEndpoint()], { tempoUrl: TEMPO_URL })

        expect(config).toContain('otelcol.auth.bearer "wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c"')
        expect(config).toContain('token = "a1b2c3"')
        expect(config).toContain('otelcol.receiver.otlp "wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c"')
        expect(config).toContain('endpoint = "127.0.0.1:43180"')
        expect(config).toContain('auth     = otelcol.auth.bearer.wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c.handler')
    })

    it('stamps spans with the identity its log streams carry, as resource attributes', async () => {
        const config = await configFor([withEndpoint({ applicationId: 'app-7' })], { tempoUrl: TEMPO_URL })

        expect(config).toContain('context    = "resource"')
        expect(config).toContain('"set(attributes[\\"workload_id\\"], \\"9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c\\")"')
        expect(config).toContain('"set(attributes[\\"vm_id\\"], \\"KeUwLBZv2RFz\\")"')
        expect(config).toContain('"set(attributes[\\"node_id\\"], \\"node-1\\")"')
        expect(config).toContain('"set(attributes[\\"application_id\\"], \\"app-7\\")"')
    })

    it('omits the application attribute when the workload has no application', async () => {
        const config = await configFor([withEndpoint()], { tempoUrl: TEMPO_URL })

        expect(config).not.toContain('application_id')
    })

    it('exports each organization to its own Tempo tenant, and platform workloads to the system tenant', async () => {
        const config = await configFor([
            withEndpoint(),
            withEndpoint({ workloadId: 'ff000000-0000-4000-8000-000000000001', organizationId: null,
                     otlp: { listenAddress: '127.0.0.1', port: 43181, token: 'd4e5f6', signals: ['traces', 'metrics'] } }),
        ], { tempoUrl: TEMPO_URL })

        expect(config).toContain('"X-Scope-OrgID" = "acme"')
        expect(config).toContain('"X-Scope-OrgID" = "kinotic-system"')
        expect(config).toContain('endpoint = "http://tempo:4318"')
    })

    it('shares one exporter between workloads of the same organization', async () => {
        const config = await configFor([
            withEndpoint(),
            withEndpoint({ workloadId: 'ff000000-0000-4000-8000-000000000001',
                     otlp: { listenAddress: '127.0.0.1', port: 43181, token: 'd4e5f6', signals: ['traces', 'metrics'] } }),
        ], { tempoUrl: TEMPO_URL })

        expect(config.match(/otelcol\.receiver\.otlp "/g)).toHaveLength(2)
        expect(config.match(/otelcol\.exporter\.otlphttp "/g)).toHaveLength(1)
    })

    it('ships nothing for a workload that did not elect telemetry', async () => {
        const config = await configFor([target()], { tempoUrl: TEMPO_URL })

        expect(config).not.toContain('otelcol.')
    })

    it('ships nothing on a node with neither a Tempo nor a Mimir URL, whatever a workload elected', async () => {
        const config = await configFor([withEndpoint()])

        expect(config).not.toContain('otelcol.')
    })

    it('ships traces alone on a node configured for Tempo but not Loki', async () => {
        const config = await configFor([withEndpoint()], { tempoUrl: TEMPO_URL, lokiUrl: null })

        expect(config).toContain('otelcol.receiver.otlp')
        expect(config).not.toContain('loki.')
    })

    it('ships metrics beside traces when the node has a Mimir URL', async () => {
        const config = await configFor([withEndpoint({ applicationId: 'app-7' })], { tempoUrl: TEMPO_URL, mimirUrl: MIMIR_URL })

        // Every stage carries both signals, and the tenant fans out to one exporter each
        expect(config).toContain('    traces = [otelcol.processor.transform.wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c.input]')
        expect(config).toContain('    metrics = [otelcol.processor.transform.wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c.input]')
        expect(config).toContain('  metric_statements {')
        expect(config).toContain('    metrics = [otelcol.exporter.otlphttp.metrics_tenant_61636d65.input]')
        expect(config).toContain('otelcol.exporter.otlphttp "metrics_tenant_61636d65"')
        expect(config).toContain('endpoint = "http://mimir:9009/otlp"')
        expect(config).toContain('otelcol.exporter.otlphttp "traces_tenant_61636d65"')
    })

    it('ships metrics alone on a node with a Mimir URL and no Tempo URL', async () => {
        const config = await configFor([withEndpoint()], { mimirUrl: MIMIR_URL })

        expect(config).toContain('    metrics = [otelcol.processor.transform.wl_9b2f4e6a_1c3d_4f5e_8a7b_0d1e2f3a4b5c.input]')
        expect(config).not.toContain('traces = [')
        expect(config).not.toContain('trace_statements')
        expect(config).not.toContain('traces_tenant')
    })
})

describe('endpoints and the guest environment', () => {

    function workload(): Workload {
        const w = new Workload('orders', 'oven/bun:latest')
        w.id = '9b2f4e6a-1c3d-4f5e-8a7b-0d1e2f3a4b5c'
        w.telemetry = true
        w.environment = { APP: 'x' }
        w.secrets = { TOKEN: 'y' }
        return w
    }

    it('issues an endpoint accepting the signals the node ships, and keeps it across restarts', async () => {
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-endpoints-'))

        const issued = manager(dataDir, { tempoUrl: TEMPO_URL }).issueEndpoint(workload(), '127.0.0.1')

        expect(issued).toMatchObject({ listenAddress: '127.0.0.1', signals: ['traces'] })
        // A guest booted by the previous process still holds this port and token
        expect(manager(dataDir, { tempoUrl: TEMPO_URL }).endpointOf(workload().id!)).toEqual(issued)
    })

    it('issues nothing to a workload that did not elect telemetry, or on a node shipping no signal', async () => {
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-endpoints-'))
        const silent = workload()
        silent.telemetry = false

        expect(manager(dataDir, { tempoUrl: TEMPO_URL }).issueEndpoint(silent, '127.0.0.1')).toBeNull()
        expect(manager(dataDir).issuesEndpoints()).toBeFalse()
        expect(manager(dataDir).issueEndpoint(workload(), '127.0.0.1')).toBeNull()
    })

    it('lays the exporter configuration over the workload environment, with the service name beneath it', () => {
        const w = workload()
        w.environment = { APP: 'x', OTEL_EXPORTER_OTLP_ENDPOINT: 'http://elsewhere:4318' }
        const endpoint = { listenAddress: '127.0.0.1', port: 43180, token: 'abc123', signals: ['traces' as const] }

        expect(AlloyManager.guestEnvironment(w, '192.168.127.254', endpoint)).toEqual({
            OTEL_SERVICE_NAME: 'orders',
            APP: 'x',
            TOKEN: 'y',
            OTEL_EXPORTER_OTLP_ENDPOINT: 'http://192.168.127.254:43180',
            OTEL_EXPORTER_OTLP_PROTOCOL: 'http/protobuf',
            OTEL_EXPORTER_OTLP_HEADERS: 'authorization=Bearer%20abc123',
            OTEL_TRACES_EXPORTER: 'otlp',
            // A signal the endpoint does not accept is turned off, since the receiver would refuse it
            OTEL_METRICS_EXPORTER: 'none',
            OTEL_LOGS_EXPORTER: 'none',
        })
    })

    it('lets the workload name its own service', () => {
        const w = workload()
        w.environment = { OTEL_SERVICE_NAME: 'checkout' }

        expect(AlloyManager.guestEnvironment(w, '127.0.0.1', { listenAddress: '127.0.0.1', port: 43180, token: 't', signals: [] }))
            .toMatchObject({ OTEL_SERVICE_NAME: 'checkout' })
    })

    it('leaves the environment of a workload without an endpoint as it is', () => {
        expect(AlloyManager.guestEnvironment(workload(), '127.0.0.1', null)).toEqual({ APP: 'x', TOKEN: 'y' })
    })
})

/**
 * The pinned Alloy release, when KINOTIC_ALLOY_BIN names one, checks the generated pipeline
 * the way the node's shipper will: every component, argument, and reference. CI has no
 * binary, so this is skipped there.
 */
describe.skipIf(!process.env.KINOTIC_ALLOY_BIN)('generated config against a real Alloy', () => {

    it('validates a pipeline shipping both log formats and the telemetry of two tenants', async () => {
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-validate-'))
        const alloyManager = manager(dataDir, { tempoUrl: TEMPO_URL, mimirUrl: MIMIR_URL })
        try {
            await alloyManager.applyTargets([
                withEndpoint({ applicationId: 'app-7' }),
                withEndpoint({
                    workloadId: 'ff000000-0000-4000-8000-000000000001',
                    organizationId: null,
                    logPath: '/var/lib/docker/containers/abc123/abc123-json.log',
                    format: LogFormat.DOCKER_JSON,
                    otlp: { listenAddress: '172.17.0.1', port: 43181, token: 'd4e5f6', signals: ['traces', 'metrics'] },
                }),
                target({ workloadId: 'ff000000-0000-4000-8000-000000000002' }),
            ])
        } finally {
            await alloyManager.stop()
        }

        const result = spawnSync(process.env.KINOTIC_ALLOY_BIN!, ['validate', join(dataDir, 'config.alloy')],
                                 { encoding: 'utf-8' })

        expect({ status: result.status, output: `${result.stdout}${result.stderr}` }).toEqual({ status: 0, output: '' })
    // Validation loads every component's schema, which outlasts the default test timeout on a busy machine
    }, 60_000)
})

function isAlive(pid: number): boolean {
    try {
        process.kill(pid, 0)
        return true
    } catch {
        return false
    }
}

describe('orphaned Alloy takeover on start', () => {

    it('terminates the process recorded in the pid file before starting', async () => {
        const orphan = spawn('sleep', ['60'])
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-takeover-'))
        writeFileSync(join(dataDir, 'alloy.pid'), String(orphan.pid))
        const alloyManager = manager(dataDir)

        await alloyManager.applyTargets([])

        expect(isAlive(orphan.pid!)).toBeFalse()
        await alloyManager.stop()
    })

    it('tolerates a recorded process that already exited', async () => {
        const exited = spawn('true')
        await new Promise(resolve => exited.once('exit', resolve))
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-takeover-'))
        writeFileSync(join(dataDir, 'alloy.pid'), String(exited.pid))
        const alloyManager = manager(dataDir)

        await alloyManager.applyTargets([])

        await alloyManager.stop()
    })
})

/**
 * Alloy processes still running for the given data dir, found by the config path the
 * manager passed on the command line. Only a process the manager lost track of can
 * outlive its stop, so a non-empty result is a leaked Alloy.
 */
async function liveAlloys(dataDir: string): Promise<string[]> {
    // A just-spawned process needs a moment to appear in the process table
    await Bun.sleep(250)
    const ps = spawnSync('ps', ['-eo', 'pid=,args='], { encoding: 'utf-8' })
    return ps.stdout.split('\n').filter(line => line.includes(dataDir))
}

describe('concurrent applyTargets', () => {

    it('spawns one Alloy and applies the last targets when calls overlap a start', async () => {
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-concurrent-'))
        const alloyManager = manager(dataDir)

        // Every vm-manager workload operation ends in applyTargets, so overlapping
        // startWorkload calls land here while the first start is still spawning
        await Promise.all([
            alloyManager.applyTargets([]),
            alloyManager.applyTargets([target()]),
            alloyManager.applyTargets([target({ workloadId: 'ff000000-0000-4000-8000-000000000001' })]),
        ])
        await alloyManager.stop()

        expect(await liveAlloys(dataDir)).toBeEmpty()
        expect(readFileSync(join(dataDir, 'config.alloy'), 'utf-8'))
            .toContain('workload_id    = "ff000000-0000-4000-8000-000000000001"')
    })

    it('ignores targets applied after stop so shutdown leaves no Alloy behind', async () => {
        const dataDir = mkdtempSync(join(tmpdir(), 'alloy-post-stop-'))
        const alloyManager = manager(dataDir)

        await alloyManager.stop()
        await alloyManager.applyTargets([target()])

        expect(await liveAlloys(dataDir)).toBeEmpty()
    })
})
