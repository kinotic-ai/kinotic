import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { spawn, type ChildProcess } from 'node:child_process'
import { SYSTEM_LOG_TENANT } from '@kinotic-ai/os-api'
import { resolveAlloyBinary } from '@/internal/api/logging/AlloyBinary'
import type { LogTarget } from '@/model/LogTarget'

// Alloy's default port, bound to loopback so the debug UI is not exposed off-host
const LISTEN_ADDR = '127.0.0.1:12345'

/**
 * Terminates the Alloy process recorded in the pid file, if one is still running. A crashed
 * vm-manager leaves its Alloy child orphaned and holding the listen port; exactly one
 * vm-manager runs per node, so any recorded process is ours to take over.
 */
export async function terminateStaleAlloy(pidFile: string): Promise<void> {
    let pid: number
    try {
        pid = Number.parseInt(readFileSync(pidFile, 'utf-8').trim(), 10)
    } catch {
        return
    }
    rmSync(pidFile, { force: true })
    if (!Number.isInteger(pid) || pid <= 0 || !isAlive(pid)) {
        return
    }

    console.log(`Terminating orphaned Alloy (pid ${pid}) left by a previous vm-manager`)
    process.kill(pid, 'SIGTERM')
    for (let i = 0; i < 50 && isAlive(pid); i++) {
        await new Promise(resolve => setTimeout(resolve, 200))
    }
    if (isAlive(pid)) {
        process.kill(pid, 'SIGKILL')
    }
}

function isAlive(pid: number): boolean {
    try {
        process.kill(pid, 0)
        return true
    } catch {
        return false
    }
}

export interface AlloyManagerOptions {
    /** Base URL of the Loki HTTP API logs are pushed to. */
    lokiUrl: string
    /** This vm-manager node's id, applied as the node_id label on every stream. */
    nodeId: string
    /** Directory holding the generated config, Alloy's WAL, and downloaded binaries. */
    dataDir: string
    /** Use exactly this Alloy binary instead of PATH lookup / download. */
    binaryPath?: string
    /** Alloy release to download when no binary is found. */
    version: string
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

    /**
     * Stops the Alloy process, escalating to SIGKILL if it ignores SIGTERM.
     */
    async stop(): Promise<void> {
        this.stopping = true
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
        const binary = await resolveAlloyBinary({
            explicitPath: this.options.binaryPath,
            version: this.options.version,
            installDir: join(this.options.dataDir, 'bin'),
        })

        // An orphan from a crashed vm-manager still holds the listen port; take it over
        await terminateStaleAlloy(this.pidFile)

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

    // Renders the pipeline: one file source per running VM, a shared process stage that
    // routes each stream to its organization's Loki tenant, and a single write endpoint
    private generateConfig(targets: LogTarget[]): string {
        const sections: string[] = [
            '// Generated by vm-manager — do not edit. Regenerated as workloads come and go.',
            '',
            ...targets.map(target => this.renderTarget(target)),
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
    url = ${river(this.options.lokiUrl + '/loki/api/v1/push')}
  }
}
`,
        ]
        return sections.join('\n')
    }

    private renderTarget(target: LogTarget): string {
        const name = componentName(target.workloadId)
        const labels = [
            `      __path__       = ${river(join(target.logDir, '*.log'))},`,
            `      workload_id    = ${river(target.workloadId)},`,
            `      vm_id          = ${river(target.vmId)},`,
            `      node_id        = ${river(this.options.nodeId)},`,
            ...(target.applicationId ? [`      application_id = ${river(target.applicationId)},`] : []),
            `      tenant         = ${river(target.organizationId ?? SYSTEM_LOG_TENANT)},`,
        ]
        return `local.file_match ${river(name)} {
  path_targets = [
    {
${labels.join('\n')}
    },
  ]
}

loki.source.file ${river(name)} {
  targets    = local.file_match.${name}.targets
  forward_to = [loki.process.workloads.receiver]
}
`
    }
}

// Alloy component labels must match [A-Za-z_][A-Za-z0-9_]*; workload ids are UUIDs
function componentName(workloadId: string): string {
    return `wl_${workloadId.replace(/[^A-Za-z0-9_]/g, '_')}`
}

// River string literals use JSON-compatible escaping
function river(value: string): string {
    return JSON.stringify(value)
}
