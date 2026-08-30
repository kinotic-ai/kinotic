import { chmodSync, existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { spawn, spawnSync, type ChildProcess } from 'node:child_process'
import type { LogTarget } from '@/model/LogTarget'
import { LogFormat } from '@/model/LogFormat'

// Alloy's default port, bound to loopback so the debug UI is not exposed off-host
const LISTEN_ADDR = '127.0.0.1:12345'

// Release installed when `alloy` is not on the PATH. Bump deliberately.
const ALLOY_VERSION = 'v1.17.1'

// Loki tenant for platform workloads with no organization (SYSTEM scope); must match
// DefaultLogService.SYSTEM_LOG_TENANT on the server
const SYSTEM_LOG_TENANT = 'kinotic-system'

export interface AlloyManagerOptions {
    /** Base URL of the Loki HTTP API logs are pushed to. */
    lokiUrl: string
    /** This vm-manager node's id, applied as the node_id label on every stream. */
    nodeId: string
    /** Directory holding the generated config, Alloy's WAL, and downloaded binaries. */
    dataDir: string
}

/**
 * Runs the Grafana Alloy process that ships workload logs to Loki. The pipeline config is
 * regenerated from the current log targets on every change; Alloy is started on the first
 * target and hot-reloads via SIGHUP afterwards.
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

    /**
     * Regenerates the pipeline for the given targets, starting Alloy or reloading its
     * config as needed. No-op when Alloy is running and the config is unchanged.
     */
    async applyTargets(targets: LogTarget[]): Promise<void> {
        // start() is async and leaves this.child null while the binary downloads and
        // spawns, so concurrent callers would each spawn an Alloy — the loser of the
        // race then holds LISTEN_ADDR as an orphan the pid file no longer names.
        // Queueing every mutation of the process behind the previous one is what keeps
        // the child/config state coherent.
        return this.enqueue(() => this.applyTargetsInternal(targets))
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

    private async applyTargetsInternal(targets: LogTarget[]): Promise<void> {
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
        console.log(`Alloy started (pid ${this.child.pid}), shipping logs to ${this.options.lokiUrl}`)

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
        const response = await fetch(url)
        if (!response.ok) {
            throw new Error(`Alloy download failed: HTTP ${response.status} for ${url}`)
        }
        // Buffered rather than streamed: Bun.write(path, response) never completes for this
        // body, leaving the node with an empty version directory and no log shipping at all.
        // The asset is ~100MB and read once per version, so holding it in memory is cheap.
        await Bun.write(zipPath, await response.arrayBuffer())

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

    // Renders the pipeline: one file source per running VM, a shared process stage that
    // routes each stream to its organization's Loki tenant, and a single write endpoint
    private generateConfig(targets: LogTarget[]): string {
        const sections: string[] = [
            '// Generated by vm-manager — do not edit. Regenerated as workloads come and go.',
            '',
            ...targets.map(target => this.renderTarget(target)),
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
    url = ${this.river(this.options.lokiUrl + '/loki/api/v1/push')}
  }
}
`,
        ]
        return sections.join('\n')
    }

    private renderTarget(target: LogTarget): string {
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
            `      tenant         = ${this.river(target.organizationId ?? SYSTEM_LOG_TENANT)},`,
        ]
        return `local.file_match ${this.river(name)} {
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

    // Alloy component labels must match [A-Za-z_][A-Za-z0-9_]*; workload ids are UUIDs
    private componentName(workloadId: string): string {
        return `wl_${workloadId.replace(/[^A-Za-z0-9_]/g, '_')}`
    }

    // River string literals use JSON-compatible escaping
    private river(value: string): string {
        return JSON.stringify(value)
    }
}
