import { chmodSync, existsSync, mkdirSync, readdirSync, readFileSync, renameSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { spawn, spawnSync, type ChildProcess } from 'node:child_process'
import type { TelemetryTarget } from '@/internal/api/model/TelemetryTarget'
import { LogFormat } from '@/internal/api/model/LogFormat'

// Alloy's default port, bound to loopback so the debug UI is not exposed off-host
const LISTEN_ADDR = '127.0.0.1:12345'

// Release installed when `alloy` is not on the PATH. Bump deliberately.
const ALLOY_VERSION = 'v1.17.1'

/**
 * Ceiling on one attempt at the release archive. Generous on purpose: it is there to catch a
 * download that has stopped moving, not to hold the node to a transfer rate. The asset is
 * about 100MB, so anything above roughly 7Mbit/s finishes well inside it.
 */
const DOWNLOAD_TIMEOUT_MS = 120_000

/** Attempts before the node gives up and runs without telemetry shipping. */
const DOWNLOAD_ATTEMPTS = 3

// Tenant for platform workloads with no organization (SYSTEM scope), for logs and traces
// alike; must match DefaultLogService.SYSTEM_LOG_TENANT on the server
const SYSTEM_TENANT = 'kinotic-system'

/**
 * How often Alloy rescans a target's path for files. A one-shot workload can be over in
 * seconds, so the default of ten leaves its file undiscovered for most of its life.
 */
const FILE_SYNC_PERIOD = '1s'

/** Ceiling on waiting for the shipper to read a workload's files before they are deleted. */
const SHIP_TIMEOUT_MS = 15_000
const SHIP_POLL_MS = 250

export interface AlloyManagerOptions {
    /** Base URL of the Loki HTTP API logs are pushed to; null ships no logs. */
    lokiUrl: string | null
    /** Base URL of the OTLP/HTTP endpoint traces are pushed to; null ships no traces. */
    tempoUrl: string | null
    /** This vm-manager node's id, applied as the node_id label on every stream. */
    nodeId: string
    /** Directory holding the generated config, Alloy's WAL, and downloaded binaries. */
    dataDir: string
}

/**
 * Runs the Grafana Alloy process that ships workload logs to Loki and workload traces to
 * Tempo. The pipeline config is regenerated from the current targets on every change; Alloy
 * is started on the first target and hot-reloads via SIGHUP afterwards.
 */
export class AlloyManager {

    private readonly options: AlloyManagerOptions
    private readonly configPath: string
    private readonly pidFile: string
    private child: ChildProcess | null = null
    private lastConfig: string | null = null
    private stopping = false
    // Serializes applyTargets and stop against each other; see the note in applyTargets
    private pending: Promise<void> = Promise.resolve()

    constructor(options: AlloyManagerOptions) {
        this.options = options
        this.configPath = join(options.dataDir, 'config.alloy')
        this.pidFile = join(options.dataDir, 'alloy.pid')
    }

    /** Whether this node ships the traces of workloads that elect tracing. */
    shipsTraces(): boolean {
        return this.options.tempoUrl !== null
    }

    /**
     * Regenerates the pipeline for the given targets, starting Alloy or reloading its
     * config as needed. No-op when Alloy is running and the config is unchanged.
     */
    async applyTargets(targets: TelemetryTarget[]): Promise<void> {
        // start() is async and leaves this.child null while the binary downloads and
        // spawns, so concurrent callers would each spawn an Alloy — the loser of the
        // race then holds LISTEN_ADDR as an orphan the pid file no longer names.
        // Queueing every mutation of the process behind the previous one is what keeps
        // the child/config state coherent.
        return this.enqueue(() => this.applyTargetsInternal(targets))
    }

    /**
     * Waits until Alloy has read every file of the target to its end, so the files can be
     * deleted without losing what the workload wrote last. Gives up after a bounded wait,
     * with a warning, rather than holding the caller to a shipper that has stalled.
     */
    async awaitShipped(target: TelemetryTarget): Promise<void> {
        if (!this.child) {
            return
        }
        const deadline = Date.now() + SHIP_TIMEOUT_MS
        let unread = this.unreadFiles(target, await this.readBytesByPath())
        while (unread.length > 0 && Date.now() < deadline) {
            await new Promise(resolve => setTimeout(resolve, SHIP_POLL_MS))
            unread = this.unreadFiles(target, await this.readBytesByPath())
        }
        if (unread.length > 0) {
            console.warn(`Deleting the logs of workload ${target.workloadId} before the log shipper read `
                         + `${unread.join(', ')} to the end; the tail of its logs is lost`)
        }
    }

    // The target's files on disk that Alloy's read counter has not caught up with
    private unreadFiles(target: TelemetryTarget, readBytesByPath: Map<string, number>): string[] {
        return this.filesOf(target).filter(file => (readBytesByPath.get(file) ?? 0) < statSync(file).size)
    }

    // A PLAIN target names a directory glob of *.log files; a DOCKER_JSON target one file
    private filesOf(target: TelemetryTarget): string[] {
        let ret: string[]
        if (target.format === LogFormat.PLAIN) {
            const dir = dirname(target.logPath)
            ret = existsSync(dir)
                ? readdirSync(dir).filter(file => file.endsWith('.log')).map(file => join(dir, file))
                : []
        } else {
            ret = existsSync(target.logPath) ? [target.logPath] : []
        }
        return ret
    }

    /**
     * Bytes Alloy has read from each file, by path, from its
     * {@code loki_source_file_read_bytes_total} metric. A shipper that cannot be reached has
     * read nothing as far as the caller is concerned.
     */
    private async readBytesByPath(): Promise<Map<string, number>> {
        const ret = new Map<string, number>()
        try {
            const response = await fetch(`http://${LISTEN_ADDR}/metrics`)
            for (const line of (await response.text()).split('\n')) {
                const match = /^loki_source_file_read_bytes_total\{.*?path="((?:[^"\\]|\\.)*)".*?\} (\d+)/.exec(line)
                if (match) {
                    ret.set(JSON.parse(`"${match[1]}"`), Number(match[2]))
                }
            }
        } catch (error) {
            console.warn('Could not read the log shipper metrics:', error)
        }
        return ret
    }

    /**
     * Why this node is not shipping workload telemetry, or null when it is.
     *
     * Reported rather than thrown: a node that loses shipping keeps running, so the failure is
     * visible and fixable, but it is not fit to take workloads whose output would go nowhere.
     * Silence would leave it accepting them and quietly dropping their output.
     */
    shippingProblem(): string | null {
        let ret: string | null = null
        // Shutdown is deliberate, and a node on its way down has nothing to report
        if (!this.stopping && this.child === null) {
            ret = `workload ${this.destinations()} are not being shipped: the shipper is not running`
        }
        return ret
    }

    /**
     * Stops the Alloy process, escalating to SIGKILL if it ignores SIGTERM.
     */
    async stop(): Promise<void> {
        // Set outside the queue so a start already waiting to run sees it and skips
        this.stopping = true
        return this.enqueue(() => this.stopInternal())
    }

    // A rejected operation must not poison the queue for the ones behind it, so the
    // chain swallows failures while the caller still receives its own
    private enqueue(operation: () => Promise<void>): Promise<void> {
        const result = this.pending.then(operation, operation)
        this.pending = result.catch(() => {})
        return result
    }

    private async applyTargetsInternal(targets: TelemetryTarget[]): Promise<void> {
        // A workload operation racing shutdown would otherwise respawn Alloy after stop
        if (this.stopping) {
            return
        }

        const config = this.generateConfig(targets)
        if (config === this.lastConfig && this.child) {
            return
        }

        mkdirSync(this.options.dataDir, { recursive: true })
        writeFileSync(this.configPath, config)
        this.lastConfig = config

        if (this.child) {
            this.child.kill('SIGHUP')
        } else {
            await this.start()
        }
    }

    private async stopInternal(): Promise<void> {
        const child = this.child
        if (!child) {
            return
        }
        this.child = null

        await new Promise<void>(resolve => {
            const killTimer = setTimeout(() => child.kill('SIGKILL'), 10_000)
            child.once('exit', () => {
                clearTimeout(killTimer)
                resolve()
            })
            child.kill('SIGTERM')
        })
        rmSync(this.pidFile, { force: true })
    }

    private async start(): Promise<void> {
        const binary = await this.resolveBinary()

        // An orphan from a crashed vm-manager still holds the listen port; take it over
        await this.terminateStale()

        this.child = spawn(binary, [
            'run', this.configPath,
            `--server.http.listen-addr=${LISTEN_ADDR}`,
            `--storage.path=${join(this.options.dataDir, 'data')}`,
        ], {
            stdio: ['ignore', 'inherit', 'inherit'],
        })
        writeFileSync(this.pidFile, String(this.child.pid))
        console.log(`Alloy started (pid ${this.child.pid}), shipping ${this.destinations()}`)

        this.child.on('exit', (code, signal) => {
            rmSync(this.pidFile, { force: true })
            if (this.stopping) {
                return
            }
            console.error(`Alloy exited unexpectedly (code=${code}, signal=${signal}); ` +
                          'it will be restarted on the next workload change')
            // Clearing lastConfig makes the next applyTargets rewrite the config and respawn
            this.child = null
            this.lastConfig = null
        })
    }

    // What this node was configured to ship, and where
    private destinations(): string {
        return [
            ...(this.options.lokiUrl !== null ? [`logs to ${this.options.lokiUrl}`] : []),
            ...(this.options.tempoUrl !== null ? [`traces to ${this.options.tempoUrl}`] : []),
        ].join(' and ')
    }

    /**
     * Resolves the Alloy binary to run: `alloy` on the PATH, else a per-version install
     * under the data dir, downloading the pinned release from GitHub on first use.
     */
    private async resolveBinary(): Promise<string> {
        // Resolve against the live PATH — the same environment the spawned child inherits;
        // Bun.which otherwise snapshots the startup environ
        const onPath = Bun.which('alloy', { PATH: process.env.PATH ?? '' })
        if (onPath) {
            return onPath
        }

        const versionDir = join(this.options.dataDir, 'bin', ALLOY_VERSION)
        const installed = join(versionDir, 'alloy')
        if (existsSync(installed)) {
            return installed
        }
        return this.downloadBinary(versionDir)
    }

    private async downloadBinary(versionDir: string): Promise<string> {
        const os = process.platform === 'linux' ? 'linux' : process.platform === 'darwin' ? 'darwin' : null
        const cpu = process.arch === 'x64' ? 'amd64' : process.arch === 'arm64' ? 'arm64' : null
        if (!os || !cpu) {
            throw new Error(`No Alloy release asset for platform ${process.platform}/${process.arch}`)
        }
        const asset = `alloy-${os}-${cpu}`
        const url = `https://github.com/grafana/alloy/releases/download/${ALLOY_VERSION}/${asset}.zip`
        console.log(`Downloading Alloy ${ALLOY_VERSION} from ${url}`)

        mkdirSync(versionDir, { recursive: true })
        const zipPath = join(versionDir, `${asset}.zip`)
        // Buffered rather than streamed: Bun.write(path, response) never completes for this
        // body, leaving the node with an empty version directory and no telemetry shipping at
        // all. The asset is ~100MB and read once per version, so holding it in memory is cheap.
        await Bun.write(zipPath, await this.fetchArchive(url))

        const unzip = spawnSync('unzip', ['-o', zipPath, '-d', versionDir], { stdio: 'ignore' })
        if (unzip.error || unzip.status !== 0) {
            throw new Error(`Failed to unzip ${zipPath} — is 'unzip' installed?`)
        }
        rmSync(zipPath, { force: true })

        // The zip contains a single binary named after the asset
        const binaryPath = join(versionDir, 'alloy')
        renameSync(join(versionDir, asset), binaryPath)
        chmodSync(binaryPath, 0o755)
        console.log(`Alloy installed at ${binaryPath}`)
        return binaryPath
    }

    /**
     * Reads the release archive, giving up on an attempt that stops making progress.
     *
     * This runs before the node registers, so a download that never finishes takes the node
     * with it — no workloads, no heartbeat, and nothing said about why. The bound is what
     * turns that into a failure the caller can log and carry on without telemetry shipping.
     */
    private async fetchArchive(url: string): Promise<ArrayBuffer> {
        let lastFailure = ''
        for (let attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                const response = await fetch(url, { signal: AbortSignal.timeout(DOWNLOAD_TIMEOUT_MS) })
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`)
                }
                return await response.arrayBuffer()
            } catch (error) {
                lastFailure = (error as Error).message
                console.warn(`Alloy download attempt ${attempt} of ${DOWNLOAD_ATTEMPTS} failed: ${lastFailure}`)
            }
        }
        throw new Error(`Alloy download failed after ${DOWNLOAD_ATTEMPTS} attempts (${lastFailure}) for ${url}`)
    }

    /**
     * Terminates the Alloy process recorded in the pid file, if one is still running. A
     * crashed vm-manager leaves its Alloy child orphaned; exactly one vm-manager runs per
     * node, so any recorded process is ours to take over.
     */
    private async terminateStale(): Promise<void> {
        let pid: number
        try {
            pid = Number.parseInt(readFileSync(this.pidFile, 'utf-8').trim(), 10)
        } catch {
            return
        }
        rmSync(this.pidFile, { force: true })
        if (!Number.isInteger(pid) || pid <= 0 || !this.isAlive(pid)) {
            return
        }

        console.log(`Terminating orphaned Alloy (pid ${pid}) left by a previous vm-manager`)
        process.kill(pid, 'SIGTERM')
        for (let i = 0; i < 50 && this.isAlive(pid); i++) {
            await new Promise(resolve => setTimeout(resolve, 200))
        }
        if (this.isAlive(pid)) {
            process.kill(pid, 'SIGKILL')
        }
    }

    private isAlive(pid: number): boolean {
        try {
            process.kill(pid, 0)
            return true
        } catch {
            return false
        }
    }

    // Renders the pipelines: one file source per VM feeding a shared stage that routes each
    // stream to its organization's Loki tenant, and one OTLP receiver per VM that elects
    // tracing feeding its organization's Tempo exporter
    private generateConfig(targets: TelemetryTarget[]): string {
        const sections: string[] = [
            '// Generated by vm-manager — do not edit. Regenerated as workloads come and go.',
            '',
        ]
        if (this.options.lokiUrl !== null) {
            sections.push(...this.renderLogPipeline(targets, this.options.lokiUrl))
        }
        if (this.options.tempoUrl !== null) {
            sections.push(...this.renderTracePipeline(targets.filter(target => target.traces !== null),
                                                     this.options.tempoUrl))
        }
        return sections.join('\n')
    }

    private renderLogPipeline(targets: TelemetryTarget[], lokiUrl: string): string[] {
        return [
            ...targets.map(target => this.renderLogSource(target)),
            ...(targets.some(target => target.format === LogFormat.DOCKER_JSON)
                ? [`loki.process "docker" {
  // Docker's json-file driver wraps every line as {"log":..,"stream":..,"time":..}; without
  // this the envelope is shipped as the log line and the workload's own message is buried
  stage.docker {}
  forward_to = [loki.process.workloads.receiver]
}
`]
                : []),
            `loki.process "workloads" {
  // The transient tenant label becomes X-Scope-OrgID on push and is never stored
  stage.tenant {
    source = "tenant"
  }
  stage.label_drop {
    values = ["tenant"]
  }
  forward_to = [loki.write.default.receiver]
}

loki.write "default" {
  endpoint {
    url = ${this.river(lokiUrl + '/loki/api/v1/push')}
  }
}
`,
        ]
    }

    private renderLogSource(target: TelemetryTarget): string {
        const name = this.componentName(target.workloadId)
        const receiver = target.format === LogFormat.DOCKER_JSON
            ? 'loki.process.docker.receiver'
            : 'loki.process.workloads.receiver'
        const labels = [
            `      __path__       = ${this.river(target.logPath)},`,
            `      workload_id    = ${this.river(target.workloadId)},`,
            `      vm_id          = ${this.river(target.vmId)},`,
            `      node_id        = ${this.river(this.options.nodeId)},`,
            ...(target.applicationId ? [`      application_id = ${this.river(target.applicationId)},`] : []),
            `      tenant         = ${this.river(this.tenantOf(target))},`,
        ]
        return `local.file_match ${this.river(name)} {
  sync_period  = ${this.river(FILE_SYNC_PERIOD)}
  path_targets = [
    {
${labels.join('\n')}
    },
  ]
}

loki.source.file ${this.river(name)} {
  targets    = local.file_match.${name}.targets
  forward_to = [${receiver}]
}
`
    }

    // The tenant is a property of the push rather than of the data, so unlike the log
    // pipeline the trace pipeline ends in one exporter per organization
    private renderTracePipeline(targets: TelemetryTarget[], tempoUrl: string): string[] {
        const tenants = [...new Set(targets.map(target => this.tenantOf(target)))]
        return [
            ...targets.map(target => this.renderTraceSource(target)),
            ...tenants.map(tenant => this.renderTraceSink(tenant, tempoUrl)),
        ]
    }

    // A receiver of the workload's own, so the component a span arrives on says which
    // workload sent it, behind a token only that workload's guest holds
    private renderTraceSource(target: TelemetryTarget): string {
        const name = this.componentName(target.workloadId)
        const traces = target.traces!
        const sink = this.tenantComponentName(this.tenantOf(target))
        const attributes: Array<[string, string]> = [
            ['workload_id', target.workloadId],
            ['vm_id', target.vmId],
            ['node_id', this.options.nodeId],
            ...(target.applicationId ? [['application_id', target.applicationId] as [string, string]] : []),
        ]
        const statements = attributes.map(([key, value]) =>
            `      ${this.river(`set(attributes[${JSON.stringify(key)}], ${JSON.stringify(value)})`)},`)
        return `otelcol.auth.bearer ${this.river(name)} {
  token = ${this.river(traces.token)}
}

otelcol.receiver.otlp ${this.river(name)} {
  http {
    endpoint = ${this.river(`${traces.listenAddress}:${traces.port}`)}
    auth     = otelcol.auth.bearer.${name}.handler
  }
  output {
    traces = [otelcol.processor.transform.${name}.input]
  }
}

otelcol.processor.transform ${this.river(name)} {
  error_mode = "ignore"
  // The identity labels the workload's log streams carry, as resource attributes
  trace_statements {
    context    = "resource"
    statements = [
${statements.join('\n')}
    ]
  }
  output {
    traces = [otelcol.processor.batch.${sink}.input]
  }
}
`
    }

    private renderTraceSink(tenant: string, tempoUrl: string): string {
        const name = this.tenantComponentName(tenant)
        return `otelcol.processor.batch ${this.river(name)} {
  output {
    traces = [otelcol.exporter.otlphttp.${name}.input]
  }
}

otelcol.exporter.otlphttp ${this.river(name)} {
  client {
    endpoint = ${this.river(tempoUrl)}
    headers  = {
      "X-Scope-OrgID" = ${this.river(tenant)},
    }
  }
}
`
    }

    private tenantOf(target: TelemetryTarget): string {
        return target.organizationId ?? SYSTEM_TENANT
    }

    // Alloy component labels must match [A-Za-z_][A-Za-z0-9_]*; workload ids are UUIDs
    private componentName(workloadId: string): string {
        return `wl_${workloadId.replace(/[^A-Za-z0-9_]/g, '_')}`
    }

    // Hex rather than the sanitized id: two organizations whose ids differ only in a
    // character the label syntax forbids must not share an exporter
    private tenantComponentName(tenant: string): string {
        return `tenant_${Buffer.from(tenant).toString('hex')}`
    }

    // River string literals use JSON-compatible escaping
    private river(value: string): string {
        return JSON.stringify(value)
    }
}
